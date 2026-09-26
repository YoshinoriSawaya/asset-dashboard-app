package com.yswy.assetdashboard.drive

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 前回の同期の結果(E03-05)。トップの1行と、タップしたときの詳細。
 *
 * ## 端末にだけ置く
 * 表示のための状態で、Driveから作り直す対象ではない。失っても次の同期まで
 * 1行が出ないだけ。詳細の中身はDriveのlogs(E01-08)と同じものを端末にも
 * 持っておき、オフラインでも見られるようにする。
 *
 * ## 生のCSV行は持たない
 * 読めなかった行は[SyncLog.render]を通して、行番号と理由だけにする
 * (口座番号・氏名が入るため。E01-08と同じ扱い)。
 */
data class LastSync(
    val at: Instant,
    /** `取り込み2件・失敗1件` のような要約。 */
    val summary: String,
    /** 失敗・移動できず・読めない行・キャッシュを作り直せなかった、のどれかがある。 */
    val hasProblem: Boolean,
    /** タップしたときに出す詳細。 */
    val detail: String,
) {
    fun line(zone: ZoneId = ZoneId.systemDefault()): String =
        "前回の同期 ${at.atZone(zone).format(LINE_TIME)}  $summary"

    fun toJson(): String = JSONObject()
        .put("at", at.toEpochMilli())
        .put("summary", summary)
        .put("hasProblem", hasProblem)
        .put("detail", detail)
        .toString()

    companion object {
        private val LINE_TIME = DateTimeFormatter.ofPattern("M/d H:mm")

        fun fromJson(json: String): LastSync? = runCatching {
            val o = JSONObject(json)
            LastSync(
                at = Instant.ofEpochMilli(o.getLong("at")),
                summary = o.getString("summary"),
                hasProblem = o.getBoolean("hasProblem"),
                detail = o.getString("detail"),
            )
        }.getOrNull()

        /** 同期できたとき。ファイルごとの結果と、読めなかった行の行番号・理由を詳細に入れる。 */
        fun of(result: FullSync.Result, at: Instant, zone: ZoneId = ZoneId.systemDefault()): LastSync {
            val inbox = result.inbox
            val ok = inbox.count(InboxSync.Status.INGESTED) + inbox.count(InboxSync.Status.REINGESTED)
            val failed = inbox.count(InboxSync.Status.FAILED)
            val moveFailed = inbox.count(InboxSync.Status.MOVE_FAILED)
            val skippedRows = inbox.skipped.values.sumOf { it.size }
            val cacheKept = result.cache is CacheSync.Outcome.Kept
            // backupの抜け(E01-16)。取り込み直さないと、DBを失ったときに戻せない
            val missingBackups = (result.cache as? CacheSync.Outcome.Rebuilt)?.missingBackups?.size ?: 0

            val summary = buildList {
                add("取り込み${ok}件")
                add("失敗${failed}件")
                if (moveFailed > 0) add("移動できず${moveFailed}件")
                if (skippedRows > 0) add("読めない行${skippedRows}件")
                if (cacheKept) add("キャッシュ未更新")
                if (missingBackups > 0) add("backup無し${missingBackups}件")
            }.joinToString("・")

            val hasProblem = failed > 0 || moveFailed > 0 || skippedRows > 0 || cacheKept || missingBackups > 0
            val detail = buildString {
                append(result.describe())
                if (inbox.hasProblem || skippedRows > 0) {
                    append("\n\n")
                    append(SyncLog.render(inbox, inbox.skipped, ZonedDateTime.ofInstant(at, zone)))
                }
            }
            return LastSync(at, summary, hasProblem, detail)
        }

        /** 同期できなかったとき(オフライン・失敗・同意待ち)。 */
        fun notSynced(reason: String, at: Instant, isProblem: Boolean) =
            LastSync(at, reason, isProblem, reason)
    }
}

/** [LastSync]を端末に置く。 */
class LastSyncStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("last_sync", Context.MODE_PRIVATE)

    fun load(): LastSync? = prefs.getString(KEY, null)?.let(LastSync::fromJson)

    fun save(value: LastSync) {
        prefs.edit().putString(KEY, value.toJson()).apply()
    }

    private companion object {
        const val KEY = "value"
    }
}
