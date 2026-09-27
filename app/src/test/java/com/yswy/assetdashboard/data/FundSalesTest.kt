package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.notify.NavAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 保有商品一覧から売却を察する(E01-19)。値はすべて作り物。 */
class FundSalesTest {

    private val d1 = LocalDate.of(2026, 6, 1)
    private val d2 = LocalDate.of(2026, 7, 1)
    private val d3 = LocalDate.of(2026, 8, 1)

    /** 1回の取り込みの1本ぶんの点。口数の目安は 評価額 ÷ 現在値。 */
    private fun snap(name: String, date: LocalDate, value: Long, price: Long, unitCost: Long = 10_000) = listOf(
        MetricPointEntity(FundHoldings.key("NISA", name, FundHoldings.VALUE), date, value, MetricOrigin.CSV),
        MetricPointEntity(FundHoldings.key("NISA", name, FundHoldings.PRICE), date, price, MetricOrigin.CSV),
        MetricPointEntity(FundHoldings.key("NISA", name, FundHoldings.UNIT_COST), date, unitCost, MetricOrigin.CSV),
    )

    @Test
    fun `口数が減った取り込みを一部売却とし、減った割合とその日の現在値を持つ`() {
        // 口数 100 → 60(4割売った)。値上がりしていても口数で見る
        val h = FundHoldings.of(snap("A", d1, 1_000_000, 10_000) + snap("A", d2, 720_000, 12_000))
        val fund = h.held.single()
        val sale = fund.sales.single()
        assertEquals(d1, sale.after)
        assertEquals(d2, sale.by)
        assertEquals(0.4, sale.share, 1e-9)
        assertEquals(12_000L, sale.price)
    }

    @Test
    fun `積立で口数が増えたり、円の丸めで少し揺れただけなら売却にしない`() {
        val h = FundHoldings.of(
            snap("A", d1, 1_000_000, 10_000) +
                snap("A", d2, 1_210_000, 11_000) + // 口数 100 → 110
                snap("A", d3, 1_209_990, 11_000), // 円の丸めくらいの揺れ
        )
        assertEquals(emptyList<FundHoldings.Sale>(), h.held.single().sales)
    }

    @Test
    fun `ほかのファンドが載っている取り込みにいないファンドは売り切り`() {
        val h = FundHoldings.of(snap("A", d1, 1_000_000, 10_000) + snap("B", d1, 500_000, 10_000) + snap("B", d2, 500_000, 10_000))
        assertEquals(listOf("B"), h.held.map { it.name })
        val sold = h.soldOut.single()
        assertEquals("A", sold.name)
        assertEquals(d2, sold.soldOutBy)
        val sale = sold.sales.single()
        assertEquals(1.0, sale.share, 1e-9)
        assertEquals(d1, sale.after)
        assertEquals(d2, sale.by)
        assertNull(sale.price)
    }

    @Test
    fun `一部売ってから売り切ったら、両方を新しい順に持つ`() {
        val h = FundHoldings.of(
            snap("A", d1, 1_000_000, 10_000) + snap("A", d2, 500_000, 10_000) +
                snap("B", d1, 1, 1) + snap("B", d2, 1, 1) + snap("B", d3, 1, 1),
        )
        val sales = h.soldOut.single().sales
        assertEquals(listOf(d3, d2), sales.map { it.by })
    }

    @Test
    fun `取り込みが1回だけなら売却は無い`() {
        val h = FundHoldings.of(snap("A", d1, 1_000_000, 10_000))
        assertTrue(h.soldOut.isEmpty())
        assertEquals(emptyList<FundHoldings.Sale>(), h.held.single().sales)
    }

    @Test
    fun `売り切ったファンドは、基準価額が上がっても知らせない`() {
        val h = FundHoldings.of(snap("A", d1, 1_000_000, 10_000) + snap("B", d2, 500_000, 10_000))
        val navs = mapOf(
            FundSource.keyOf("NISA", "A") to Nav(d3, 20_000),
            FundSource.keyOf("NISA", "B") to Nav(d3, 20_000),
        )
        val notices = NavAlert.evaluate(h, navs, emptyMap<String, LocalDate>()).notices
        assertEquals(1, notices.size)
        assertTrue(notices.single().key.contains("|B:"))
    }

    @Test
    fun `売った日の基準価額は、その日が無ければ前のいちばん近い日`() {
        val navs = listOf(Nav(LocalDate.of(2026, 6, 26), 100), Nav(LocalDate.of(2026, 6, 29), 110))
        assertEquals(100L, NavHistory.priceOn(navs, LocalDate.of(2026, 6, 28))?.yen)
        assertEquals(110L, NavHistory.priceOn(navs, LocalDate.of(2026, 7, 1))?.yen)
        assertNull(NavHistory.priceOn(navs, LocalDate.of(2026, 6, 1)))
    }
}
