package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * 前の月の振り返り(E03-10)。前の月の結果を1画面に集める。中身はどれもほかの画面と同じ計算:
 * 入出金(`Summary.cashflow`)、いつもより多いカテゴリ(E07-25)、予算(E07-29)、固定費の値上がり・止まったもの(E07-26・E07-28)、
 * 純資産の増減(E10-01)。
 */
data class MonthlyReview(
    val month: YearMonth,
    val incomeYen: Long,
    /** 消費(生活費 + 遊び代 + カテゴリなし)。 */
    val consumptionYen: Long,
    val investmentYen: Long,
    /** 純資産のその月の増減。数える系列が無い・前の月の値が無ければnull。 */
    val netWorthChangeYen: Long?,
    /** いつもよりはっきり多かったカテゴリ(E07-25の ▲)。 */
    val high: List<CategorySpending.Row>,
    /** 予算を決めたカテゴリの結果(E07-29)。使った割合の多い順。 */
    val budgets: List<BudgetResult>,
    /** その月に値上がりした固定費(E07-28)。 */
    val increased: List<FixedCosts.Item>,
    /** その月に止まった固定費(E07-26の「先月は無し」)。 */
    val stopped: List<FixedCosts.Item>,
    /** 今月の明細がまだ無い(その月の明細が出そろっていないかもしれない)。 */
    val maybeIncomplete: Boolean,
) {
    /** 収入 − 消費。 */
    val surplusYen: Long get() = incomeYen - consumptionYen

    data class BudgetResult(val category: String, val usedYen: Long, val budgetYen: Long) {
        val over: Boolean get() = usedYen > budgetYen
        val ratio: Double get() = usedYen.toDouble() / budgetYen
    }

    companion object {
        /**
         * 今月より前で明細のある、いちばん新しい月の振り返り。
         * @param netWorthSeries 純資産の合算の点(E10-01)。無ければ増減は出さない
         * @return 明細のある月が今月より前に無ければnull
         */
        fun of(
            transactions: List<BankTransactionEntity>,
            today: LocalDate,
            budgets: Map<String, Long> = emptyMap(),
            netWorthSeries: List<MetricPointEntity> = emptyList(),
        ): MonthlyReview? {
            val thisMonth = YearMonth.from(today)
            val month = CategorySpending.months(transactions).firstOrNull { it < thisMonth } ?: return null
            val cash = Summary.cashflow(transactions, Summary.month(month))
            val spending = CategorySpending.of(transactions, month)
            // 固定費は、見た月の最後がこの月のときだけ(この月の変化として出す)
            val fixed = FixedCosts.of(transactions, today)?.takeIf { it.months.last() == month }
            return MonthlyReview(
                month = month,
                incomeYen = cash.incomeYen,
                consumptionYen = cash.consumptionYen,
                investmentYen = cash.investmentYen,
                netWorthChangeYen = netWorthSeries.takeIf { it.isNotEmpty() }
                    ?.let { Summary.metricChange(NetWorth.KEY, it, Summary.month(month)).changeYen },
                high = spending.rows.filter { it.high },
                budgets = budgets.map { (name, budget) ->
                    BudgetResult(name, spending.rows.firstOrNull { it.category == name }?.yen ?: 0L, budget)
                }.sortedByDescending { it.ratio },
                increased = fixed?.increased.orEmpty(),
                stopped = fixed?.items.orEmpty().filter { it.missingLastMonth },
                maybeIncomplete = transactions.none { YearMonth.from(it.date) == thisMonth },
            )
        }
    }
}
