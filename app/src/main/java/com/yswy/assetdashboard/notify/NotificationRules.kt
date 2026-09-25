package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.FundOutlook
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.SyncStatus
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/**
 * 今日、何を通知するか(E05)。端末の毎日の確認([DailyCheck])から呼ぶ純粋関数。
 *
 * ## 通知の文面に金額を入れない
 * 通知はロック画面にも出る。ウィジェット(E04)と同じく、人前で見えても
 * 困らないよう、金額は出さずアプリへ誘うだけにする。
 *
 * ## 同じ通知を繰り返しすぎない
 * 通知ごとに「最後に出した日」を覚えておき([lastNotified])、種類ごとの
 * 間隔が空くまでは出さない。
 */
object NotificationRules {

    data class Notice(
        /** 同じ通知を見分ける鍵。最後に出した日の記録にも使う。 */
        val key: String,
        val title: String,
        val text: String,
    )

    /** CSVの催促をもう一度出すまでの日数(E05-03)。 */
    const val SYNC_REPEAT_DAYS = 3L

    /** リマインダーを期日の何日前に出すか(E05-05/06)。当日にも出す。 */
    const val REMINDER_ADVANCE_DAYS = 7L

    /** 目標を下回った通知の間隔(E07-07)。 */
    const val SHORTFALL_REPEAT_DAYS = 30L

    /** 年末の枠の通知の間隔(E07-09)。12月の間だけ。 */
    const val ALLOWANCE_REPEAT_DAYS = 7L

    fun evaluate(
        today: LocalDate,
        sync: SyncStatus,
        overviews: List<ItemOverview>,
        lastNotified: Map<String, LocalDate>,
    ): List<Notice> {
        val notices = mutableListOf<Notice>()

        fun daysSince(key: String): Long? = lastNotified[key]?.let { ChronoUnit.DAYS.between(it, today) }
        fun intervalPassed(key: String, days: Long) = (daysSince(key) ?: Long.MAX_VALUE) >= days

        // E05-02/03: 同期が必要なら催促。済むまで3日ごとに繰り返す
        if (sync.isDue && intervalPassed(KEY_SYNC, SYNC_REPEAT_DAYS)) {
            notices += Notice(
                KEY_SYNC,
                "CSVを置いて同期してください",
                sync.latestDataDate?.let { "手元のデータは $it までです。銀行のCSVを inbox に置いて、アプリを開いてください。" }
                    ?: "まだデータがありません。銀行のCSVを inbox に置いて、アプリを開いてください。",
            )
        }

        for (overview in overviews) {
            when (overview) {
                // E05-05/06: 期日の7日前と当日。1回の期日につき、それぞれ1度だけ
                is ItemOverview.Reminder -> {
                    val due = overview.item.dueDate
                    val days = overview.daysLeft
                    val stage = when {
                        days == 0L -> "today"
                        days in 1..REMINDER_ADVANCE_DAYS -> "soon"
                        else -> null
                    } ?: continue
                    val key = "reminder:${overview.item.id}:$due:$stage"
                    if (key in lastNotified) continue
                    notices += Notice(
                        key,
                        if (stage == "today") "今日: ${overview.item.name}" else "あと${days}日: ${overview.item.name}",
                        "期日 $due。済んだらアプリで「済みにする」を押してください。",
                    )
                }

                is ItemOverview.Goal -> {
                    val goal = overview.item
                    val target = overview.targetYen ?: continue
                    val current = overview.currentYen ?: continue

                    if (goal.resetsYearly) {
                        // E07-09: 12月に入って枠が残っていれば、週に1度
                        val key = "allowance:${goal.id}"
                        if (today.month == Month.DECEMBER && current < target && intervalPassed(key, ALLOWANCE_REPEAT_DAYS)) {
                            notices += Notice(key, "${goal.name}の枠が残っています", "${today.year}年の枠は年末までです。アプリで残りを確認してください。")
                        }
                    } else if (goal.autoTarget != null && current < target) {
                        // E07-07: 生活費から出す目標(生活防衛資金)を下回ったら、30日に1度
                        val key = "shortfall:${goal.id}"
                        if (intervalPassed(key, SHORTFALL_REPEAT_DAYS)) {
                            notices += Notice(key, "${goal.name}が目標を下回っています", "アプリで回復の目安を確認してください。")
                        }
                    } else if (goal.autoTarget != null) {
                        // E09-02: まだ目標以上だが、このペースだと数か月で割るなら、下回る前に知らせる
                        val months = FundOutlook.of(overview)?.monthsUntilBelowTarget
                        val key = "outlook:${goal.id}"
                        if (months != null && months <= FundOutlook.WARN_MONTHS && intervalPassed(key, SHORTFALL_REPEAT_DAYS)) {
                            notices += Notice(key, "${goal.name}が減っています", "このペースだと数か月で目標を割ります。アプリで見通しを確認してください。")
                        }
                    }
                }

                is ItemOverview.Metric -> Unit
            }
        }
        return notices
    }

    const val KEY_SYNC = "sync"
}
