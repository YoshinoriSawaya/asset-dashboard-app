package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.data.SurplusPlan.Tier
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SurplusPlanTest {

    private val today = LocalDate.of(2026, 9, 25)

    /** 生活防衛資金: 生活費月20万 × 6か月 = 120万、下限3か月 = 60万。 */
    private fun fund(current: Long) = ItemOverview.Goal(
        Item.Goal("f", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6, 3)),
        current,
        auto = AutoTargets.Result(targetYen = 1_200_000, monthsUsed = 6, monthlyAverageYen = 200_000),
    )

    /** 期日まで半年で、12か月前から積み増す目標(=積み増し中)。 */
    private fun car(current: Long) = ItemOverview.Goal(
        Item.Goal("c", "車購入", 1_000_000, "車", dueDate = LocalDate.of(2027, 3, 31), rampUpMonths = 12),
        current,
    )

    private fun pc(current: Long) = ItemOverview.Goal(Item.Goal("p", "PC買い替え", 300_000, "PC"), current)

    private fun SurplusPlan.summary() = lines.map { Triple(it.tier, it.goal.id, it.amountYen) }

    @Test
    fun `下限の回復、積み増しの今月分、不足分の順に回し、余りを残す`() {
        val goals = listOf(fund(500_000), car(400_000), pc(250_000))
        val monthly = (RampUp.of(goals[1].item, 1_000_000, 400_000, today) as RampUp.Active).monthlyYen

        val plan = SurplusPlan.of(2_000_000, goals, today)

        assertEquals(
            listOf(
                Triple(Tier.FLOOR, "f", 100_000L),
                Triple(Tier.RAMP_UP, "c", monthly),
                Triple(Tier.SHORTFALL, "f", 600_000L),
                Triple(Tier.SHORTFALL, "c", 600_000L - monthly),
                Triple(Tier.SHORTFALL, "p", 50_000L),
            ),
            plan.summary(),
        )
        assertEquals(700_000L, plan.totalFor("f"))
        assertEquals(600_000L, plan.totalFor("c"))
        assertEquals(2_000_000L - 700_000 - 600_000 - 50_000, plan.freeYen)
    }

    @Test
    fun `足りなければ上の段から使い切る`() {
        val plan = SurplusPlan.of(150_000, listOf(fund(500_000), car(400_000), pc(250_000)), today)
        assertEquals(Triple(Tier.FLOOR, "f", 100_000L), plan.summary().first())
        assertEquals(150_000L, plan.lines.sumOf { it.amountYen })
        assertEquals(0L, plan.freeYen)
    }

    @Test
    fun `同じ段の中は一覧の順`() {
        val plan = SurplusPlan.of(60_000, listOf(pc(250_000), ItemOverview.Goal(Item.Goal("x", "旅行", 100_000, "旅行"), 0)), today)
        assertEquals(listOf(Triple(Tier.SHORTFALL, "p", 50_000L), Triple(Tier.SHORTFALL, "x", 10_000L)), plan.summary())
    }

    @Test
    fun `期日を過ぎて届いていない目標は、不足の全額を積み増しの段で`() {
        val overdue = ItemOverview.Goal(
            Item.Goal("o", "車検", 200_000, "車検", dueDate = LocalDate.of(2026, 8, 31), rampUpMonths = 6),
            50_000,
        )
        val plan = SurplusPlan.of(500_000, listOf(pc(250_000), overdue), today)
        assertEquals(listOf(Triple(Tier.RAMP_UP, "o", 150_000L), Triple(Tier.SHORTFALL, "p", 50_000L)), plan.summary())
    }

    @Test
    fun `届いている目標と毎年の枠には回さず、分からない目標は知らせる`() {
        val done = pc(300_000)
        val yearly = ItemOverview.Goal(Item.Goal("y", "ふるさと納税", 60_000, "寄付", resetsYearly = true), 0)
        val unknownTarget = ItemOverview.Goal(Item.Goal("u", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget()), 100_000)
        val noSeries = ItemOverview.Goal(Item.Goal("n", "未設定", 100_000), null)

        val plan = SurplusPlan.of(100_000, listOf(done, yearly, unknownTarget, noSeries), today)

        assertEquals(emptyList<SurplusPlan.Line>(), plan.lines)
        assertEquals(100_000L, plan.freeYen)
        assertEquals(listOf("u", "n"), plan.unknown.map { it.id })
    }

    @Test
    fun `下限の上にいれば下限の段は出ない`() {
        val plan = SurplusPlan.of(100_000, listOf(fund(700_000)), today)
        assertEquals(listOf(Triple(Tier.SHORTFALL, "f", 100_000L)), plan.summary())
    }
}
