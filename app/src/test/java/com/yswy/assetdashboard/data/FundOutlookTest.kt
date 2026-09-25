package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.notify.NotificationRules
import com.yswy.assetdashboard.ui.AiExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FundOutlookTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val fund = Item.Goal("g", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6))

    /** 生活費の平均300,000円 × 6か月 = 目標1,800,000円 */
    private fun overview(current: Long, pace: Long?) =
        ItemOverview.Goal(fund, current, AutoTargets.Result(1_800_000, 3, 300_000), monthlyPaceYen = pace)

    @Test
    fun `生活費の何か月分あるか`() {
        assertEquals(7.0, FundOutlook.of(overview(2_100_000, null))!!.monthsCovered!!, 1e-9)
    }

    @Test
    fun `減っていれば、目標を割るまでの月数`() {
        // 目標より300,000円多く、月100,000円ずつ減る → 3か月
        assertEquals(3, FundOutlook.of(overview(2_100_000, -100_000))!!.monthsUntilBelowTarget)
    }

    @Test
    fun `増えている・もう下回っている・ペース不明なら出さない`() {
        assertNull(FundOutlook.of(overview(2_100_000, 50_000))!!.monthsUntilBelowTarget)
        assertNull(FundOutlook.of(overview(1_500_000, -100_000))!!.monthsUntilBelowTarget)
        assertNull(FundOutlook.of(overview(2_100_000, null))!!.monthsUntilBelowTarget)
    }

    @Test
    fun `決まった額の目標には出さない`() {
        assertNull(FundOutlook.of(ItemOverview.Goal(Item.Goal("c", "車", 1_000_000, "預金・現金"), 500_000)))
    }

    @Test
    fun `3か月以内に割りそうなら、下回る前に通知する`() {
        fun keys(o: ItemOverview.Goal) =
            NotificationRules.evaluate(today, SyncStatus(today, false), listOf(o), emptyMap()).map { it.key }
        assertEquals(listOf("outlook:g"), keys(overview(2_100_000, -100_000)))
        assertEquals(emptyList<String>(), keys(overview(2_700_000, -100_000))) // 9か月後
        assertEquals(listOf("shortfall:g"), keys(overview(1_500_000, -100_000))) // もう下回っている(E07-07)
    }

    @Test
    fun `AI用の書き出しに見通しを入れる`() {
        val text = AiExport.build(today, null, emptyList(), listOf(overview(2_100_000, -100_000)))
        assertTrue(text.contains("生活費の約7.0か月分 / このペースだと約3か月後に目標を割る"))
    }

    @Test
    fun `ペースは約半年前の点から最新まで`() {
        fun p(d: String, v: Long) = MetricPointEntity("x", LocalDate.parse(d), v, MetricOrigin.CSV)
        val pace = Pace.of(listOf(p("2026-01-01", 0), p("2026-03-27", 0), p("2026-09-25", 182_000)))!!
        assertEquals(1_000.0, pace.perDayYen, 1e-9)
        assertNull(Pace.of(listOf(p("2026-08-01", 0), p("2026-09-25", 1))))
    }
}
