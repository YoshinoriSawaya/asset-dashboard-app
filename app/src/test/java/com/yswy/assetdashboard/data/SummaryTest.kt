package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

class SummaryTest {

    private fun point(key: String, date: String, yen: Long) =
        MetricPointEntity(key, LocalDate.parse(date), yen, MetricOrigin.CSV)

    private fun tx(date: String, withdrawal: Long? = null, deposit: Long? = null) =
        BankTransactionEntity("$date|$withdrawal|$deposit", LocalDate.parse(date), "x",
            withdrawal, deposit, null, null, null, "f")

    private val points = listOf(
        point("合計", "2026-07-31", 1_000_000),
        point("合計", "2026-08-15", 1_050_000),
        point("合計", "2026-08-31", 1_100_000),
        // 9月はデータが抜けている
        point("合計", "2026-10-10", 1_080_000),
        point("年金", "2026-08-31", 999),
    )

    @Test
    fun `月の増減は期末の値と、その月より前の最後の値の差`() {
        val aug = Summary.metricChange("合計", points, Summary.month(YearMonth.of(2026, 8)))
        assertEquals(1_000_000L, aug.openingYen)
        assertEquals(1_100_000L, aug.closingYen)
        assertEquals(100_000L, aug.changeYen)
    }

    @Test
    fun `データの抜けた月の次の月も、直前の観測値から増減が出る`() {
        val oct = Summary.metricChange("合計", points, Summary.month(YearMonth.of(2026, 10)))
        assertEquals(1_100_000L, oct.openingYen)
        assertEquals(-20_000L, oct.changeYen)
    }

    @Test
    fun `データの無い月は期末が不明で、増減も出さない`() {
        val sep = Summary.metricChange("合計", points, Summary.month(YearMonth.of(2026, 9)))
        assertNull(sep.closingYen)
        assertNull(sep.changeYen)
    }

    @Test
    fun `最初の月は比べる相手が無いので増減を出さない`() {
        val jul = Summary.metricChange("合計", points, Summary.month(YearMonth.of(2026, 7)))
        assertNull(jul.openingYen)
        assertEquals(1_000_000L, jul.closingYen)
        assertNull(jul.changeYen)
    }

    @Test
    fun `他の系列は混ざらない`() {
        val aug = Summary.metricChange("年金", points, Summary.month(YearMonth.of(2026, 8)))
        assertEquals(999L, aug.closingYen)
        assertNull(aug.openingYen)
    }

    @Test
    fun `月次の一覧は抜けた月も含めて並ぶ`() {
        val monthly = Summary.monthlyMetric("合計", points)
        assertEquals(listOf(7, 8, 9, 10), monthly.map { it.period.start.monthValue })
        assertEquals(listOf(null, 100_000L, null, -20_000L), monthly.map { it.changeYen })
    }

    @Test
    fun `年次は年をまたいで前年末の値と比べる`() {
        val pts = listOf(point("合計", "2025-12-31", 900), point("合計", "2026-03-31", 950), point("合計", "2026-12-01", 1_000))
        val y2026 = Summary.metricChange("合計", pts, Summary.year(Year.of(2026)))
        assertEquals(100L, y2026.changeYen)
        assertEquals(listOf(2025, 2026), Summary.yearlyMetric("合計", pts).map { it.period.start.year })
    }

    @Test
    fun `入出金は期間内の合計で、両端の日を含む`() {
        val txs = listOf(
            tx("2026-08-31", withdrawal = 999),
            tx("2026-09-01", deposit = 300_000),
            tx("2026-09-10", withdrawal = 80_000),
            tx("2026-09-30", withdrawal = 1_200),
            tx("2026-10-01", deposit = 5),
        )
        val sep = Summary.cashflow(txs, Summary.month(YearMonth.of(2026, 9)))
        assertEquals(300_000L, sep.incomeYen)
        assertEquals(81_200L, sep.spendingYen)
        assertEquals(218_800L, sep.netYen)
        assertEquals(3, sep.count)
    }

    @Test
    fun `入出金の月次と年次`() {
        val txs = listOf(tx("2025-12-20", withdrawal = 10), tx("2026-02-01", deposit = 7))
        assertEquals(listOf(10L, 0L, 0L), Summary.monthlyCashflow(txs).map { it.spendingYen })
        assertEquals(listOf(10L, 0L), Summary.yearlyCashflow(txs).map { it.spendingYen })
        assertEquals(listOf(0L, 7L), Summary.yearlyCashflow(txs).map { it.incomeYen })
    }

    @Test
    fun `データが無ければ一覧は空`() {
        assertEquals(emptyList<MetricChange>(), Summary.monthlyMetric("合計", emptyList()))
        assertEquals(emptyList<Cashflow>(), Summary.monthlyCashflow(emptyList()))
    }
}
