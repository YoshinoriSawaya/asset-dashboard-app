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
    fun `大型出費の予定はその月に引き、引く前の額も持つ`() {
        // 今月(0か月後)に10万、13か月後に50万、期間(30年)の外に100万
        val plans = listOf(0L to 100_000L, 13L to 500_000L, 360L to 1_000_000L)
        val o = NetWorthOutlook.of(listOf(cash), incomeYen = 300_000, consumptionYen = 200_000, plans = plans)!!
        assertEquals(NetWorthOutlook.Point(0, 1_000_000, 0, 1_000_000), o.at(0))
        // 1年後: 引く前 1,000,000 + 100,000×12 = 2,200,000。今月の10万だけ引く
        assertEquals(NetWorthOutlook.Point(1, 2_100_000, 0, 2_200_000), o.at(1))
        // 2年後: 13か月後の50万も引く
        assertEquals(NetWorthOutlook.Point(2, 3_400_000 - 600_000, 0, 3_400_000), o.at(2))
        assertEquals(600_000L, o.plannedTotalYen)
    }

    @Test
    fun `予定はリマインダーの何年ごとと期日のある目標から作る`() {
        val today = java.time.LocalDate.of(2026, 9, 27)
        val items = listOf(
            Item.Reminder("r", "車検", java.time.LocalDate.of(2027, 3, 1), Repeat.YEARLY, amountYen = 100_000, repeatYears = 2),
            Item.Goal("g", "車の購入", 2_000_000, dueDate = java.time.LocalDate.of(2030, 4, 1)),
        )
        val netWorth = NetWorth.of(
            listOf(Item.Metric(Item.metricId("預金"), "預金", "預金", inNetWorth = true)),
            mapOf("預金" to listOf(MetricPointEntity("預金", today, 1_000_000, MetricOrigin.CSV))),
            today,
        )!!
        val tx = listOf(
            BankTransactionEntity("a", java.time.LocalDate.of(2026, 8, 25), "給与", null, 300_000, null, null, null, "f"),
            BankTransactionEntity("b", java.time.LocalDate.of(2026, 8, 10), "電気代", 200_000, null, null, null, null, "f"),
        )
        val o = NetWorthOutlook.build(netWorth, tx, today, items)!!
        // 車検は2027-03から2年ごとに30年で15回、車の購入は1回
        assertEquals(100_000L * 15 + 2_000_000L, o.plannedTotalYen)
        // 5年後(60か月後)までに: 車検 2027-03・2029-03・2031-03 と車の購入
        assertEquals(o.at(5)!!.withoutPlansYen - (300_000L + 2_000_000L), o.at(5)!!.totalYen)
    }

    @Test
    fun `系列が無ければ出さない`() {
        assertNull(NetWorthOutlook.of(emptyList(), 300_000, 200_000))
    }
}
