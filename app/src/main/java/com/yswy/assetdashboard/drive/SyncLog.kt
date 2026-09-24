package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.csv.SkippedRow
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 取り込みに失敗したファイルの理由を、Driveのlogsフォルダに残す。
 *
 * ## 生のCSV行は書かない
 * 読めなかった行には口座番号や氏名が入る(E01-04で実際に確認した)。
 * logsはDriveにあり、logcatより人目に触れやすい。
 *
 * 行番号と理由さえあれば、processedにある元ファイルを開いて
 * その行を見れば済む。**元ファイルは本人の手元にあるので、
 * ログに中身を複製する必要は無い。**
 *
 * ## 1回の同期につき1ファイル
 * 追記方式にすると「落として、足して、上げ直す」が要り、
 * 途中で失敗すると過去のログごと壊す。毎回新しいファイルを作れば
 * 既存のログは絶対に壊れない。
 *
 * 問題が無かった同期ではファイルを作らない。logsに何かあること自体が
 * 「見るべきものがある」という合図になる。
 */
object SyncLog {

    private const val TAG = "SyncLog"

    private val FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 問題があればlogsに書く。問題が無ければ何もしない。
     *
     * ログを書けなくても同期自体は成功しているので、例外は投げない。
     * ここで落ちると本末転倒。
     */
    suspend fun writeIfNeeded(
        api: DriveApi,
        folders: AppFolders,
        report: InboxSync.Report,
        details: Map<String, List<SkippedRow>> = emptyMap(),
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Boolean {
        if (!report.hasProblem && details.values.all { it.isEmpty() }) return false

        val content = render(report, details, now)
        val name = "${now.format(FILE_NAME_FORMAT)}.log"

        return try {
            api.uploadTextFile(name, folders.logs, content)
            Log.i(TAG, "logsに $name を書いた")
            true
        } catch (e: Exception) {
            // 書けなくても同期は成立している。落とさない。
            Log.w(TAG, "logsに書けなかった", e)
            false
        }
    }

    /** ログの中身。テストから直接確かめられるよう分けてある。 */
    fun render(
        report: InboxSync.Report,
        details: Map<String, List<SkippedRow>>,
        now: ZonedDateTime,
    ): String = buildString {
        appendLine("同期日時: ${now.format(TIMESTAMP_FORMAT)}")
        appendLine("結果: ${report.summary()}")
        appendLine()

        for (entry in report.entries) {
            val skipped = details[entry.fileName].orEmpty()
            val isProblem = entry.status == InboxSync.Status.FAILED ||
                entry.status == InboxSync.Status.MOVE_FAILED
            if (!isProblem && skipped.isEmpty()) continue

            appendLine("── ${entry.fileName}")
            appendLine("   ${entry.status.label}: ${entry.detail}")

            if (skipped.isNotEmpty()) {
                appendLine("   読めなかった行 ${skipped.size}件:")
                // 行番号と理由だけ。中身は書かない(元ファイルはprocessedにある)。
                skipped.take(50).forEach { appendLine("     ${it.lineNumber}行目: ${it.reason}") }
                if (skipped.size > 50) appendLine("     ...ほか${skipped.size - 50}件")
            }
            appendLine()
        }

        appendLine("※ 読めなかった行の中身はここには書かない(口座番号などが入るため)。")
        appendLine("   元のファイルはprocessedフォルダにあるので、行番号で突き合わせる。")
    }
}
