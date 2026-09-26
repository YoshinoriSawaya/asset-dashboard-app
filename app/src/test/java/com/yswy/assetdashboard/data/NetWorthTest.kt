package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NetWorthTest {

    private val today = LocalDate.of(2026, 9, 25)
    private fun metric(key: String, counted: Boolean = true, hidden: Boolean = false) =
        Item.Metric(Item.metricId(key), key, key, hidden = hidden, inNetWorth = counted)
    private fun p(key: String, date: String, yen: Long) = MetricPointEntity(key, LocalDate.parse(date), yen, MetricOrigin.CSV)

    @Test
    fun `数える系列が無ければnull`() {
        assertNull(NetWorth.of(listOf(metric("合計", counted = false)), emptyMap(), today))
    }

    @Test
    fun `選んだ系列だけを日付ごとに足す`() {
        val points = mapOf(
            "預金・現金" to listOf(p("預金・現金", "2026-08-01", 100), p("預金・現金", "2026-09-01", 150)),
            "投資信託" to listOf(p("投資信託", "2026-08-01", 50), p("投資信託", "2026-09-01", 70)),
            "合計" to listOf(p("合計", "2026-08-01", 150), p("合計", "2026-09-01", 220)),
        )
        val nw = NetWorth.of(listOf(metric("合計", counted = false), metric("預金・現金"), metric("投資信託")), points, today)!!
        assertEquals(listOf(150L, 220L), nw.series.map { it.valueYen })
        assertEquals(listOf("預金・現金", "投資信託"), nw.metrics.map { it.metricKey })
        assertEquals(220L, nw.latest?.valueYen)
    }

    @Test
    fun `その日に点が無い系列は、それまでの最新値を使う`() {
        val points = mapOf(
            "預金・現金" to listOf(p("預金・現金", "2026-09-01", 100), p("預金・現金", "2026-09-02", 110), p("預金・現金", "2026-09-03", 120)),
            "NISA" to listOf(p("NISA", "2026-09-01", 1000)),
        )
        val nw = NetWorth.of(listOf(metric("預金・現金"), metric("NISA")), points, today)!!
        assertEquals(listOf(1100L, 1110L, 1120L), nw.series.map { it.valueYen })
        assertEquals(emptyList<Pair<Item.Metric, LocalDate>>(), nw.lateStarts)
    }

    @Test
    fun `途中から始まった系列は、それより前は足さず、始まった日を知らせる`() {
        val nisa = metric("NISA")
        val points = mapOf(
            "預金・現金" to listOf(p("預金・現金", "2026-07-01", 100), p("預金・現金", "2026-09-01", 120)),
            "NISA" to listOf(p("NISA", "2026-09-10", 1000)),
        )
        val nw = NetWorth.of(listOf(metric("預金・現金"), nisa), points, today)!!
        assertEquals(listOf(100L, 120L, 1120L), nw.series.map { it.valueYen })
        assertEquals(listOf(nisa to LocalDate.of(2026, 9, 10)), nw.lateStarts)
    }

    @Test
    fun `隠している系列も数える`() {
        val points = mapOf("合計" to listOf(p("合計", "2026-09-01", 500)))
        assertEquals(500L, NetWorth.of(listOf(metric("合計", hidden = true)), points, today)!!.latest?.valueYen)
    }

    @Test
    fun `今月の増減と月ごとの推移`() {
        val points = mapOf(
            "預金・現金" to listOf(p("預金・現金", "2026-08-20", 100), p("預金・現金", "2026-09-20", 130)),
            "投資信託" to listOf(p("投資信託", "2026-08-20", 50), p("投資信託", "2026-09-20", 40)),
        )
        val nw = NetWorth.of(listOf(metric("預金・現金"), metric("投資信託")), points, today)!!
        assertEquals(20L, nw.monthChangeYen)
        assertEquals(listOf(170L, 150L), nw.monthly.map { it.closingYen })
    }

    @Test
    fun `点がまだ無い系列だけなら、データなし`() {
        val nw = NetWorth.of(listOf(metric("手入力")), emptyMap(), today)!!
        assertNull(nw.latest)
        assertNull(nw.monthChangeYen)
    }
}
