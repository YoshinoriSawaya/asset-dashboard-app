package com.yswy.assetdashboard.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 「同期が必要か」の判定。アプリを開いたとき(E02-04)と、
 * ウィジェットの色(E04-02)が同じ判定を使う。
 *
 * ## 「前回同期した日」ではなく「データの新しさ」で見る
 * 前回同期した日から数えると、期限が来てアプリを開いた瞬間に自動同期が
 * 走り、CSVを1つも足していないのに期限が戻ってしまう。
 * 知らせたいのは「新しいCSVをinboxに置いてほしい」なので、手元の
 * データがいつの時点のものかで判定する。
 *
 * データの日付はDriveのbackupから来るので、入れ直しても同じ判定になる
 * (ローカルにしか無い「最後に同期した時刻」を正にしない)。
 */
object SyncPolicy {

    /** データがこれより古くなったら同期が必要。 */
    val DUE_AFTER_DAYS = 30L

    /**
     * 自動同期を試してからこれだけ経つまでは、開いても自動では試さない。
     * 期限切れのままCSVが来ないと、開くたびに同期が走ってしまうため。
     * 手動の同期ボタンはこれに関係なく押せる。
     */
    val AUTO_SYNC_INTERVAL: Duration = Duration.ofHours(1)

    /** 最新のデータの日付から見た期限。データが無ければnull(=もう期限切れ)。 */
    fun dueDate(latestDataDate: LocalDate?): LocalDate? = latestDataDate?.plusDays(DUE_AFTER_DAYS)

    fun isDue(latestDataDate: LocalDate?, today: LocalDate): Boolean {
        val due = dueDate(latestDataDate) ?: return true
        return today.isAfter(due)
    }

    fun shouldAutoSync(
        latestDataDate: LocalDate?,
        today: LocalDate,
        lastAutoSyncAt: Instant?,
        now: Instant,
    ): Boolean {
        if (!isDue(latestDataDate, today)) return false
        if (lastAutoSyncAt == null) return true
        return Duration.between(lastAutoSyncAt, now) >= AUTO_SYNC_INTERVAL
    }

    /** Metricと明細のうち新しいほう。どちらかしか無い期間もある。 */
    fun latestOf(vararg dates: LocalDate?): LocalDate? = dates.filterNotNull().maxOrNull()
}
