package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class GoalForecastTest {

    private fun point(date: String, yen: Long) = MetricPointEntity("預金・現金", LocalDate.parse(date), yen, MetricOrigin.CSV)
    private val goal = Item.Goal("g", "車", 1_000_000, "預金・現金")

    @Test
    fun `約半年前の点から最新までの増え方で、届く月を出す`() {
        // 182日で+182,000円 → 1日1,000円。残り300,000円 → 300日後
        val points = listOf(point("2026-01-01", 0), point("2026-03-27", 518_000), point("2026-09-25", 700_000))
        val f = GoalForecast.of(goal, 1_000_000, points) as GoalForecast.Reaching
        assertEquals(YearMonth.of(2027, 7), f.month) // 2026-09-25 + 300日 = 2027-07-22
        assertEquals(30_437L, f.monthlyPaceYen)
        assertNull(f.onTime)
    }

    @Test
    fun `半年より前の点が無ければ、いちばん古い点から`() {
        val points = listOf(point("2026-06-27", 610_000), point("2026-09-25", 700_000)) // 90日で+90,000
        val f = GoalForecast.of(goal, 1_000_000, points) as GoalForecast.Reaching
        assertEquals(YearMonth.of(2027, 7), f.month)
    }

    @Test
    fun `期間が60日に満たなければペースを出さない`() {
        val points = listOf(point("2026-08-01", 600_000), point("2026-09-25", 700_000))
        assertEquals(GoalForecast.NotEnoughData, GoalForecast.of(goal, 1_000_000, points))
    }

    @Test
    fun `減っているか横ばいなら届かない`() {
        val points = listOf(point("2026-03-01", 800_000), point("2026-09-25", 700_000))
        val f = GoalForecast.of(goal, 1_000_000, points) as GoalForecast.NotReaching
        assertEquals(true, f.monthlyPaceYen < 0)
    }

    @Test
    fun `もう届いていれば達成`() {
        assertEquals(GoalForecast.Achieved, GoalForecast.of(goal, 1_000_000, listOf(point("2026-09-25", 1_200_000))))
    }

    @Test
    fun `期日に間に合うかも出す`() {
        val points = listOf(point("2026-03-27", 518_000), point("2026-09-25", 700_000))
        val early = GoalForecast.of(goal.copy(dueDate = LocalDate.of(2028, 1, 1)), 1_000_000, points) as GoalForecast.Reaching
        val late = GoalForecast.of(goal.copy(dueDate = LocalDate.of(2027, 1, 1)), 1_000_000, points) as GoalForecast.Reaching
        assertEquals(true, early.onTime)
        assertEquals(false, late.onTime)
        // 期日まで3か月、残り300,000円 → 月々100,000円
        assertEquals(100_000L, GoalForecast.monthlyNeededForDue(1_000_000, 700_000, LocalDate.of(2026, 12, 25), LocalDate.of(2026, 9, 25)))
        assertNull(GoalForecast.monthlyNeededForDue(1_000_000, 700_000, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun `毎年の枠・目標額や点が無いものは予測しない`() {
        val points = listOf(point("2026-03-01", 0), point("2026-09-25", 1))
        assertNull(GoalForecast.of(goal.copy(resetsYearly = true), 1_000_000, points))
        assertNull(GoalForecast.of(goal, null, points))
        assertNull(GoalForecast.of(goal, 1_000_000, emptyList()))
    }
}
