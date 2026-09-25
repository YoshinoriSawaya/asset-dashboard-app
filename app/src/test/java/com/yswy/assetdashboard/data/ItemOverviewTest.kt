package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.ui.Formatters
import com.yswy.assetdashboard.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ItemOverviewTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun point(key: String, date: String, yen: Long) =
        MetricPointEntity(key, LocalDate.parse(date), yen, MetricOrigin.CSV)

    private val points = mapOf(
        "合計" to listOf(
            point("合計", "2026-08-31", 1_000_000),
            point("合計", "2026-09-20", 1_030_000),
        ),
        "預金・現金" to listOf(point("預金・現金", "2026-09-20", 250_000)),
    )

    @Test
    fun `Metricは最新値と今月の増減`() {
        val row = ItemOverview.of(Item.Metric(Item.metricId("合計"), "合計", "合計"), points, today)
            as ItemOverview.Metric
        assertEquals(1_030_000L, row.latest?.valueYen)
        assertEquals(30_000L, row.monthChangeYen)
    }

    @Test
    fun `今月のデータが無いMetricは増減を出さない`() {
        val row = ItemOverview.of(Item.Metric("m", "合計", "合計"), points, today.plusMonths(1))
            as ItemOverview.Metric
        assertEquals(1_030_000L, row.latest?.valueYen)
        assertNull(row.monthChangeYen)
    }

    @Test
    fun `データの無い系列のMetric`() {
        val row = ItemOverview.of(Item.Metric("m", "年金", "年金"), points, today) as ItemOverview.Metric
        assertNull(row.latest)
        assertNull(row.monthChangeYen)
    }

    @Test
    fun `Goalの進捗は紐づけた系列の最新値と目標額の比`() {
        val row = ItemOverview.of(Item.Goal("g", "車", 1_000_000, "預金・現金"), points, today) as ItemOverview.Goal
        assertEquals(0.25, row.progress!!, 1e-9)
    }

    @Test
    fun `系列を紐づけていないGoalは進捗不明`() {
        val row = ItemOverview.of(Item.Goal("g", "PC", 200_000), points, today) as ItemOverview.Goal
        assertNull(row.progress)
    }

    @Test
    fun `Reminderは期日までの日数、過ぎていれば負`() {
        val soon = ItemOverview.of(Item.Reminder("r", "点検", today.plusDays(3)), points, today) as ItemOverview.Reminder
        val past = ItemOverview.of(Item.Reminder("r", "点検", today.minusDays(2)), points, today) as ItemOverview.Reminder
        assertEquals(3L, soon.daysLeft)
        assertEquals(-2L, past.daysLeft)
    }

    @Test
    fun `金額と日数の書式`() {
        assertEquals("1,234,567円", Formatters.yen(1_234_567))
        assertEquals("+12,345円", Formatters.yenChange(12_345))
        assertEquals("-500円", Formatters.yenChange(-500))
        assertEquals("±0円", Formatters.yenChange(0))
        assertEquals("25%", Formatters.percent(0.25))
        assertEquals("120%", Formatters.percent(1.2))
        assertEquals("あと3日", Formatters.daysLeft(3))
        assertEquals("今日", Formatters.daysLeft(0))
        assertEquals("2日過ぎ", Formatters.daysLeft(-2))
    }

    @Test
    fun `ルートからidを取り出す`() {
        assertEquals("metric:預金・現金", Routes.itemId(Routes.item("metric:預金・現金")))
        assertNull(Routes.itemId(Routes.TOP))
    }
}
