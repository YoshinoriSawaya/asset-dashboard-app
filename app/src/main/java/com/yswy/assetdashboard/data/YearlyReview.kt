package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * 1年の振り返り(E03-12)。前の月の振り返り(E03-10)の年版。中身はどれもほかの画面と同じ計算:
 * 入出金(`Summary.cashflow`)、カテゴリ別(E07-25と同じ数え方)、純資産の増減(E10-01)、固定費(E07-26)。
 *
 * 本人が選んだ: 期間は直近12か月と暦年の両方、中身はお金の流れと月ごとの棒・カテゴリ別と前の年との比べ・
 * 純資産の増減・固定費の変化。通知はしない。
 */
data class YearlyReview(
    val period: Period,
    val incomeYen: Long,
    /** 消費(生活費 + 遊び代 + カテゴリなし)。 */
    val consumptionYen: Long,
    /** 大型出費(E07-21の種類)。消費には入れないが、純資産からは出ていく。 */
    val plannedYen: Long,
    val investmentYen: Long,
    /** 月ごとの残り(収入 − 消費)。明細の無い月はnull。 */
    val monthly: List<Pair<YearMonth, Long?>>,
    /** 期間の純資産の増減(E10-01)。数える系列が無い・期首か期末の値が無ければnull。 */
    val netWorthChangeYen: Long?,
    /** カテゴリごとの合計と前の期間の合計。今の期間の多い順。 */
    val categories: List<CategoryYear>,
    /** 前の期間(12か月前)に明細のある月の数。0なら比べない。 */
    val previousMonthsWithData: Int,
    val fixed: FixedChange?,
) {
    /** 明細のある月の数。12に満たなければ、出そろっていない年。 */
    val monthsWithData: Int get() = monthly.count { it.second != null }

    /** 収入 − 消費。 */
    val surplusYen: Long get() = incomeYen - consumptionYen

    /**
     * 貯めた分: 収入 − 消費 − 大型出費。純資産に残ったはずのお金(積立投資に回した分も純資産のうち)。
     * 純資産の増減からこれを引いた残りを「評価額の増減など」とする(相場のほか、明細に無い出入り・計上の漏れも入る)。
     */
    val savedYen: Long get() = incomeYen - consumptionYen - plannedYen

    val otherChangeYen: Long? get() = netWorthChangeYen?.let { it - savedYen }

    /** 期間。[months]は古い順の12か月(今年はまだ終わっていない月を除く)。 */
    data class Period(val label: String, val months: List<YearMonth>) {
        val range: ClosedRange<LocalDate> get() = months.first().atDay(1)..months.last().atEndOfMonth()
        val previous: Period get() = Period("前の期間", months.map { it.minusMonths(12) })
    }

    data class CategoryYear(val category: String?, val kind: CategoryKind?, val yen: Long, val previousYen: Long) {
        val changeYen: Long get() = yen - previousYen
    }

    /** 固定費の変化(E07-26)。期間の前の6か月と、期間の終わりの6か月を比べる。 */
    data class FixedChange(
        /** 期間の終わりの固定費の月の目安の合計。 */
        val monthlyTotalYen: Long,
        /** 期間の前の固定費の月の目安の合計。前に3か月分の明細が無ければnull。 */
        val previousMonthlyTotalYen: Long?,
        /** 期間のあいだに増えた(前には無く、終わりにある)。 */
        val added: List<FixedCosts.Item>,
        /** 期間のあいだに止まった(前にはあり、終わりに無い)。 */
        val stopped: List<FixedCosts.Item>,
        /** 金額が変わった(前, 終わり)。月の目安が5%より動いたもの。 */
        val changed: List<Pair<FixedCosts.Item, FixedCosts.Item>>,
    )

    companion object {
        /**
         * 選べる期間。最初が直近12か月(先月まで)、続けて明細のある暦年の新しい順。今年は先月までの途中の年として出す。
         * 今月は明細が出そろっていないので、どの期間にも入れない。
         */
        fun periods(transactions: List<BankTransactionEntity>, today: LocalDate): List<Period> {
            val lastMonth = YearMonth.from(today).minusMonths(1)
            val recent = Period("直近12か月", (11 downTo 0).map { lastMonth.minusMonths(it.toLong()) })
            val years = transactions.map { it.date.year }.distinct().sortedDescending()
                .mapNotNull { y ->
                    val months = (1..12).map { YearMonth.of(y, it) }.filter { it <= lastMonth }
                    if (months.isEmpty()) return@mapNotNull null
                    Period(if (months.size < 12) "${y}年(${months.last().monthValue}月まで)" else "${y}年", months)
                }
            return listOf(recent) + years
        }

        fun of(
            transactions: List<BankTransactionEntity>,
            period: Period,
            netWorthSeries: List<MetricPointEntity> = emptyList(),
        ): YearlyReview {
            val cash = Summary.cashflow(transactions, period.range)
            val withData = transactions.map { YearMonth.from(it.date) }.toSet()
            val monthly = period.months.map { m ->
                m to if (m in withData) Summary.cashflow(transactions, Summary.month(m)).let { it.incomeYen - it.consumptionYen } else null
            }
            val previous = period.previous
            return YearlyReview(
                period = period,
                incomeYen = cash.incomeYen,
                consumptionYen = cash.consumptionYen,
                plannedYen = transactions.filter { it.date in period.range && it.categoryKind == CategoryKind.PLANNED }
                    .sumOf { Summary.usedYen(it) },
                investmentYen = cash.investmentYen,
                monthly = monthly,
                netWorthChangeYen = netWorthSeries.takeIf { it.isNotEmpty() }
                    ?.let { Summary.metricChange(NetWorth.KEY, it, period.range).changeYen },
                categories = categories(transactions, period.range, previous.range),
                previousMonthsWithData = previous.months.count { it in withData },
                fixed = fixedChange(transactions, period),
            )
        }

        /** カテゴリ別の合計(数え方はE07-25と同じ: 振替を除き、使った額が0の行は数えない)。 */
        private fun categories(
            transactions: List<BankTransactionEntity>,
            range: ClosedRange<LocalDate>,
            previousRange: ClosedRange<LocalDate>,
        ): List<CategoryYear> {
            val counted = transactions.filter { it.categoryKind != CategoryKind.TRANSFER && Summary.usedYen(it) != 0L }
            fun sums(r: ClosedRange<LocalDate>) = counted.filter { it.date in r }
                .groupBy { it.category to it.categoryKind }
                .mapValues { (_, rows) -> rows.sumOf { Summary.usedYen(it) } }
            val now = sums(range)
            val before = sums(previousRange)
            return (now.keys + before.keys).distinct()
                .map { key -> CategoryYear(key.first, key.second, now[key] ?: 0L, before[key] ?: 0L) }
                .filter { it.yen != 0L || it.previousYen != 0L }
                .sortedWith(compareByDescending<CategoryYear> { it.yen }.thenByDescending { it.previousYen })
        }

        /** 期間の前の6か月の固定費と、期間の終わりの6か月の固定費を、摘要で突き合わせる。終わりが決められなければnull。 */
        private fun fixedChange(transactions: List<BankTransactionEntity>, period: Period): FixedChange? {
            val end = FixedCosts.of(transactions, period.months.last().plusMonths(1).atDay(1)) ?: return null
            val start = FixedCosts.of(transactions, period.months.first().atDay(1))
            val before = start?.items.orEmpty().associateBy { it.description }
            val after = end.items.associateBy { it.description }
            return FixedChange(
                monthlyTotalYen = end.monthlyTotalYen,
                previousMonthlyTotalYen = start?.monthlyTotalYen,
                added = if (start == null) emptyList() else end.items.filter { it.description !in before },
                stopped = start?.items.orEmpty().filter { it.description !in after },
                changed = end.items.mapNotNull { a ->
                    val b = before[a.description] ?: return@mapNotNull null
                    if (b.monthlyYen == 0L) return@mapNotNull null
                    val ratio = kotlin.math.abs(a.monthlyYen - b.monthlyYen).toDouble() / b.monthlyYen
                    if (ratio > FixedCosts.FIXED_TOLERANCE) b to a else null
                },
            )
        }
    }
}
