package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.csv.CsvIngest
import com.yswy.assetdashboard.csv.ParseResult
import com.yswy.assetdashboard.csv.SkippedRow
import com.yswy.assetdashboard.data.IngestedFile
import com.yswy.assetdashboard.data.IngestedFileDao

/**
 * inboxを一周する。読んで、記録して、processedへ移す。
 *
 * ## 記録してから移動する(この順序は重要)
 * 逆にすると、移動に成功した直後に記録が失敗したとき
 * 「processedにあるが記録は無い」状態になる。次回のスキャンでは
 * inboxに無いので**二度と見つからず、黙って消える**。
 *
 * 記録を先にすれば、移動が失敗しても
 * 「取り込み済みなのにinboxに残っている」状態として見え([InboxScanner]の
 * `alreadyIngested`)、次回この関数がリトライする。壊れ方が見える側に倒す。
 */
object InboxSync {

    private const val TAG = "InboxSync"

    suspend fun run(api: DriveApi, folders: AppFolders, dao: IngestedFileDao): Report {
        val scan = InboxScanner.scan(api, folders, dao)
        val entries = mutableListOf<Entry>()
        val skippedByFile = mutableMapOf<String, List<SkippedRow>>()

        // 前回移動に失敗して残っているものを先に片付ける。
        // 取り込み自体はもう済んでいるので、読み直さず移動だけ試す。
        for (file in scan.alreadyIngested) {
            entries += if (tryMove(api, folders, file)) {
                Entry(file.name, Status.MOVE_RECOVERED, "前回の移動失敗から回復")
            } else {
                Entry(file.name, Status.MOVE_FAILED, "取り込み済みだが移動できない")
            }
        }

        for (file in scan.pending) {
            val (entry, skipped) = ingestOne(api, folders, dao, file)
            entries += entry
            if (skipped.isNotEmpty()) skippedByFile[file.name] = skipped
        }

        val report = Report(entries)
        Log.i(TAG, "同期完了: ${report.summary()}")

        // 問題があればDriveのlogsに残す。書けなくても同期は成立している。
        SyncLog.writeIfNeeded(api, folders, report, skippedByFile)

        return report
    }

    /** @return 結果と、読めなかった行(ログ用) */
    private suspend fun ingestOne(
        api: DriveApi,
        folders: AppFolders,
        dao: IngestedFileDao,
        file: DriveApi.DriveFile,
    ): Pair<Entry, List<SkippedRow>> {
        val outcome = CsvIngest.read(api, file)

        val result: ParseResult = when (outcome) {
            is CsvIngest.Outcome.Parsed -> outcome.result

            // 読めなかったものはinboxに残す。直して置き直せば次回拾われる。
            is CsvIngest.Outcome.UnknownFormat ->
                return Entry(file.name, Status.FAILED, "フォーマットを判別できない") to emptyList()

            is CsvIngest.Outcome.Failed ->
                return Entry(file.name, Status.FAILED, outcome.reason) to emptyList()
        }

        // 記録が先。ここで落ちてもファイルはinboxに残るので、次回やり直せる。
        try {
            dao.upsert(
                IngestedFile(
                    driveFileId = file.id,
                    name = file.name,
                    modifiedTime = file.modifiedTime,
                    md5Checksum = file.md5Checksum,
                    ingestedAt = System.currentTimeMillis(),
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "記録に失敗: ${file.name}", e)
            return Entry(file.name, Status.FAILED, "取り込み記録を保存できない: ${e.message}") to
                result.skipped
        }

        // 正規化した結果をbackupに残す。これがあればDBを失っても作り直せる。
        val backedUp = BackupWriter.write(
            api = api,
            folders = folders,
            sourceFileName = file.name,
            sourceFileId = file.id,
            adapterId = result.adapterId,
            data = result.data,
        )

        val detail = buildString {
            append(result.adapterId)
            if (result.skipped.isNotEmpty()) append(" / 読めない行 ${result.skipped.size}")
            if (!backedUp) append(" / backupに書けず")
        }

        val entry = if (tryMove(api, folders, file)) {
            Entry(file.name, Status.INGESTED, detail)
        } else {
            // 取り込みは済んでいる。次回この関数が移動だけリトライする。
            Entry(file.name, Status.MOVE_FAILED, "$detail / processedへ移動できない")
        }
        return entry to result.skipped
    }

    private suspend fun tryMove(
        api: DriveApi,
        folders: AppFolders,
        file: DriveApi.DriveFile,
    ): Boolean = try {
        api.moveFile(file.id, fromParentId = folders.inbox, toParentId = folders.processed)
        true
    } catch (e: Exception) {
        // 移動できなくても取り込みは成立している。落とさずに次のファイルへ。
        Log.w(TAG, "processedへ移動できない: ${file.name}", e)
        false
    }

    enum class Status(val label: String) {
        INGESTED("取り込み"),
        MOVE_RECOVERED("移動を回復"),
        MOVE_FAILED("移動できず"),
        FAILED("失敗"),
    }

    data class Entry(val fileName: String, val status: Status, val detail: String)

    data class Report(val entries: List<Entry>) {
        fun count(status: Status): Int = entries.count { it.status == status }

        val hasProblem: Boolean
            get() = entries.any { it.status == Status.FAILED || it.status == Status.MOVE_FAILED }

        /** ログや画面に出す1行。 */
        fun summary(): String {
            if (entries.isEmpty()) return "新しいファイルは無し"
            return Status.entries
                .mapNotNull { s -> count(s).takeIf { it > 0 }?.let { "${s.label}${it}件" } }
                .joinToString(" / ")
        }
    }
}
