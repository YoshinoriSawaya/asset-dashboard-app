package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

/** 純資産の将来の見通し(E09-06)。値はすべて作り物。 */
class NetWorthOutlookTest {

    private val cash = NetWorthOutlook.Asset("預金", 1_000_000, rateBp = null, monthlyYen = null)

    @Test
    fun `預金は増やさず毎月の残りを足し、投資の系列は利回りと積立で増やす`() {
        val fund = NetWorthOutlook.Asset("投資信託", 500_000, rateBp = 0, monthlyYen = 30_000)
        val o = NetWorthOutlook.of(listOf(cash, fund), incomeYen = 300_000, consumptionYen = 200_000)!!
        // 残り = 300,000 − 200,000 − 投資への積立 30,000
        assertEquals(70_000L, o.monthlyCashYen)
        assertEquals(30_000L, o.monthlyInvestYen)
        assertEquals(1_500_000L, o.nowYen)
        assertEquals(NetWorthOutlook.Point(0, 1_500_000, 500_000), o.at(0))
        // 10年後: 投資 500,000 + 30,000×120 = 4,100,000、預金 1,000,000 + 70,000×120 = 9,400,000
        assertEquals(NetWorthOutlook.Point(10, 13_500_000, 4_100_000), o.at(10))
        assertEquals((0..30).toList(), o.points.map { it.years })
        assertEquals(listOf("投資信託" to 0), o.investments)
    }

    @Test
    fun `投資の系列の増え方は将来の評価額と同じ計算`() {
        val fund = NetWorthOutlook.Asset("投資信託", 500_000, rateBp = 300, monthlyYen = 30_000)
        val o = NetWorthOutlook.of(listOf(cash, fund), incomeYen = 300_000, consumptionYen = 200_000)!!
        val inv = FutureValue.valueAt(500_000, 300, 30_000, 60)
        assertEquals((inv / 10_000).roundToLong() * 10_000, o.at(5)!!.investedYen)
        assertEquals(((inv + 1_000_000 + 70_000.0 * 60) / 10_000).roundToLong() * 10_000, o.at(5)!!.totalYen)
    }

    @Test
    fun `残りがマイナスなら預金が減っていく`() {
        val o = NetWorthOutlook.of(listOf(cash), incomeYen = 100_000, consumptionYen = 150_000)!!
        assertEquals(-50_000L, o.monthlyCashYen)
        assertEquals(1_000_000L - 50_000L * 12, o.at(1)!!.totalYen)
        assertTrue(o.investments.isEmpty())
    }

    @Test
    fun `系列が無ければ出さない`() {
        assertNull(NetWorthOutlook.of(emptyList(), 300_000, 200_000))
    }
}
