package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * 純資産の将来の見通し(E09-06)。このままの暮らしを続けたら、純資産がこの先どうなるかの目安。
 *
 * - **投資の系列**(想定利回りを決めた系列。E09-03): 利回りと積立額(E09-05)で増やす。計算は[FutureValue.valueAt]
 * - **それ以外の系列**(預金・年金など): 増やさない(利回り0%)。毎月の残りを足していく
 * - **毎月の残り** = 収入 − 消費 − 投資の系列への積立額。収入と消費は直近の月の平均(積立投資の目安(E10-04)と同じ)。
 *   大型出費(家電・車など)は毎月の残りからは引かない(本人が決めた)。残りがマイナスなら預金が減っていく
 * - **大型出費の予定**(E09-07): 見込み額のあるリマインダー(何年ごと・物価上昇つき)と期日のある目標を、その月に引く。
 *   引く前の額も持つ([Point.withoutPlansYen])。予定に入れていない出費は引かれない
 *
 * 1万円単位で四捨五入する。
 */
data class NetWorthOutlook(
    val nowYen: Long,
    /** 毎月の残り(投資の系列以外に足していく額)。 */
    val monthlyCashYen: Long,
    /** 投資の系列への積立額の合計。 */
    val monthlyInvestYen: Long,
    /** 投資の系列(名前と年利回り)。 */
    val investments: List<Pair<String, Int>>,
    /** 0年後(今)から[MAX_YEARS]年後まで1年ごと。 */
    val points: List<Point>,
    /** 期間中の大型出費の予定の合計(E09-07)。 */
    val plannedTotalYen: Long = 0,
) {
    /**
     * @param totalYen 大型出費の予定(E09-07)を引いた純資産
     * @param withoutPlansYen 予定を引く前
     */
    data class Point(val years: Int, val totalYen: Long, val investedYen: Long, val withoutPlansYen: Long = totalYen)

    fun at(years: Int): Point? = points.firstOrNull { it.years == years }

    /** 純資産に数える系列1つ分。 */
    data class Asset(
        val name: String,
        val valueYen: Long,
        /** 想定利回り。決めていなければnull(増やさない)。 */
        val rateBp: Int?,
        /** 積立額の月平均。利回りを決めた系列だけ使う。 */
        val monthlyYen: Long?,
    )

    companion object {
        const val MAX_YEARS = 30
        val TABLE_YEARS = listOf(5, 10, 20, 30)

        /**
         * @param plans 大型出費の予定(E09-07)。(今月から何か月後か, 額)。y年後の点には、0〜12y−1か月後の予定を引く
         * @return 系列が無ければnull
         */
        fun of(
            assets: List<Asset>,
            incomeYen: Long,
            consumptionYen: Long,
            maxYears: Int = MAX_YEARS,
            plans: List<Pair<Long, Long>> = emptyList(),
        ): NetWorthOutlook? {
            if (assets.isEmpty()) return null
            val invested = assets.filter { it.rateBp != null }
            val cash = assets.filter { it.rateBp == null }
            val investMonthly = invested.sumOf { it.monthlyYen ?: 0L }
            val cashMonthly = incomeYen - consumptionYen - investMonthly
            val cashNow = cash.sumOf { it.valueYen }
            val inPeriod = plans.filter { (m, _) -> m >= 0 && m < maxYears * 12L }

            val points = (0..maxYears).map { y ->
                val n = y * 12
                val inv = invested.sumOf { FutureValue.valueAt(it.valueYen, it.rateBp!!, it.monthlyYen ?: 0L, n) }
                val before = inv + cashNow + cashMonthly.toDouble() * n
                // 予定はお金が出ていくだけとみる(預金から払う。投資の系列は崩さない)
                val planned = inPeriod.filter { (m, _) -> m < n }.sumOf { it.second }
                Point(y, roundMan(before - planned), roundMan(inv), roundMan(before))
            }
            return NetWorthOutlook(
                nowYen = assets.sumOf { it.valueYen },
                monthlyCashYen = cashMonthly,
                monthlyInvestYen = investMonthly,
                investments = invested.map { it.name to it.rateBp!! },
                points = points,
                plannedTotalYen = inPeriod.sumOf { it.second },
            )
        }

        private fun roundMan(yen: Double): Long = (yen / 10_000).roundToLong() * 10_000

        /**
         * 純資産(E10-01)の内訳と明細から組み立てる。
         * [items]があれば、大型出費の予定(見込み額のあるリマインダーと期日のある目標。E09-04と同じもの)を引く(E09-07)。
         */
        fun build(
            netWorth: NetWorth,
            transactions: List<BankTransactionEntity>,
            today: LocalDate,
            items: List<Item> = emptyList(),
        ): NetWorthOutlook? {
            val thisMonth = YearMonth.from(today)
            val plans = ExpenseCalendar.build(items, today, MAX_YEARS.toLong())
                .map { ChronoUnit.MONTHS.between(thisMonth, YearMonth.from(it.date)) to it.amountYen }
            val monthly = Summary.monthlyCashflow(transactions)
            val plan = InvestPlan.of(monthly, emptyList(), today) ?: return null
            val assets = netWorth.breakdown.map { part ->
                val rate = part.metric.expectedReturnBp
                NetWorthOutlook.Asset(
                    name = part.metric.name,
                    valueYen = part.valueYen,
                    rateBp = rate,
                    monthlyYen = rate?.let { InvestPlan.monthlyFor(part.metric, transactions, monthly, today) },
                )
            }
            return of(assets, plan.incomeYen, plan.consumptionYen, plans = plans)
        }
    }
}
