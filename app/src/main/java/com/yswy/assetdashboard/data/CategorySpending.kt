package com.yswy.assetdashboard.data

import java.time.YearMonth

/**
 * カテゴリ別の支出(E07-25)。ある月にカテゴリごとにいくら使ったかと、直近の月の平均との比べ。
 *
 * - 額の数え方は積立投資の目安(E10-04)と同じ([Summary.usedYen])。カードの返品は引き、カードの引き落としの行は
 *   数えない(内訳はカードの明細にある)。振替は出さない(使ったお金ではない)
 * - 平均は、その月より前の、明細のある月の直近[AVERAGE_MONTHS]か月。そのカテゴリを使わなかった月は0として数える
 *   (使った月だけで平均すると、たまにしか使わないカテゴリの平均が大きく出る)
 * - 「いつもより多い」は、平均の[HIGH_RATIO]倍を超え、かつ差が[HIGH_MIN_YEN]円以上のとき。少ない額のぶれで印が付かないように
 */
data class CategorySpending(
    val month: YearMonth,
    /** 使った額の多い順。 */
    val rows: List<Row>,
    /** 平均に使った月数。前の月が無ければ0。 */
    val averageMonths: Int,
) {
    data class Row(
        /** カテゴリの名前。カテゴリの無い明細はnull。 */
        val category: String?,
        val kind: CategoryKind?,
        val yen: Long,
        val count: Int,
        /** 直近の月の平均。前の月が無ければnull。 */
        val averageYen: Long?,
    ) {
        /** 平均よりはっきり多い。 */
        val high: Boolean
            get() = averageYen != null && yen > averageYen * HIGH_RATIO && yen - averageYen >= HIGH_MIN_YEN
    }

    /** その月に使った額の合計(振替を除く全部)。 */
    val totalYen: Long get() = rows.sumOf { it.yen }

    /** 消費(生活費 + 遊び代 + カテゴリなし)。積立投資の目安で収入から引くもの。 */
    val consumptionYen: Long get() = rows.filter { it.kind?.isConsumption ?: true }.sumOf { it.yen }

    /**
     * カテゴリの推移(E07-27)。月ごと・カテゴリごとの額。積み上げ棒グラフに使う。
     * [categories]は期間の合計の多い順に上位だけ。残りは「その他」にまとめ、[values]の各月の最後に置く。
     */
    data class Trend(
        /** 古い月が先頭。 */
        val months: List<YearMonth>,
        /** 色を付けるカテゴリ。nullはカテゴリなし。 */
        val categories: List<String?>,
        /** 月ごとに、[categories]の順の額と、最後に「その他」の額。 */
        val values: List<List<Long>>,
    ) {
        /** 「その他」に何か入っているか(凡例に出すか)。 */
        val hasOthers: Boolean get() = values.any { it.last() != 0L }
    }

    companion object {
        const val AVERAGE_MONTHS = 6
        const val TREND_MONTHS = 12
        const val TREND_TOP = 6

        /**
         * 直近[months]か月(明細のある月、[last]まで)の推移。
         * 上位[top]カテゴリに色を付け、残りは「その他」。振替は入れない(額の数え方は[of]と同じ)。
         */
        fun trend(transactions: List<BankTransactionEntity>, last: YearMonth, months: Int = TREND_MONTHS, top: Int = TREND_TOP): Trend {
            val counted = transactions.filter { it.categoryKind != CategoryKind.TRANSFER && Summary.usedYen(it) != 0L }
            val window = transactions.map { YearMonth.from(it.date) }.distinct().filter { it <= last }.sorted().takeLast(months)
            val inWindow = counted.filter { YearMonth.from(it.date) in window }
            val ranked = inWindow.groupBy { it.category }
                .mapValues { (_, rows) -> rows.sumOf { Summary.usedYen(it) } }
                .entries.sortedByDescending { it.value }.map { it.key }
            val shown = ranked.take(top)
            val values = window.map { m ->
                val rows = inWindow.filter { YearMonth.from(it.date) == m }
                val byCategory = rows.groupBy { it.category }.mapValues { (_, r) -> r.sumOf { Summary.usedYen(it) } }
                shown.map { byCategory[it] ?: 0L } + byCategory.filterKeys { it !in shown }.values.sum()
            }
            return Trend(window, shown, values)
        }

        const val HIGH_RATIO = 1.3
        const val HIGH_MIN_YEN = 5_000L

        /** 明細のある月。新しい月が先頭。 */
        fun months(transactions: List<BankTransactionEntity>): List<YearMonth> =
            transactions.map { YearMonth.from(it.date) }.distinct().sortedDescending()

        /** @return その月に振替以外の出金が無ければ、行の無い結果 */
        fun of(transactions: List<BankTransactionEntity>, month: YearMonth): CategorySpending {
            val counted = transactions.filter { it.categoryKind != CategoryKind.TRANSFER && Summary.usedYen(it) != 0L }
            val byMonth = counted.groupBy { YearMonth.from(it.date) }
            // 平均に使う月: この月より前で、明細(振替を含む何か)がある月
            val previous = transactions.map { YearMonth.from(it.date) }.distinct()
                .filter { it < month }.sortedDescending().take(AVERAGE_MONTHS)

            fun key(t: BankTransactionEntity) = t.category to t.categoryKind
            val thisMonth = byMonth[month].orEmpty().groupBy(::key)
            val averages = previous.flatMap { byMonth[it].orEmpty() }.groupBy(::key)
                .mapValues { (_, rows) -> rows.sumOf { Summary.usedYen(it) } / previous.size }

            val rows = thisMonth.map { (k, rows) ->
                Row(
                    category = k.first,
                    kind = k.second,
                    yen = rows.sumOf { Summary.usedYen(it) },
                    count = rows.size,
                    averageYen = if (previous.isEmpty()) null else averages[k] ?: 0L,
                )
            }.sortedByDescending { it.yen }
            return CategorySpending(month, rows, previous.size)
        }

        /** その月・そのカテゴリの明細。新しい日が先頭。[category]がnullならカテゴリの無い明細。 */
        fun transactions(transactions: List<BankTransactionEntity>, month: YearMonth, category: String?): List<BankTransactionEntity> =
            transactions.filter {
                YearMonth.from(it.date) == month && it.category == category &&
                    it.categoryKind != CategoryKind.TRANSFER && Summary.usedYen(it) != 0L
            }.sortedByDescending { it.date }
    }
}
