package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AllocationTest {

    private val day = LocalDate.of(2026, 9, 1)

    private fun goal(id: String, target: Long?, current: Long?, key: String = "預金", yearly: Boolean = false) =
        ItemOverview.Goal(Item.Goal(id, id, target ?: 1, key, resetsYearly = yearly), current, monthlyPaceYen = null)
            .let { if (target == null) it.copy(item = it.item.copy(targetYen = null, autoTarget = AutoTarget())) else it }

    @Test
    fun `上から目標額まで満たし、余りは自由に使えるお金`() {
        assertEquals(listOf(1_800L, 200L) to 0L, Allocation.fill(2_000, listOf(1_800, 1_000)))
        assertEquals(listOf(1_800L, 1_000L) to 500L, Allocation.fill(3_300, listOf(1_800, 1_000)))
        // 残高がマイナスでも割り当てはマイナスにしない
        assertEquals(listOf(0L, 0L) to 0L, Allocation.fill(-100, listOf(1_800, 1_000)))
        // 目標額が分からない目標は0で、下の分を食わない
        assertEquals(listOf(0L, 1_000L) to 1_000L, Allocation.fill(2_000, listOf(null, 1_000)))
    }

    @Test
    fun `同じ系列を測る目標が2つ以上なら、今の値を割当額に置き換える`() {
        val result = Allocation.apply(listOf(goal("防衛", 1_800, 2_000), goal("車", 1_000, 2_000), goal("PC", 500, 50, key = "別")))
            .filterIsInstance<ItemOverview.Goal>()
        assertEquals(listOf(1_800L, 200L, 50L), result.map { it.currentYen })
        assertEquals(Allocation.Share("預金", 2_000, 0, 1, 2, 0), result[0].share)
        assertEquals(Allocation.Share("預金", 2_000, 1_800, 2, 2, 0), result[1].share)
        // 1つだけの系列は分けない
        assertNull(result[2].share)
        // 下の目標が使える額は、系列 − 上の目標額
        assertEquals(200L, result[1].availableYen)
        assertEquals(50L, result[2].availableYen)
    }

    @Test
    fun `毎年の枠と値の無い目標は分けない`() {
        val result = Allocation.apply(listOf(goal("枠", 100, 30, yearly = true), goal("車", 1_000, 2_000)))
            .filterIsInstance<ItemOverview.Goal>()
        assertEquals(listOf(30L, 2_000L), result.map { it.currentYen })
        assertNull(result[1].share)
    }

    @Test
    fun `生活防衛資金が満ちていても、目標を割るまでの月数は使える額で数える`() {
        val fund = ItemOverview.Goal(
            Item.Goal("f", "防衛", null, "預金", autoTarget = AutoTarget(6, 6)),
            3_000, AutoTargets.Result(1_800, 6, 300), monthlyPaceYen = -100,
        )
        val shared = Allocation.apply(listOf(fund, goal("車", 1_000, 3_000))).filterIsInstance<ItemOverview.Goal>().first()
        assertEquals(1_800L, shared.currentYen)
        // (3,000 − 1,800) / 100 = 12か月。割当額(1,800)で数えると0か月になってしまう
        assertEquals(12, FundOutlook.of(shared)!!.monthsUntilBelowTarget)
    }

    @Test
    fun `推移を今の目標額で積み上げる`() {
        val goals = Allocation.apply(listOf(goal("防衛", 1_800, 2_500), goal("車", 1_000, 2_500)))
            .filterIsInstance<ItemOverview.Goal>()
        val series = listOf(
            MetricPointEntity("預金", day.plusDays(30), 2_500, MetricOrigin.CSV),
            MetricPointEntity("預金", day, 1_000, MetricOrigin.CSV),
        )
        val stack = Allocation.stack(series, goals) { if (it == "防衛") 0 else 1 }!!
        assertEquals(listOf(day, day.plusDays(30)), stack.dates)
        assertEquals(listOf("防衛", "車", "自由に使えるお金"), stack.layers.map { it.name })
        assertEquals(listOf(listOf(1_000L, 1_800L), listOf(0L, 700L), listOf(0L, 0L)), stack.layers.map { it.values })
        assertEquals(listOf(0, 1, null), stack.layers.map { it.colorIndex })
        assertNull(Allocation.stack(series, goals.take(1)) { 0 })
    }

    @Test
    fun `分け合う目標の達成予測は、上の目標の分を足した額で見る`() {
        val goals = Allocation.apply(listOf(goal("防衛", 1_800, 2_000), goal("車", 1_000, 2_000)))
        val series = listOf(MetricPointEntity("預金", day, 2_000, MetricOrigin.CSV))
        val car = ItemDetail.of(goals[1], series, goals, day) as ItemDetail.Goal
        // 系列は2,000で、車の目標額1,000だけなら達成だが、上の1,800と合わせた2,800には届かない
        assertEquals(GoalForecast.NotEnoughData, car.forecast)
        assertEquals(1, car.colorIndex)
    }
}
