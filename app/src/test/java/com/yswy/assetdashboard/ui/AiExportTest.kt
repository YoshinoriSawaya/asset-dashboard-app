package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.CategorySpending
import com.yswy.assetdashboard.data.FixedCosts
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.PeriodUnit
import com.yswy.assetdashboard.data.SummaryBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

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
    fun `カテゴリ別の支出と毎月の支払いを出し、振込の相手とほかの摘要とメモは出さない`() {
        fun card(date: String, desc: String, yen: Long, category: String? = null, kind: CategoryKind? = null) =
            BankTransactionEntity("$date|$desc|$yen", LocalDate.parse(date), desc, yen, null, null, "メモ欄の中身",
                CardStatementAdapter.LABEL, "f", category = category, categoryKind = kind)
        val txs = buildList {
            listOf("04", "05", "06", "07", "08").forEach { m ->
                add(card("2026-$m-15", "動画サブスク", 990, "遊び代", CategoryKind.DISCRETIONARY))
                add(tx("2026-$m-27", "振込 ﾀﾅｶ ﾀﾛｳ", withdrawal = 20_000))
                repeat(3) { add(card("2026-$m-0${it + 1}", "コンビニB", 500, "外食", CategoryKind.DISCRETIONARY)) }
            }
        }
        val text = AiExport.build(
            today = today,
            latestDataDate = LocalDate.of(2026, 9, 20),
            months = emptyList(),
            goals = emptyList(),
            spending = CategorySpending.of(txs, YearMonth.of(2026, 8)),
            fixedCosts = FixedCosts.of(txs, today),
        )
        assertTrue(text.contains("## カテゴリ別の支出(2026年8月。振替を除く)"))
        assertTrue(text.contains("| 外食 | 遊び代 | 1,500円 | 3 |"))
        assertTrue(text.contains("## 毎月の支払い"))
        assertTrue(text.contains("| 動画サブスク | 遊び代 | 990円 | 11,880円 | 定額 | 5/5 |"))
        assertTrue(text.contains("| 振込(相手は伏せる) |"))
        assertFalse(text.contains("ﾀﾅｶ"))
        // 毎月の支払いでない摘要とメモは出さない
        assertFalse(text.contains("コンビニB"))
        assertFalse(text.contains("メモ欄の中身"))
        assertTrue(text.contains("年に1回の支払い(年会費など)は入らない"))
    }

    @Test
    fun `振込を含む摘要だけを伏せる`() {
        assertEquals("振込(相手は伏せる)", AiExport.maskedName("パソコン振込 ﾀﾅｶ ﾀﾛｳ"))
        assertEquals("電気　東電料金等", AiExport.maskedName("電気　東電料金等"))
    }

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
