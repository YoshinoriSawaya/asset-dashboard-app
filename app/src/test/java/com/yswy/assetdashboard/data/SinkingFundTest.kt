package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.drive.Settings
import com.yswy.assetdashboard.ui.GoalForm
import com.yswy.assetdashboard.ui.ReminderForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 大型出費の積立(E07-15)。値はすべて作り物。 */
class SinkingFundTest {

    private val today = LocalDate.of(2026, 9, 25)
    private fun d(s: String) = LocalDate.parse(s)

    private val fund = Item.Goal("f", "家電・車の積立", null, "預金・現金", sinking = SinkingFund(horizonYears = 5))

    private fun reminder(id: String, due: String, yen: Long, years: Int = 1, repeat: Repeat = Repeat.YEARLY, fundId: String? = "f") =
        Item.Reminder(id, id, d(due), repeat, amountYen = yen, repeatYears = years, fundId = fundId)

    @Test
    fun `N年ごとの回を並べ、向こう1年の合計が目標額、向こうN年をならした額が月々`() {
        val reminders = listOf(
            reminder("車検", "2027-03-01", 150_000, years = 2), // 2027-03, 2029-03, 2031-03(5年の内。2031-09-25まで)
            reminder("自動車税", "2027-05-01", 40_000), // 毎年: 2027〜2031の5回
            reminder("洗濯機", "2030-06-01", 200_000, years = 10), // 1回(次は2040年で外)
        )
        val plan = SinkingPlan.of(fund, reminders, today)!!

        assertEquals(
            listOf(
                "2027-03-01", "2027-05-01", "2028-05-01", "2029-03-01", "2029-05-01",
                "2030-05-01", "2030-06-01", "2031-03-01", "2031-05-01",
            ),
            plan.occurrences.map { it.date.toString() },
        )
        assertEquals(190_000L, plan.nextYearYen)
        // (150,000×3 + 40,000×5 + 200,000) ÷ 60か月 = 14,166.6… → 切り上げ
        assertEquals(14_167L, plan.monthlyYen)
        assertEquals(listOf("車検", "自動車税"), plan.nextYear(today).map { it.reminder.id })
    }

    @Test
    fun `物価上昇は今年からの年数ぶん掛ける`() {
        val growing = fund.copy(sinking = SinkingFund(horizonYears = 5, growthRateBp = 200))
        val plan = SinkingPlan.of(growing, listOf(reminder("PC", "2026-12-01", 100_000, years = 3)), today)!!
        // 2026年は掛けない、2029年は3年ぶん
        assertEquals(listOf(100_000L, 106_121L), plan.occurrences.map { it.amountYen })
        assertEquals(100_000L, SinkingPlan.grown(100_000, 200, 0))
        assertEquals(100_000L, SinkingPlan.grown(100_000, 0, 10))
    }

    @Test
    fun `期日を過ぎて済みにしていない回は、向こう1年に入れる`() {
        val plan = SinkingPlan.of(fund, listOf(reminder("冷蔵庫", "2026-08-01", 150_000, repeat = Repeat.NONE)), today)!!
        assertEquals(150_000L, plan.nextYearYen)
    }

    @Test
    fun `積立先が違う・見込み額の無いリマインダーは数えない。積立でない目標はnull`() {
        val reminders = listOf(
            reminder("他の積立", "2027-01-01", 50_000, fundId = "other"),
            reminder("積立なし", "2027-01-01", 50_000, fundId = null),
            Item.Reminder("額なし", "額なし", d("2027-01-01"), Repeat.YEARLY, fundId = "f"),
        )
        assertEquals(0L, SinkingPlan.of(fund, reminders, today)!!.nextYearYen)
        assertNull(SinkingPlan.of(Item.Goal("g", "車", 1_000_000), reminders, today))
    }

    @Test
    fun `積立の目標の目標額は、向こう1年の予定から出る`() {
        val overview = ItemOverview.of(
            fund, mapOf("預金・現金" to emptyList()), today,
            reminders = listOf(reminder("車検", "2027-03-01", 150_000, years = 2)),
        ) as ItemOverview.Goal
        assertEquals(150_000L, overview.targetYen)
    }

