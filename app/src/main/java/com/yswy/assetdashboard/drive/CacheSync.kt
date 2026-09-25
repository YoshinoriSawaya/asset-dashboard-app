package com.yswy.assetdashboard.drive

import android.util.Log
import androidx.room.withTransaction
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemEntity
import com.yswy.assetdashboard.data.ItemType
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.csv.Deduplication
import com.yswy.assetdashboard.csv.ParsedData

/**
 * Driveの中身からローカルキャッシュ(Room)を作り直す。
 *
 * ## 差分ではなく丸ごと作り直す
 * 入力は `backup/`(CSV由来)・`corrections/`・`settings/` の3つで、
 * 同期のたびにこれを全部読んで3テーブルを入れ替える。
 *
 * - Driveと食い違う状態が残らない。補正を消した、項目を消した、なども
 *   次の同期でそのまま反映される(差分だと「消した」を追いかける必要がある)
 * - 取り込み済みのファイルもbackupから入るので、processedのファイルを
 *   inboxへ戻して読み直す必要が無い
 * - 入れ直し(E06-02)と同じ道を通るので、「Driveから再構築できる」が
 *   毎回の同期で確かめられている
 *
 * 代わりに、同期のたびにbackupを全部落とす。ファイル数は月に数件なので
 * 当面は問題にならない。遅くなったら、modifiedTimeで変わったものだけ
 * 落とすようにする。
 *
 * ## 作り直せないときは今のキャッシュを残す
 * 一覧が取れない、ダウンロードが途中で失敗した、correctionsやsettingsが
 * 読めない、のいずれかなら入れ替えない。空や欠けたデータで上書きすると、
 * 資産が消えたように見える。次の同期でやり直せばよい。
 *
 * 一方、**中身の形が読めないbackupファイル**は1つ飛ばして先へ進む
 * (1ファイルのために全部を止めない)。飛ばしたものは報告に出す。
 */
object CacheSync {

    private const val TAG = "CacheSync"

    /** Roomに入れるもの一式。 */
    data class Snapshot(
        val items: List<ItemEntity>,
        val points: List<MetricPointEntity>,
        val transactions: List<BankTransactionEntity>,
        /** 重複として落とした行数(期間の重なるCSVなど)。 */
        val droppedDuplicates: Int,
    )

    sealed interface Outcome {
        data class Rebuilt(
            val snapshot: Snapshot,
            val backupFiles: Int,
            /** 形が読めず飛ばしたbackupファイル名。 */
            val unreadable: List<String>,
        ) : Outcome

        /** 作り直さなかった。今のキャッシュはそのまま。 */
        data class Kept(val reason: String) : Outcome
    }

    fun Outcome.summary(): String = when (this) {
        is Outcome.Rebuilt -> buildString {
            append("キャッシュ: Metric ${snapshot.points.size}点 / 明細 ${snapshot.transactions.size}件")
            append(" / 項目 ${snapshot.items.size}件")
            append(" (backup ${backupFiles}件")
            if (snapshot.droppedDuplicates > 0) append(", 重複 ${snapshot.droppedDuplicates}行")
            if (unreadable.isNotEmpty()) append(", 読めず ${unreadable.size}件")
            append(")")
        }
        is Outcome.Kept -> "キャッシュは更新せず: $reason"
    }

