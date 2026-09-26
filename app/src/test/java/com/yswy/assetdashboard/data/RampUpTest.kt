package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RampUpTest {

    private val due = LocalDate.of(2027, 9, 1)
    private val car = Item.Goal("c", "車", 1_200_000, "預金・現金", dueDate = due, rampUpMonths = 12)

    @Test
    fun `始める月の前は何もしなくてよい`() {
        assertEquals(RampUp.Waiting(YearMonth.of(2026, 9)), RampUp.of(car, 1_200_000, 0, LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun `始める月に入ったら、期日までの月数で割って切り上げる`() {
        // 2026-09 → 2027-09 で12か月。残り1,000,000を12で割って切り上げ
        assertEquals(RampUp.Active(12, 83_334), RampUp.of(car, 1_200_000, 200_000, LocalDate.of(2026, 9, 1)))
        // 「期日に間に合わせるには」(E09-01)と同じ数え方
        assertEquals(GoalForecast.monthlyNeededForDue(1_200_000, 200_000, due, LocalDate.of(2026, 9, 1)), 83_334L)
    }

    @Test
    fun `期日の月に入ったら残りを今月で`() {
        assertEquals(RampUp.Active(1, 300_000), RampUp.of(car, 1_200_000, 900_000, LocalDate.of(2027, 9, 1)))
    }

    @Test
    fun `届いていれば達成、期日を過ぎて届いていなければ過ぎた`() {
        assertEquals(RampUp.Achieved, RampUp.of(car, 1_200_000, 1_200_000, LocalDate.of(2026, 1, 1)))
        assertEquals(RampUp.Overdue, RampUp.of(car, 1_200_000, 100, LocalDate.of(2027, 9, 2)))
    }

    @Test
    fun `積み増しを決めていない・期日が無い・値が分からなければ出さない`() {
        val day = LocalDate.of(2026, 10, 1)
        assertNull(RampUp.of(car.copy(rampUpMonths = null), 1_200_000, 0, day))
        assertNull(RampUp.of(car.copy(dueDate = null), 1_200_000, 0, day))
        assertNull(RampUp.of(car, null, 0, day))
        assertNull(RampUp.of(car, 1_200_000, null, day))
    }
}