    @Test
    fun `N年ごとのリマインダーを済みにすると、N年先へ進む`() {
        val shaken = reminder("車検", "2026-09-20", 150_000, years = 2)
        assertEquals(d("2028-09-20"), ReminderForm.completed(shaken, today)?.dueDate)
    }

    @Test
    fun `リマインダーの入力、何年ごとと積立先`() {
        val ok = ReminderForm.parse(null, "洗濯機", "2030-06-01", Repeat.YEARLY, newId = { "id" }, amount = "200000", everyYears = "10", fundId = "f")
        val r = (ok as ReminderForm.Result.Ok).reminder
        assertEquals(10, r.repeatYears)
        assertEquals("f", r.fundId)
        // 空なら毎年、毎年でなければ何年ごとは使わない
        assertEquals(1, (ReminderForm.parse(null, "税", "2027-05-01", Repeat.YEARLY, amount = "1") as ReminderForm.Result.Ok).reminder.repeatYears)
        assertEquals(1, (ReminderForm.parse(null, "一度", "2027-05-01", Repeat.NONE, everyYears = "5") as ReminderForm.Result.Ok).reminder.repeatYears)
        assertTrue(ReminderForm.parse(null, "x", "2027-05-01", Repeat.YEARLY, everyYears = "0") is ReminderForm.Result.Invalid)
        // 積立先を選ぶなら見込み額が要る
        assertTrue(ReminderForm.parse(null, "x", "2027-05-01", Repeat.YEARLY, fundId = "f") is ReminderForm.Result.Invalid)
    }

    @Test
    fun `目標の入力、大型出費の予定から出す`() {
        val ok = GoalForm.parse(null, "家電の積立", "", "預金・現金", "", newId = { "id" }, sinking = "5" to "2.5")
        assertEquals(SinkingFund(5, 250), (ok as GoalForm.Result.Ok).goal.sinking)
        assertNull(ok.goal.targetYen)
        assertEquals(SinkingFund(3, 0), (GoalForm.parse(null, "x", "", null, "", sinking = "3" to "") as GoalForm.Result.Ok).goal.sinking)
        assertTrue(GoalForm.parse(null, "x", "", null, "", sinking = "0" to "") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "", null, "", sinking = "5" to "-1") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "", null, "", sinking = "5" to "", auto = "6" to "6") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "", null, "2030-01-01", sinking = "5" to "", rampUp = "12") is GoalForm.Result.Invalid)
    }

    @Test
    fun `settingsに書いて読み戻せる。書いていない古い設定では毎年・積立なし`() {
        val items = listOf(
            fund.copy(sinking = SinkingFund(5, 200)),
            reminder("車検", "2027-03-01", 150_000, years = 2),
        ).map { it.toEntity() }
        val back = Settings.parse(Settings.render(items))
        assertEquals(items, back)
        assertEquals(items.map { it.toItem() }, back.map { it.toItem() })

        val old = Item.Reminder("r", "保険", d("2027-01-01"), Repeat.YEARLY, amountYen = 1).toEntity()
        val oldBack = Settings.parse(Settings.render(listOf(old))).single().toItem() as Item.Reminder
        assertEquals(1, oldBack.repeatYears)
        assertNull(oldBack.fundId)
    }

    @Test
    fun `大型出費の一覧はN年ごとに展開し、積立の物価上昇を掛ける`() {
        val items = listOf(
            fund.copy(sinking = SinkingFund(5, 200)),
            reminder("車検", "2026-11-01", 150_000, years = 2),
        )
        val entries = ExpenseCalendar.build(items, today)
        assertEquals(listOf(d("2026-11-01"), d("2028-11-01")), entries.map { it.date })
        assertEquals(listOf(150_000L, SinkingPlan.grown(150_000, 200, 2)), entries.map { it.amountYen })
    }
}
