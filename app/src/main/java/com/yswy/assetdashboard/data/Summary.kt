package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.CardStatementAdapter
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/**
 * ある期間のMetricの増減。
 *
 * Metricは時点の残高なので**足し合わせない**。期間の終わりの値と、
 * 期間が始まる前の値の差を見る。
 */
data class MetricChange(
    val metricKey: String,
    val period: ClosedRange<LocalDate>,
    /** 期間が始まる前の最後の値。それより前にデータが無ければnull。 */
    val openingYen: Long?,
    /** 期間中の最後の値。期間中にデータが無ければnull(不明であって0ではない)。 */
    val closingYen: Long?,
) {
    val changeYen: Long?
        get() = if (openingYen != null && closingYen != null) closingYen - openingYen else null
}

/** ある期間の入出金の合計。明細は「動いた額」なので足し合わせてよい。 */
data class Cashflow(
    val period: ClosedRange<LocalDate>,
    val incomeYen: Long,
    val spendingYen: Long,
    val count: Int,
    /** 支出のうち、振替などを除いた生活費(E07-06)。除く決まりが無ければ支出と同じ。 */
    val livingSpendingYen: Long = spendingYen,
    /** 消費(E07-21)。生活費と遊び代。大型出費・振替・積立投資は入れない */
    val consumptionYen: Long = livingSpendingYen,
    /** 積立投資に回した額(E07-21)。カテゴリの種類が積立投資の出金 */
    val investmentYen: Long = 0,
) {
    val netYen: Long get() = incomeYen - spendingYen
}

/**
 * 月次・年次の集計(E02-05)。DBを読まない純粋関数だけを置く。
 * 入力は `MetricPointDao.series` / `BankTransactionDao.between` の結果。
 *
 * ## 期首の値は「期間が始まる前の最後の値」
 * 「前月末の値」とすると、前月にデータが無い月は計算できなくなる。
 * 直前の観測値を使えば、データの抜けた月があっても増減が出る。
 * 期間中にデータが無ければ、期末の値は不明(null)として0とは区別する。
 */
object Summary {

    fun month(month: YearMonth): ClosedRange<LocalDate> = month.atDay(1)..month.atEndOfMonth()

    fun year(year: Year): ClosedRange<LocalDate> = year.atDay(1)..year.atMonth(12).atEndOfMonth()

    /** [points]は1つの系列の点。順序は問わない。 */
    fun metricChange(
        metricKey: String,
        points: List<MetricPointEntity>,
        period: ClosedRange<LocalDate>,
    ): MetricChange {
        val ofKey = points.filter { it.metricKey == metricKey }
        return MetricChange(
            metricKey = metricKey,
            period = period,
            openingYen = ofKey.filter { it.date < period.start }.maxByOrNull { it.date }?.valueYen,
            closingYen = ofKey.filter { it.date in period }.maxByOrNull { it.date }?.valueYen,
        )
    }

    /**
     * 入金・出金は銀行の口座の出入り。カードの利用明細(E01-14)は口座を出入りしないので入れない
     * (カードの分は、銀行のカード引き落としとして出金に入っている)。
     * 自分の口座どうしの振替(E07-21)は、入金にも出金にも入れない。
     * 生活費・消費・積立投資は、カードの明細を使った日に数え、代わりに銀行の引き落としの行を除く。
     */
    fun cashflow(transactions: List<BankTransactionEntity>, period: ClosedRange<LocalDate>): Cashflow {
        val inPeriod = transactions.filter { it.date in period }
        val bank = inPeriod.filterNot { it.label == CardStatementAdapter.LABEL || it.categoryKind == CategoryKind.TRANSFER }
        // 使った額。カードの返品(入金として持つ)は引く。カードの引き落としの行は内訳がカードの明細にあるので数えない
        fun used(rows: List<BankTransactionEntity>) = rows.filterNot { it.cardPayment }.sumOf {
            if (it.label == CardStatementAdapter.LABEL) (it.withdrawal ?: 0L) - (it.deposit ?: 0L) else it.withdrawal ?: 0L
        }
        return Cashflow(
            period = period,
            incomeYen = bank.sumOf { it.deposit ?: 0L },
            spendingYen = bank.sumOf { it.withdrawal ?: 0L },
            count = inPeriod.size,
            livingSpendingYen = used(inPeriod.filterNot { it.excludedFromSpending }),
            consumptionYen = used(inPeriod.filter { it.categoryKind?.isConsumption ?: true }),
            investmentYen = used(inPeriod.filter { it.categoryKind == CategoryKind.INVESTMENT }),
        )
    }

    /** データのある最初の月から最後の月まで、抜けた月も含めて並べる。 */
    fun monthlyMetric(metricKey: String, points: List<MetricPointEntity>): List<MetricChange> =
        monthsCovering(points.filter { it.metricKey == metricKey }.map { it.date })
            .map { metricChange(metricKey, points, month(it)) }

    fun yearlyMetric(metricKey: String, points: List<MetricPointEntity>): List<MetricChange> =
        yearsCovering(points.filter { it.metricKey == metricKey }.map { it.date })
            .map { metricChange(metricKey, points, year(it)) }

    fun monthlyCashflow(transactions: List<BankTransactionEntity>): List<Cashflow> =
        monthsCovering(transactions.map { it.date }).map { cashflow(transactions, month(it)) }

    fun yearlyCashflow(transactions: List<BankTransactionEntity>): List<Cashflow> =
        yearsCovering(transactions.map { it.date }).map { cashflow(transactions, year(it)) }

    private fun monthsCovering(dates: List<LocalDate>): List<YearMonth> {
        val first = dates.minOrNull()?.let(YearMonth::from) ?: return emptyList()
        val last = YearMonth.from(dates.max())
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
    }

    private fun yearsCovering(dates: List<LocalDate>): List<Year> {
        val first = dates.minOrNull()?.let(Year::from) ?: return emptyList()
        val last = Year.from(dates.max())
        return generateSequence(first) { it.plusYears(1) }.takeWhile { it <= last }.toList()
    }
}
