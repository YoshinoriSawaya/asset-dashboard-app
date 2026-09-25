package com.yswy.assetdashboard.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate

/** 手元のキャッシュから見た同期の状態。画面とウィジェット(E04)で使う。 */
data class SyncStatus(
    /** 手元のいちばん新しいデータの日付。何も無ければnull。 */
    val latestDataDate: LocalDate?,
    val isDue: Boolean,
) {
    val dueDate: LocalDate? get() = SyncPolicy.dueDate(latestDataDate)

    fun describe(): String = when (latestDataDate) {
        null -> "データなし(同期が必要)"
        else -> buildString {
            append("最新データ: $latestDataDate(期限 $dueDate)")
            if (isDue) append(" 同期が必要")
        }
    }

    companion object {
        suspend fun load(db: AppDatabase, today: LocalDate = LocalDate.now()): SyncStatus {
            val latest = SyncPolicy.latestOf(
                db.metricPointDao().latestDate(),
                db.bankTransactionDao().latestDate(),
            )
            return SyncStatus(latest, SyncPolicy.isDue(latest, today))
        }
    }
}

/**
 * 自動同期を最後に試した時刻。[SyncPolicy.AUTO_SYNC_INTERVAL]の間引きにだけ使う。
 *
 * Driveに置かないローカルの状態だが、失っても「次に開いたとき1回余計に
 * 同期する」だけなので構わない。
 */
class AutoSyncPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("auto_sync", Context.MODE_PRIVATE)

    var lastAttemptAt: Instant?
        get() = prefs.getLong(KEY, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) {
            prefs.edit().putLong(KEY, value?.toEpochMilli() ?: 0L).apply()
        }

    private companion object {
        const val KEY = "last_attempt_at"
    }
}