    suspend fun rebuild(api: DriveApi, folders: AppFolders, db: AppDatabase): Outcome {
        val files = try {
            api.listFiles(folders.backup)
        } catch (e: Exception) {
            Log.w(TAG, "backupの一覧を取れない", e)
            return Outcome.Kept("backupの一覧を取れない")
        }.filter { it.name.endsWith(".json") }
            // 後から取り込んだものを後ろに。Metricの後勝ちがこの順序に依存する。
            .sortedBy { it.modifiedTime }

        val backups = mutableListOf<BackupReader.Backup>()
        val unreadable = mutableListOf<String>()
        for (file in files) {
            val bytes = try {
                api.download(file.id)
            } catch (e: Exception) {
                // 通信の問題なら次回は読める。欠けたまま入れ替えない。
                Log.w(TAG, "backupを落とせない: ${file.name}", e)
                return Outcome.Kept("backupを落とせない: ${file.name}")
            }
            val backup = runCatching { BackupReader.parse(String(bytes, Charsets.UTF_8)) }.getOrNull()
            if (backup == null) {
                Log.w(TAG, "backupの形を読めないので飛ばす: ${file.name}")
                unreadable += file.name
            } else {
                backups += backup
            }
        }

        val corrections = Corrections.load(api, folders)
            ?: return Outcome.Kept("correctionsを読めない")
        val settings = Settings.load(api, folders)
            ?: return Outcome.Kept("settingsを読めない")
        val exclusions = SpendingRules.load(api, folders)
            ?: return Outcome.Kept("生活費から除く決まりを読めない")

        val snapshot = build(backups, corrections, settings, exclusions)

        db.withTransaction {
            db.itemDao().deleteAll()
            db.metricPointDao().deleteAll()
            db.bankTransactionDao().deleteAll()
            db.itemDao().upsertAll(snapshot.items)
            db.metricPointDao().upsertAll(snapshot.points)
            db.bankTransactionDao().insertAll(snapshot.transactions)
        }

        return Outcome.Rebuilt(snapshot, files.size, unreadable).also {
            Log.i(TAG, it.summary())
        }
    }

    /**
     * Driveの中身から、Roomに入れるもの一式を組み立てる。
     *
     * @param backups 古い順(後から取り込んだものが後ろ)
     */
    fun build(
        backups: List<BackupReader.Backup>,
        corrections: List<Corrections.Entry>,
        settings: List<ItemEntity>,
        /** 摘要にこの言葉を含む出金を生活費から除く(E07-06)。 */
        exclusions: List<String> = emptyList(),
    ): Snapshot {
        var dropped = 0

        // 取引は先勝ち、Metricは後勝ち(E01-11)。
        val transactions = LinkedHashMap<String, BankTransactionEntity>()
        val points = LinkedHashMap<String, MetricPointEntity>()

        for (backup in backups) {
            when (val data = backup.data) {
                is ParsedData.Transactions -> data.rows.forEach { row ->
                    val entity = BankTransactionEntity.from(row, backup.sourceFileId).copy(
                        excludedFromSpending = SpendingRules.matches(row.description, exclusions),
                    )
                    if (transactions.putIfAbsent(entity.dedupKey, entity) != null) dropped++
                }
                is ParsedData.Metrics -> data.points.forEach { point ->
                    if (points.put(Deduplication.keyOf(point), MetricPointEntity.from(point)) != null) dropped++
                }
            }
        }

        // 補正を重ねる。由来を残すので Corrections.apply ではなくここで重ねる。
        for ((key, entry) in Corrections.effective(corrections)) {
            points[key] = MetricPointEntity(
                metricKey = entry.metricKey,
                date = entry.date,
                valueYen = entry.valueYen,
                origin = when (entry.kind) {
                    Corrections.Kind.OVERRIDE -> MetricOrigin.OVERRIDE
                    Corrections.Kind.MANUAL -> MetricOrigin.MANUAL
                },
            )
        }

        return Snapshot(
            items = withAutoMetrics(settings, points.values.map { it.metricKey }),
            points = points.values.toList(),
            transactions = transactions.values.toList(),
            droppedDuplicates = dropped,
        )
    }

    /**
     * settingsの項目に、まだ載っていないmetricKeyのMetric項目を足す(E02-01)。
     *
     * 自動で足した分はsettingsに書かない。人が名前を変えるなどしたときに
     * 初めて書く。こうしておけば作り直しても同じ一覧になる。
     */
    private fun withAutoMetrics(settings: List<ItemEntity>, metricKeys: List<String>): List<ItemEntity> {
        val ids = settings.map { it.id }.toSet()
        val auto = metricKeys.distinct().sorted()
            .filter { Item.metricId(it) !in ids }
            .map { ItemEntity(id = Item.metricId(it), type = ItemType.METRIC, name = it, metricKey = it) }
        return settings + auto
    }
}
