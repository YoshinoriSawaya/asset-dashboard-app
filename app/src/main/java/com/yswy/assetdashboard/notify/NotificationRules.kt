package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.CategorySpending
import com.yswy.assetdashboard.data.Drawdown
import com.yswy.assetdashboard.data.FixedCosts
import com.yswy.assetdashboard.ui.AiExport
import com.yswy.assetdashboard.data.FundOutlook
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.RampUp
import com.yswy.assetdashboard.data.SyncStatus
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
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

    /** 積み増しの期間中の通知の間隔(E07-11)。最初の1回が「始める時期です」。 */
    const val RAMP_UP_REPEAT_DAYS = 30L

    /** 生活防衛資金が下限を割ったときの間隔(E07-12)。目標額を割っただけより詰める。 */
    const val BELOW_FLOOR_REPEAT_DAYS = 7L

    fun evaluate(
        today: LocalDate,
        sync: SyncStatus,
        overviews: List<ItemOverview>,
        lastNotified: Map<String, LocalDate>,
        /** 前の月のカテゴリ別の支出(E05-08)。その月の明細が出そろっていなければnull([lastMonthSpending]) */
        lastMonth: CategorySpending? = null,
        /** 前の月までの固定費(E07-28)。前の月が締まっていなければnull([lastMonthFixedCosts]) */
        fixedCosts: FixedCosts? = null,
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
                        when (Drawdown.stateOf(overview)) {
                            // E07-12: 下限を割ったら、週に1度
                            Drawdown.FundState.BELOW_FLOOR -> {
                                val key = "belowfloor:${goal.id}"
                                if (intervalPassed(key, BELOW_FLOOR_REPEAT_DAYS)) {
                                    notices += Notice(key, "${goal.name}が下限を割っています", "アプリで、まず下限まで戻す目安を確認してください。")
                                }
                            }
                            // E07-12: 下限までは取り崩してよい範囲。咎めずに回復の目安へ誘う
                            Drawdown.FundState.DRAWN -> {
                                val key = "shortfall:${goal.id}"
                                if (intervalPassed(key, SHORTFALL_REPEAT_DAYS)) {
                                    notices += Notice(key, "${goal.name}を取り崩しています", "下限まではまだ余裕があります。アプリで回復の目安を確認してください。")
                                }
                            }
                            // E07-07: 下限を決めていなければ、目標を割ったら30日に1度
                            else -> {
                                val key = "shortfall:${goal.id}"
                                if (intervalPassed(key, SHORTFALL_REPEAT_DAYS)) {
                                    notices += Notice(key, "${goal.name}が目標を下回っています", "アプリで回復の目安を確認してください。")
                                }
                            }
                        }
                    } else if (RampUp.of(goal, target, current, today) == RampUp.Overdue) {
                        // E07-12: 期日に届かなかった。生活防衛資金で補えるかへ誘う。期日ごとに1度
                        val key = "rampup:${goal.id}:${goal.dueDate}:overdue"
                        if (key !in lastNotified) {
                            notices += Notice(key, "${goal.name}の期日が過ぎました", "足りない分を生活防衛資金で補えるか、アプリで確認してください。")
                        }
                    } else if (RampUp.of(goal, target, current, today) is RampUp.Active) {
                        // E07-11: 期日に向けて積み増す時期に入ったら知らせ、届くまで月に1度。
                        // 期日を変えたら、始めの通知からやり直す
                        val key = "rampup:${goal.id}:${goal.dueDate}"
                        if (intervalPassed(key, RAMP_UP_REPEAT_DAYS)) {
                            notices += if (key !in lastNotified) {
                                Notice(key, "${goal.name}の積み増しを始める時期です", "期日 ${goal.dueDate} に向けて、アプリで月々の目安を確認してください。")
                            } else {
                                Notice(key, "${goal.name}の積み増し", "期日 ${goal.dueDate} まで。アプリで今月の目安を確認してください。")
                            }
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

        // E05-08: 前の月に、いつもよりはっきり多い消費のカテゴリがあれば、その月につき1度。金額は出さずカテゴリ名だけ
        lastMonth?.let { spending ->
            val names = highConsumption(spending)
            val key = "highspend:${spending.month}"
            if (names.isNotEmpty() && key !in lastNotified) {
                notices += Notice(
                    key,
                    "${spending.month.monthValue}月は${names.joinToString("・")}がいつもより多め",
                    "アプリの「カテゴリ別の支出」で中身を確認してください。",
                )
            }
        }
        // E07-28: 前の月に値上がりした固定費があれば、その月につき1度。金額は出さず名前だけ(振込の相手は伏せる)
        fixedCosts?.let { costs ->
            val names = costs.increased.map { AiExport.maskedName(it.description) }.distinct()
            val key = "priceup:${costs.months.last()}"
            if (names.isNotEmpty() && key !in lastNotified) {
                val shown = names.take(2).joinToString("・") + if (names.size > 2) "など" else ""
                notices += Notice(key, "固定費が値上がりしました", "$shown。アプリの「固定費・サブスク」で確認してください。")
            }
        }
        return notices
    }

    /**
     * 通知に使う固定費(E07-28)。見た月の最後が前の月で、前の月が締まっている(今月の明細がある)ときだけ。
     * 前の月の明細が無い(同期していない)まま古い月で判定しないように。
     */
    fun lastMonthFixedCosts(transactions: List<BankTransactionEntity>, today: LocalDate): FixedCosts? {
        val thisMonth = YearMonth.from(today)
        if (transactions.none { YearMonth.from(it.date) == thisMonth }) return null
        return FixedCosts.of(transactions, today)?.takeIf { it.months.last() == thisMonth.minusMonths(1) }
    }

    /**
     * いつもより多い(E07-25の ▲)消費のカテゴリの名前(E05-08)。額の多い順。
     * 大型出費・積立投資は積立や計画で準備しているので、▲でも知らせない(画面の ▲ はそのまま)。
     */
    fun highConsumption(spending: CategorySpending): List<String> =
        spending.rows.filter { it.high && (it.kind?.isConsumption ?: true) }.map { it.category ?: "カテゴリなし" }

    /**
     * 通知に使う前の月のカテゴリ別の支出(E05-08)。前の月の明細が出そろっていなければnull。
     * 今月の日付の明細が1件でもあれば、前の月は締まったとみる(明細は同期したときにしか入らない)。
     */
    fun lastMonthSpending(transactions: List<BankTransactionEntity>, today: LocalDate): CategorySpending? {
        val thisMonth = YearMonth.from(today)
        if (transactions.none { YearMonth.from(it.date) == thisMonth }) return null
        val spending = CategorySpending.of(transactions, thisMonth.minusMonths(1))
        return spending.takeIf { it.averageMonths > 0 }
    }

    const val KEY_SYNC = "sync"
}
