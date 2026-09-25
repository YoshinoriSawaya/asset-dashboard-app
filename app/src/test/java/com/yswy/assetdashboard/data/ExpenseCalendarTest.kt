package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.drive.Settings
import com.yswy.assetdashboard.ui.ReminderForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseCalendarTest {

    private val today = LocalDate.of(2026, 9, 25)
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `毎年のリマインダーは3年の間の各年に、期日の目標はその日に`() {
        val items = listOf(
            Item.Reminder("r1", "車検", d("2026-11-10"), Repeat.YEARLY, amountYen = 100_000),
            Item.Goal("g1", "車の購入", 1_500_000, "預金・現金", dueDate = d("2028-04-01")),
        )
        val entries = ExpenseCalendar.build(items, today)
        assertEquals(
            listOf(d("2026-11-10"), d("2027-11-10"), d("2028-04-01"), d("2028-11-10")),
            entries.map { it.date },
        )
        assertEquals(mapOf(2026 to 100_000L, 2027 to 100_000L, 2028 to 1_600_000L), ExpenseCalendar.totalsByYear(entries))
    }

    @Test
    fun `期日が過ぎた毎年のリマインダーも、次の年から載る`() {
        val items = listOf(Item.Reminder("r", "保険", d("2025-03-01"), Repeat.YEARLY, amountYen = 60_000))
        assertEquals(listOf(d("2027-03-01"), d("2028-03-01"), d("2029-03-01")), ExpenseCalendar.build(items, today).map { it.date })
    }

    @Test
    fun `見込み額の無いもの・毎月のもの・毎年の枠・生活費から出す目標・期間外は載せない`() {
        val items = listOf(
            Item.Reminder("a", "額なし", d("2026-12-01"), Repeat.YEARLY),
            Item.Reminder("b", "毎月", d("2026-10-01"), Repeat.MONTHLY, amountYen = 10_000),
            Item.Goal("c", "ふるさと納税", 100_000, "x", dueDate = d("2026-12-31"), resetsYearly = true),
            Item.Goal("e", "生活防衛資金", null, "x", dueDate = d("2027-01-01"), autoTarget = AutoTarget()),
            Item.Reminder("f", "遠い", d("2030-01-01"), Repeat.NONE, amountYen = 1),
            Item.Reminder("g", "過去", d("2026-01-01"), Repeat.NONE, amountYen = 1),
        )
        assertTrue(ExpenseCalendar.build(items, today).isEmpty())
    }

    @Test
    fun `見込み額は行のtargetYen列に入り、settingsと往復できる`() {
        val r = Item.Reminder("r", "車検", d("2027-03-01"), Repeat.YEARLY, amountYen = 100_000)
        assertEquals(100_000L, r.toEntity().targetYen)
        assertEquals(r, r.toEntity().toItem())
        assertEquals(listOf(r.toEntity()), Settings.parse(Settings.render(listOf(r.toEntity()))))
    }

    @Test
    fun `リマインダーの見込み額の入力は任意`() {
        val ok = ReminderForm.parse(null, "車検", "2027-03-01", Repeat.YEARLY, newId = { "r" }, amount = "100,000") as ReminderForm.Result.Ok
        assertEquals(100_000L, ok.reminder.amountYen)
        val none = ReminderForm.parse(null, "車検", "2027-03-01", Repeat.YEARLY, newId = { "r" }) as ReminderForm.Result.Ok
        assertEquals(null, none.reminder.amountYen)
        assertTrue(ReminderForm.parse(null, "車検", "2027-03-01", Repeat.YEARLY, amount = "abc") is ReminderForm.Result.Invalid)
    }
}
