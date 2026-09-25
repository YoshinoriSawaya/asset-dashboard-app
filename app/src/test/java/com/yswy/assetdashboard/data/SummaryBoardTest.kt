package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SummaryBoardTest {

    private val total = Item.Metric(Item.metricId("合計"), "合計", "合計")
    private val pension = Item.Metric(Item.metricId("年金"), "年金", "年金")

    private fun point(key: String, date: String, yen: Long) =
        MetricPointEntity(key, LocalDate.parse(date), yen, MetricOrigin.CSV)

    private fun tx(date: String, withdrawal: Long? = null, deposit: Long? = null) =
        BankTransactionEntity("$date|$withdrawal|$deposit", LocalDate.parse(date), "x",
            withdrawal, deposit, null, null, null, "f")

    private val points = mapOf(
        "合計" to listOf(point("合計", "2026-07-31", 100), point("合計", "2026-08-31", 130)),
        "年金" to listOf(point("年金", "2026-08-31", 10)),
    )

    @Test
    fun `今日の月まで新しい順に並び、今月はデータが無ければ不明`() {
        val board = SummaryBoard.build(
            PeriodUnit.MONTH, listOf(total, pension), points, emptyList(), today = LocalDate.of(2026, 9, 25),
        )

        assertEquals(listOf("2026年9月", "2026年8月", "2026年7月"), board.map { it.label })
        val sep = board.first()
        assertNull(sep.metrics.first().second.closingYen)
        assertNull(sep.cashflow)
        // 8月の合計は7月末から+30
        assertEquals(30L, board[1].metrics.first { it.first == total }.second.changeYen)
    }

    @Test
    fun `系列は項目の並び順どおりで、足し合わせない`() {
        val board = SummaryBoard.build(
            PeriodUnit.MONTH, listOf(pension, total), points, emptyList(), today = LocalDate.of(2026, 8, 31),
        )
        assertEquals(listOf("年金", "合計"), board.first().metrics.map { it.first.name })
    }

    @Test
    fun `入出金は明細のある期間だけ`() {
        val board = SummaryBoard.build(
            PeriodUnit.MONTH, listOf(total), points,
            listOf(tx("2026-08-10", withdrawal = 50), tx("2026-08-25", deposit = 200)),
            today = LocalDate.of(2026, 8, 31),
        )
        val aug = board.first()
        assertEquals(200L, aug.cashflow?.incomeYen)
        assertEquals(50L, aug.cashflow?.spendingYen)
        assertNull(board[1].cashflow) // 7月は明細なし
        assertEquals(LocalDate.of(2026, 8, 10)..LocalDate.of(2026, 8, 25), aug.cashflowCoverage)
    }

    @Test
    fun `年次`() {
        val board = SummaryBoard.build(
            PeriodUnit.YEAR, listOf(total),
            mapOf("合計" to listOf(point("合計", "2025-12-31", 100), point("合計", "2026-08-31", 130))),
            emptyList(), today = LocalDate.of(2026, 9, 25),
        )
        assertEquals(listOf("2026年", "2025年"), board.map { it.label })
        assertEquals(30L, board.first().metrics.single().second.changeYen)
    }

    @Test
    fun `データが無ければ空`() {
        assertEquals(emptyList<PeriodSummary>(),
            SummaryBoard.build(PeriodUnit.MONTH, listOf(total), emptyMap(), emptyList(), LocalDate.of(2026, 9, 25)))
    }
}
