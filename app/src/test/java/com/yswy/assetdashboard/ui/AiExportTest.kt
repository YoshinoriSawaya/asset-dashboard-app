package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.PeriodUnit
import com.yswy.assetdashboard.data.SummaryBoard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AiExportTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val total = Item.Metric(Item.metricId("合計"), "合計", "合計")

    private fun point(date: String, yen: Long) = MetricPointEntity("合計", LocalDate.parse(date), yen, MetricOrigin.CSV)

    private fun tx(date: String, description: String, withdrawal: Long? = null, deposit: Long? = null) =
        BankTransactionEntity("$date|$description", LocalDate.parse(date), description,
            withdrawal, deposit, null, "メモ欄の中身", null, "f")

    private fun export(
        points: List<MetricPointEntity> = listOf(point("2026-08-31", 1_000_000), point("2026-09-20", 1_050_000)),
        transactions: List<BankTransactionEntity> = emptyList(),
        goals: List<ItemOverview.Goal> = emptyList(),
    ) = AiExport.build(
        today = today,
        latestDataDate = LocalDate.of(2026, 9, 20),
        months = SummaryBoard.build(PeriodUnit.MONTH, listOf(total), mapOf("合計" to points), transactions, today),
        goals = goals,
    )

    @Test
    fun `系列の月ごとの値と増減を表で出す`() {
        val text = export()
        assertTrue(text.contains("# 資産の状況(2026-09-25時点)"))
        assertTrue(text.contains("手元のデータは 2026-09-20 までのものです。"))
        assertTrue(text.contains("### 合計"))
        assertTrue(text.contains("| 2026年9月 | 1,050,000円 | +50,000円 |"))
        assertTrue(text.contains("| 2026年8月 | 1,000,000円 | - |"))
    }

    @Test
    fun `直近6か月だけ`() {
        val points = (1..12).map { point("2025-%02d-28".format(it), it * 1000L) } + point("2026-09-20", 99_000)
        val text = export(points = points)
        assertTrue(text.contains("2026年4月"))
        assertFalse(text.contains("2026年3月"))
    }

    @Test
    fun `明細の摘要とメモは入れず、月の合計だけ`() {
        val text = export(
            transactions = listOf(
                tx("2026-09-01", "振込 ﾀﾅｶ ﾀﾛｳ", deposit = 300_000),
                tx("2026-09-10", "家賃", withdrawal = 80_000),
            ),
        )
        assertFalse(text.contains("ﾀﾅｶ"))
        assertFalse(text.contains("家賃"))
        assertFalse(text.contains("メモ欄の中身"))
        assertTrue(text.contains("| 2026年9月 | 300,000円 | 80,000円 | +220,000円 | 2 | 2026-09-01〜2026-09-10 |"))
    }

    @Test
    fun `目標は進捗と残り`() {
        val goal = ItemOverview.Goal(Item.Goal("g", "車購入", 1_000_000, "合計", LocalDate.of(2030, 4, 1)), 250_000)
        val text = export(goals = listOf(goal))
        assertTrue(text.contains("- 車購入: 目標 1,000,000円 / 現在 250,000円(25%) / 残り 750,000円 / 期日 2030-04-01"))
    }

    @Test
    fun `系列を分け合う目標は、割当額と何番目かを出す`() {
        val goals = com.yswy.assetdashboard.data.Allocation.apply(
            listOf(
                ItemOverview.Goal(Item.Goal("f", "防衛", 600_000, "合計"), 1_000_000),
                ItemOverview.Goal(Item.Goal("c", "車", 300_000, "合計"), 1_000_000),
            ),
        ).filterIsInstance<ItemOverview.Goal>()
        val text = export(goals = goals)
        assertTrue(text.contains("- 車: 目標 300,000円 / 現在 300,000円(100%) / 残り 0円 / 合計(1,000,000円)を2つの目標で上から順に分けた2番目。自由に使えるお金 100,000円"))
    }

    @Test
    fun `読むときの注意を付ける`() {
        val text = export()
        assertTrue(text.contains("二重に数える"))
        assertTrue(text.contains("振替"))
    }
}
