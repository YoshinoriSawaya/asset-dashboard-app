package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * 積立投資の目安(E10-04)。毎月の収入から、消費と守りのお金を引いた残りの8割(本人が決めた。ゆとり2割)。
 *
 * ```
 * 目安 = (収入 − 消費(生活費+遊び代) − 生活防衛資金の月額 − 大型出費の積立の月額 − 積み増し中の目標の月額) × 8割
 * ```
 * - 収入・消費・今の積立額は、直近の月の平均。月の選び方は生活防衛資金の目標額(E07-06)とそろえる
 *   (今月と、明細の無い月は使わない)
 * - **ボーナスも通常の収入として平均に入れる**(本人が決めた)
 * - 振替(自分の口座どうし)は収入にも消費にも入れない。大型出費(家具・家電)は積立の月額で引くので、
 *   消費には入れない(二重に引かない)。積立投資は消費に入れず、今の積立額として比べる(E07-21)
 * - 生活防衛資金の月額は、埋める期間を決めた目標(E07-19)で足りないものの月額。
 *   大型出費の積立の月額は、積立の目標(E07-15)のならした月額。積み増し中の目標(E07-11。車の頭金など)は、
 *   期日までの月額を先に取る(元のE10-04の「頭金の準備を優先する」)
 *
 * 1000円単位で切り捨てる(目安を多めに出さない)。
 */
data class InvestPlan(
    val months: Int,
    val incomeYen: Long,
    val consumptionYen: Long,
    val refillYen: Long,
    val sinkingYen: Long,
    /** 積み増しの時期に入った目標(E07-11)の月額の合計。 */
    val rampUpYen: Long,
    /** 今の積立投資(月平均)。 */
    val currentYen: Long,
    /** 目安。0を下回れば0。 */
    val suggestedYen: Long,
    /** 消費のうち、カテゴリの無い明細(月平均。E07-23)。生活費として数えている */
    val uncategorizedYen: Long = 0,
) {
    /** 消費のうちカテゴリの無い明細の割合(E07-23)。消費が無ければnull。 */
    val uncategorizedShare: Double?
        get() = if (consumptionYen > 0) uncategorizedYen.toDouble() / consumptionYen else null

    /** 収入から全部を引いた残り(ゆとりを引く前)。 */
    val surplusYen: Long get() = incomeYen - consumptionYen - refillYen - sinkingYen - rampUpYen

    companion object {
        const val DEFAULT_MONTHS = 6

        /** ゆとり。残りのこの割合は投資に回さない。 */
        const val MARGIN = 0.2

        /**
         * @param monthly 月ごとの入出金(`Summary.monthlyCashflow`)
         * @param goals 一覧の目標(生活防衛資金の月額・大型出費の積立の月額を拾う)
         * @return 使える月が無ければnull
         */
        fun of(monthly: List<Cashflow>, goals: List<ItemOverview.Goal>, today: LocalDate, months: Int = DEFAULT_MONTHS): InvestPlan? {
            val usable = usableMonths(monthly, today, months)
            if (usable.isEmpty()) return null

            val n = usable.size
            val income = usable.sumOf { it.incomeYen } / n
            val consumption = usable.sumOf { it.consumptionYen } / n
            val current = usable.sumOf { it.investmentYen } / n
            val refill = goals.sumOf { (Refill.of(it) as? Refill.Short)?.monthlyYen ?: 0L }
            val sinking = goals.sumOf { it.sinking?.monthlyYen ?: 0L }
            val rampUp = goals.sumOf { (RampUp.of(it.item, it.targetYen, it.currentYen, today) as? RampUp.Active)?.monthlyYen ?: 0L }
            val surplus = income - consumption - refill - sinking - rampUp
            val suggested = if (surplus <= 0) 0L else (surplus * (1 - MARGIN)).toLong() / 1_000 * 1_000
            val uncategorized = usable.sumOf { it.uncategorizedYen } / n
            return InvestPlan(n, income, consumption, refill, sinking, rampUp, current, suggested, uncategorized)
        }

        /**
         * 今の積立投資の月平均(E07-21)。将来の評価額(E09-03)にも使う。
         * 使える月が無いか、積立投資の明細が1件も無ければnull(まだ分からない)。
         */
        fun currentMonthlyYen(monthly: List<Cashflow>, today: LocalDate, months: Int = DEFAULT_MONTHS): Long? {
            val usable = usableMonths(monthly, today, months)
            if (usable.isEmpty()) return null
            return (usable.sumOf { it.investmentYen } / usable.size).takeIf { it > 0 }
        }

        /** 平均に使う月。今月と、明細の無い月は使わない(生活防衛資金(E07-06)とそろえる)。古い順。 */
        private fun usableMonths(monthly: List<Cashflow>, today: LocalDate, months: Int): List<Cashflow> {
            val thisMonth = YearMonth.from(today)
            return monthly
                .filter { it.count > 0 && YearMonth.from(it.period.start) < thisMonth }
                .sortedBy { it.period.start }
                .takeLast(months)
        }
    }
}
