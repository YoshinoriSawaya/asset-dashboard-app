package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 日付の入力(E07-17)。 */
class DateInputTest {

    private val march1 = LocalDate.of(2027, 3, 1)

    @Test
    fun `yyyymmddと区切りのある形を読む`() {
        assertEquals(march1, DateInput.parse("20270301"))
        assertEquals(march1, DateInput.parse(" 20270301 "))
        assertEquals(march1, DateInput.parse("2027-03-01"))
        assertEquals(march1, DateInput.parse("2027/3/1"))
        assertEquals(march1, DateInput.parse("2027.3.1"))
    }

    @Test
    fun `読めない・存在しない日付はnull`() {
        assertNull(DateInput.parse(""))
        assertNull(DateInput.parse("2027031")) // 7桁
        assertNull(DateInput.parse("20270230")) // 2月30日
        assertNull(DateInput.parse("来年の3月"))
    }

    @Test
    fun `入力欄にはyyyymmddで入れておき、そのまま読み戻せる`() {
        assertEquals("20270301", DateInput.format(march1))
        assertEquals(march1, DateInput.parse(DateInput.format(march1)))
    }

    @Test
    fun `目標・リマインダー・補正の画面で使える`() {
        val reminder = ReminderForm.parse(null, "車検", "20270301", Repeat.YEARLY) as ReminderForm.Result.Ok
        assertEquals(march1, reminder.reminder.dueDate)
        val goal = GoalForm.parse(null, "車", "1000000", null, "20300401") as GoalForm.Result.Ok
        assertEquals(LocalDate.of(2030, 4, 1), goal.goal.dueDate)
        val correction = CorrectionForm.parse("預金", "20260925", "1000", "") as CorrectionForm.Result.Ok
        assertEquals(LocalDate.of(2026, 9, 25), correction.date)
        assertTrue(ReminderForm.parse(null, "x", "2027031", Repeat.YEARLY) is ReminderForm.Result.Invalid)
    }
}
