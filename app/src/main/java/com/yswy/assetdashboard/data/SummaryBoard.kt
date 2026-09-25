package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/**
 * サマリー画面(E03-03)の1期間ぶん。系列ごとの増減と、その期間の入出金。
 *
 * 系列をまたいで足さない。資産推移CSVの `合計` はそれ自体が1系列で、
 * 他の系列と足すと二重計上になる(E02-01・E10-01)。
 */
data class PeriodSummary(
    val period: ClosedRange<LocalDate>,
    val label: String,
    /** 表示する(隠していない)Metric項目ごとの増減。項目の並び順どおり。 */
    val metrics: List<Pair<Item.Metric, MetricChange>>,
    /** その期間に明細が1件も無ければnull(0円と区別する)。 */
    val cashflow: Cashflow?,
    /**
     * 期間のうち、明細が実際にある日の範囲。明細は銀行から落とした期間しか
     * 無いので、年次の入出金が1年ぶんとは限らない。それを画面で示すため。
     */
    val cashflowCoverage: ClosedRange<LocalDate>? = null,
)

enum class PeriodUnit { MONTH, YEAR }

object SummaryBoard {

    /**
     * 最初のデータの期間から、今日を含む期間まで、新しい順に並べる。
     *
     * 今日を含む期間まで伸ばすのは、同期していないと今月が「データなし」で
     * 見えるようにするため(期限切れに気づける)。
     */
    fun build(
        unit: PeriodUnit,
        metricItems: List<Item.Metric>,
        pointsByKey: Map<String, List<MetricPointEntity>>,
        transactions: List<BankTransactionEntity>,
        today: LocalDate,
    ): List<PeriodSummary> {
        val dates = metricItems.flatMap { pointsByKey[it.metricKey].orEmpty() }.map { it.date } +
            transactions.map { it.date }
        val first = dates.minOrNull() ?: return emptyList()
        val last = maxOf(dates.max(), today)

        return periods(unit, first, last).reversed().map { (period, label) ->
            val cashflow = Summary.cashflow(transactions, period)
            val inPeriod = transactions.map { it.date }.filter { it in period }
            PeriodSummary(
                period = period,
                label = label,
                metrics = metricItems.map { item ->
                    item to Summary.metricChange(item.metricKey, pointsByKey[item.metricKey].orEmpty(), period)
                },
                cashflow = cashflow.takeIf { it.count > 0 },
                cashflowCoverage = inPeriod.minOrNull()?.let { it..inPeriod.max() },
            )
        }
    }

    private fun periods(unit: PeriodUnit, first: LocalDate, last: LocalDate): List<Pair<ClosedRange<LocalDate>, String>> =
        when (unit) {
            PeriodUnit.MONTH -> generateSequence(YearMonth.from(first)) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(last) }
                .map { Summary.month(it) to "${it.year}年${it.monthValue}月" }
                .toList()
            PeriodUnit.YEAR -> generateSequence(Year.from(first)) { it.plusYears(1) }
                .takeWhile { it <= Year.from(last) }
                .map { Summary.year(it) to "${it.value}年" }
                .toList()
        }

    /** 隠していないMetric項目と、その系列の点・全明細を読んで組み立てる。 */
    suspend fun load(db: AppDatabase, unit: PeriodUnit, today: LocalDate = LocalDate.now()): List<PeriodSummary> {
        val metricItems = db.itemDao().getAll()
            .mapNotNull { it.toItem() as? Item.Metric }
            .filterNot { it.hidden }
        val pointsByKey = metricItems.map { it.metricKey }.distinct()
            .associateWith { db.metricPointDao().series(it) }
        val transactions = db.bankTransactionDao().all()
        return build(unit, metricItems, pointsByKey, transactions, today)
    }
}
