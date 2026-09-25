package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * 支出の実績から目標額を出す(E07-06)。生活防衛資金のような
 * 「生活費の何か月分」で決まる目標に使う。
 */
object AutoTargets {

    data class Result(
        /** 目標額。使える月が無ければnull(不明)。 */
        val targetYen: Long?,
        /** 平均に使った月数。[AutoTarget.averageMonths]より少ないことがある。 */
        val monthsUsed: Int,
        val monthlyAverageYen: Long?,
    )

    /**
     * ## 使う月
     * - **今月は使わない**。途中までの支出なので、入れると平均が下がる
     * - **明細が1件も無い月は使わない**。明細は銀行から落とした期間しか無く、
     *   無い月は「支出0円」ではなく「分からない」
     * - 残った月のうち新しいほうから[AutoTarget.averageMonths]か月
     *
     * 支出は生活費([Cashflow.livingSpendingYen]。振替などを除いたもの)。
     *
     * @param monthly 月ごとの入出金(`Summary.monthlyCashflow`)
     */
    fun compute(rule: AutoTarget, monthly: List<Cashflow>, today: LocalDate): Result {
        val thisMonth = YearMonth.from(today)
        val usable = monthly
            .filter { it.count > 0 && YearMonth.from(it.period.start) < thisMonth }
            .sortedBy { it.period.start }
            .takeLast(rule.averageMonths)
        if (usable.isEmpty()) return Result(null, 0, null)

        val average = usable.sumOf { it.livingSpendingYen } / usable.size
        return Result(average * rule.coverMonths, usable.size, average)
    }
}
