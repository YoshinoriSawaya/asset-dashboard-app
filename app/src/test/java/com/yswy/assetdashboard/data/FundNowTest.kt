package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 今の基準価額での見直しと最高値との比べ(E05-12)。値はすべて作り物。 */
class FundNowTest {

    private fun nav(date: String, yen: Long) = Nav(LocalDate.parse(date), yen)

    private fun snap(value: Long, cost: Long?, price: Long?, date: String = "2026-09-01") =
        FundHoldings.Snapshot(LocalDate.parse(date), value, cost, 10_000, price)

    private fun fund(name: String, s: FundHoldings.Snapshot) = FundHoldings.Fund("NISA", name, listOf(s))

    @Test
    fun `取り込み時の口数のまま今の基準価額で見直す`() {
        val e = FundNow.estimate(snap(100_000, 80_000, 12_000), nav("2026-09-25", 13_200))!!
        assertEquals(110_000, e.valueYen)
        assertEquals(30_000L, e.gainYen)
        assertEquals(0.10, e.sinceImport, 1e-9)
    }

    @Test
    fun `見直せないときはnull`() {
        // 現在値が無い
        assertNull(FundNow.estimate(snap(100_000, 80_000, null), nav("2026-09-25", 13_200)))
        // 基準価額が取り込みより古い
        assertNull(FundNow.estimate(snap(100_000, 80_000, 12_000), nav("2026-08-31", 13_200)))
        // 単価の桁が合わない(1口あたりと1万口あたり)
        assertNull(FundNow.estimate(snap(100_000, 80_000, 12_000), nav("2026-09-25", 1)))
        assertNull(FundNow.estimate(snap(100_000, 80_000, 12_000), null))
    }

    @Test
    fun `取り込みと同じ日の基準価額なら見直す`() {
        assertEquals(110_000, FundNow.estimate(snap(100_000, 80_000, 12_000), nav("2026-09-01", 13_200))!!.valueYen)
    }

    @Test
    fun `取り込みより前の基準価額でも現在値と同じなら±0%で見直す`() {
        val e = FundNow.estimate(snap(100_000, 80_000, 12_000), nav("2026-08-29", 12_000))!!
        assertEquals(100_000, e.valueYen)
        assertEquals(0.0, e.sinceImport, 1e-9)
    }

    @Test
    fun `最高値は同じ値なら新しい日、今との差は0以下`() {
        val history = listOf(nav("2024-01-05", 9_000), nav("2025-03-03", 15_000), nav("2026-07-01", 15_000), nav("2026-09-25", 13_500))
        val peak = FundNow.peak(history)!!
        assertEquals(nav("2026-07-01", 15_000), peak)
        assertEquals(-0.10, FundNow.fromPeak(history.last(), peak)!!, 1e-9)
        assertNull(FundNow.peak(emptyList()))
        assertNull(FundNow.fromPeak(null, peak))
    }

    @Test
    fun `最高値のときの評価額は今の見積もりの口数に最高値を掛ける`() {
        val now = nav("2026-09-25", 13_500)
        val e = FundNow.estimate(snap(100_000, 80_000, 12_000), now)!!
        assertEquals(112_500, e.valueYen)
        assertEquals(125_000L, FundNow.valueAtPeak(e, now, nav("2026-07-01", 15_000)))
    }

    @Test
    fun `合計は見直せないファンドを取り込み時の値のまま数え、含み益は取得額の分かるものだけ`() {
        val a = fund("A", snap(100_000, 80_000, 12_000))
        val b = fund("B", snap(50_000, 60_000, 10_000))
        val c = fund("C", snap(20_000, null, 10_000))
        val navs = mapOf("NISA|A" to nav("2026-09-25", 13_200))
        val t = FundNow.total(listOf(a, b, c), navs)
        assertEquals(110_000 + 50_000 + 20_000L, t.valueYen)
        assertEquals(140_000L, t.costYen)
        assertEquals(30_000L - 10_000L, t.gainYen)
        assertEquals(1, t.refreshed)
        assertEquals(3, t.count)
    }

    @Test
    fun `最高値の合計は比べられたファンドだけ`() {
        val a = fund("A", snap(100_000, 80_000, 12_000))
        val b = fund("B", snap(50_000, 60_000, 10_000))
        val navs = mapOf("NISA|A" to nav("2026-09-25", 13_500), "NISA|B" to nav("2026-09-25", 10_000))
        val peaks = mapOf("NISA|A" to nav("2026-07-01", 15_000))
        val t = FundNow.peakTotal(listOf(a, b), navs, peaks)
        assertEquals(1, t.count)
        assertEquals(112_500L, t.nowYen)
        assertEquals(125_000L, t.atPeakYen)
        assertEquals(-12_500L, t.diffYen)
        assertEquals(-0.10, t.ratio!!, 1e-9)
    }
}
