package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ItemDetailTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun point(date: String, yen: Long, origin: MetricOrigin = MetricOrigin.CSV) =
        MetricPointEntity("合計", LocalDate.parse(date), yen, origin)

    @Test
    fun `Metricの月次は新しい月が先頭で、補正した点を数える`() {
        val series = listOf(
            point("2026-07-31", 100),
            point("2026-08-31", 130, MetricOrigin.OVERRIDE),
            point("2026-09-20", 120),
            point("2026-09-21", 125, MetricOrigin.MANUAL),
        )
        val overview = ItemOverview.of(Item.Metric("m", "合計", "合計"), mapOf("合計" to series), today)

        val detail = ItemDetail.of(overview, series) as ItemDetail.Metric
        assertEquals(listOf(9, 8, 7), detail.monthly.map { it.period.start.monthValue })
        assertEquals(listOf(-5L, 30L, null), detail.monthly.map { it.changeYen })
        assertEquals(4, detail.pointCount)
        assertEquals(2, detail.correctedCount)
    }

    @Test
    fun `Goalの残りは目標との差で、達成したら0`() {
        fun goal(current: Long?) = ItemDetail.of(
            ItemOverview.Goal(Item.Goal("g", "車", 1_000_000, "預金・現金"), current),
            emptyList(),
        ) as ItemDetail.Goal

        assertEquals(600_000L, goal(400_000).remainingYen)
        assertEquals(0L, goal(1_200_000).remainingYen)
        assertNull(goal(null).remainingYen)
    }
}
