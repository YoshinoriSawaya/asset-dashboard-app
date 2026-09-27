package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.data.AutoTarget
import com.yswy.assetdashboard.data.AutoTargets
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.data.SyncStatus
import com.yswy.assetdashboard.ui.ReminderForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationRulesTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val fresh = SyncStatus(today, isDue = false)
    private val due = SyncStatus(LocalDate.of(2026, 8, 1), isDue = true)

    private fun keys(sync: SyncStatus = fresh, items: List<ItemOverview> = emptyList(), last: Map<String, LocalDate> = emptyMap(), on: LocalDate = today) =
        NotificationRules.evaluate(on, sync, items, last).map { it.key }

    @Test
    fun `同期が必要なら催促し、3日空くまで繰り返さない`() {
        assertEquals(listOf("sync"), keys(sync = due))
        assertEquals(emptyList<String>(), keys(sync = due, last = mapOf("sync" to today.minusDays(2))))
        assertEquals(listOf("sync"), keys(sync = due, last = mapOf("sync" to today.minusDays(3))))
        assertEquals(emptyList<String>(), keys(sync = fresh))
    }

    @Test
    fun `リマインダーは7日前と当日に、それぞれ1度だけ`() {
        fun reminder(daysLeft: Long) = ItemOverview.Reminder(Item.Reminder("r", "車検", today.plusDays(daysLeft), Repeat.YEARLY), daysLeft)
        val dueDate = today.plusDays(7)
        assertEquals(listOf("reminder:r:$dueDate:soon"), keys(items = listOf(reminder(7))))
        assertEquals(emptyList<String>(), keys(items = listOf(reminder(8))))
        assertEquals(emptyList<String>(), keys(items = listOf(reminder(3)), last = mapOf("reminder:r:${today.plusDays(3)}:soon" to today.minusDays(4))))
        assertEquals(listOf("reminder:r:$today:today"), keys(items = listOf(reminder(0))))
        assertEquals(emptyList<String>(), keys(items = listOf(reminder(-1))))
    }

    @Test
    fun `生活費から出す目標を下回ったら30日に1度`() {
        val goal = Item.Goal("g", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6))
        val short = ItemOverview.Goal(goal, 500_000, AutoTargets.Result(1_800_000, 1, 300_000))
        val enough = ItemOverview.Goal(goal, 2_000_000, AutoTargets.Result(1_800_000, 1, 300_000))
        assertEquals(listOf("shortfall:g"), keys(items = listOf(short)))
        assertEquals(emptyList<String>(), keys(items = listOf(short), last = mapOf("shortfall:g" to today.minusDays(29))))
        assertEquals(emptyList<String>(), keys(items = listOf(enough)))
        // 決まった額の目標(貯める目標)は、届いていなくても通知しない
        val saving = ItemOverview.Goal(Item.Goal("c", "車", 1_000_000, "預金・現金"), 100_000)
        assertEquals(emptyList<String>(), keys(items = listOf(saving)))
    }

    @Test
    fun `毎年の枠は12月に残っていれば週に1度`() {
        val goal = Item.Goal("f", "ふるさと納税", 100_000, "ふるさと納税", resetsYearly = true)
        val left = ItemOverview.Goal(goal, 40_000)
        val dec = LocalDate.of(2026, 12, 3)
        assertEquals(emptyList<String>(), keys(items = listOf(left)))
        assertEquals(listOf("allowance:f"), keys(items = listOf(left), on = dec))
        assertEquals(emptyList<String>(), keys(items = listOf(left), on = dec, last = mapOf("allowance:f" to dec.minusDays(6))))
        assertEquals(emptyList<String>(), keys(items = listOf(ItemOverview.Goal(goal, 100_000)), on = dec))
    }

    @Test
    fun `通知の文面に金額を出さない`() {
        val goal = Item.Goal("g", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6))
        val notices = NotificationRules.evaluate(
            today, due,
            listOf(
                ItemOverview.Goal(goal, 500_000, AutoTargets.Result(1_800_000, 1, 300_000)),
                ItemOverview.Reminder(Item.Reminder("r", "車検", today, Repeat.YEARLY), 0),
            ),
            emptyMap(),
        )
        notices.forEach { assertFalse(it.text, (it.title + it.text).contains("円")) }
    }

    @Test
    fun `済みにすると、繰り返すものは次の期日へ、繰り返さないものは消える`() {
        val yearly = Item.Reminder("r", "車検", LocalDate.of(2026, 9, 20), Repeat.YEARLY)
        assertEquals(LocalDate.of(2027, 9, 20), ReminderForm.completed(yearly, today)?.dueDate)
        val monthly = Item.Reminder("m", "家計の見直し", LocalDate.of(2026, 7, 31), Repeat.MONTHLY)
        // 期日を大きく過ぎていても、今日より後になるまで進める
        assertEquals(LocalDate.of(2026, 9, 30), ReminderForm.completed(monthly, today)?.dueDate)
        assertNull(ReminderForm.completed(yearly.copy(repeat = Repeat.NONE), today))
    }

    @Test
    fun `リマインダーの入力`() {
        val ok = ReminderForm.parse(null, " 保険の更新 ", "2027-03-01", Repeat.YEARLY, newId = { "id" }) as ReminderForm.Result.Ok
        assertEquals(Item.Reminder("id", "保険の更新", LocalDate.of(2027, 3, 1), Repeat.YEARLY, sortOrder = -1), ok.reminder)
        assertTrue(ReminderForm.parse(null, "", "2027-03-01", Repeat.YEARLY) is ReminderForm.Result.Invalid)
        // 区切りのある形・yyyymmddは通す(E07-17)。読めない日付は通さない
        assertTrue(ReminderForm.parse(null, "x", "2027/13/01", Repeat.YEARLY) is ReminderForm.Result.Invalid)
    }

    @Test
    fun `最後に出した日の記録は、古いものを捨てて往復できる`() {
        val map = mapOf("sync" to today, "reminder:old" to today.minusDays(NotifyStore.KEEP_DAYS + 1))
        assertEquals(mapOf("sync" to today), NotifyStore.prune(map, today))
        assertEquals(map, NotifyStore.parse(NotifyStore.render(map)))
        assertEquals(emptyMap<String, LocalDate>(), NotifyStore.parse("壊れた"))
    }

    @Test
    fun `次の確認は今日の9時か、過ぎていれば明日の9時`() {
        val zone = ZoneId.of("Asia/Tokyo")
        assertEquals(ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, zone), DailyCheck.nextCheckAt(ZonedDateTime.of(2026, 9, 25, 8, 30, 0, 0, zone)))
        assertEquals(ZonedDateTime.of(2026, 9, 26, 9, 0, 0, 0, zone), DailyCheck.nextCheckAt(ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, zone)))
    }

    @Test
    fun `積み増しの時期に入ったら知らせ、届くまで30日に1度。文面に金額を入れない`() {
        val dueDate = LocalDate.of(2027, 3, 1)
        val car = Item.Goal("c", "車", 1_000_000, "預金・現金", dueDate = dueDate, rampUpMonths = 6)
        fun at(current: Long) = listOf(ItemOverview.Goal(car, current))
        val key = "rampup:c:$dueDate"

        // 2026-09 はまだ(始めるのは 2026-09 = 期日の6か月前 → 今日から)
        assertEquals(listOf(key), keys(items = at(100_000)))
        assertEquals(emptyList<String>(), keys(items = at(100_000), on = LocalDate.of(2026, 8, 31)))

        val first = NotificationRules.evaluate(today, fresh, at(100_000), emptyMap()).single()
        assertTrue(first.title.contains("始める時期"))
        val again = NotificationRules.evaluate(today, fresh, at(100_000), mapOf(key to today.minusDays(30))).single()
        assertFalse(again.title.contains("始める時期"))
        for (notice in listOf(first, again)) assertFalse((notice.title + notice.text).contains("000"))

        assertEquals(emptyList<String>(), keys(items = at(100_000), last = mapOf(key to today.minusDays(29))))
        // 届いたら出さない
        assertEquals(emptyList<String>(), keys(items = at(1_000_000)))
    }

    @Test
    fun `下限までの取り崩しは咎めず30日ごと、下限を割ったら週に1度`() {
        val fundItem = Item.Goal("f", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6, 3))
        fun fund(current: Long) = listOf(ItemOverview.Goal(fundItem, current, AutoTargets.Result(1_800_000, 6, 300_000)))

        val drawn = NotificationRules.evaluate(today, fresh, fund(1_000_000), emptyMap()).single()
        assertEquals("shortfall:f", drawn.key)
        assertTrue(drawn.title.contains("取り崩しています"))

        val below = NotificationRules.evaluate(today, fresh, fund(800_000), emptyMap()).single()
        assertEquals("belowfloor:f", below.key)
        assertTrue(below.title.contains("下限を割って"))
        assertEquals(emptyList<String>(), keys(items = fund(800_000), last = mapOf("belowfloor:f" to today.minusDays(6))))
        assertEquals(listOf("belowfloor:f"), keys(items = fund(800_000), last = mapOf("belowfloor:f" to today.minusDays(7))))

        for (notice in listOf(drawn, below)) assertFalse((notice.title + notice.text).contains("000"))
    }

    @Test
    fun `期日の目標が届かずに期日を過ぎたら、期日ごとに1度`() {
        val dueDate = LocalDate.of(2026, 9, 1)
        val car = listOf(ItemOverview.Goal(Item.Goal("c", "車", 1_000_000, "車の資金", dueDate = dueDate, rampUpMonths = 12), 600_000))
        val key = "rampup:c:$dueDate:overdue"
        assertEquals(listOf(key), keys(items = car))
        assertEquals(emptyList<String>(), keys(items = car, last = mapOf(key to today.minusDays(100))))
    }

    // E05-08: いつもより多い月の通知
    private var n = 0
    private fun tx(date: String, yen: Long, category: String?, kind: CategoryKind?) = BankTransactionEntity(
        "k${n++}", LocalDate.parse(date), "店", yen, null, null, null, CardStatementAdapter.LABEL, "f",
        category = category, categoryKind = kind,
    )

    /** 7月は外食1万・家電0、8月は外食5万・家電30万。9月の明細が1件あるかで、8月が締まったかが変わる。 */
    private fun spendingRows(withThisMonth: Boolean) = buildList {
        add(tx("2026-07-10", 10_000, "外食", CategoryKind.DISCRETIONARY))
        add(tx("2026-08-10", 50_000, "外食", CategoryKind.DISCRETIONARY))
        add(tx("2026-08-20", 300_000, "家具・家電", CategoryKind.PLANNED))
        if (withThisMonth) add(tx("2026-09-02", 1_000, "食費", CategoryKind.LIVING))
    }

    @Test
    fun `前の月にいつもより多い消費のカテゴリがあれば、その月につき1度、カテゴリ名だけで知らせる`() {
        val august = NotificationRules.lastMonthSpending(spendingRows(withThisMonth = true), today)!!
        val notice = NotificationRules.evaluate(today, fresh, emptyList(), emptyMap(), august).single()
        assertEquals("highspend:2026-08", notice.key)
        // 大型出費(家具・家電)は ▲ でも知らせない
        assertEquals("8月は外食がいつもより多め", notice.title)
        assertFalse((notice.title + notice.text).contains("円"))
        assertTrue(NotificationRules.evaluate(today, fresh, emptyList(), mapOf(notice.key to today), august).isEmpty())
    }

    // E07-28: 固定費の値上がりの通知
    private fun card(date: String, desc: String, yen: Long) = BankTransactionEntity(
        "k${n++}", LocalDate.parse(date), desc, yen, null, null, null, CardStatementAdapter.LABEL, "f",
    )

    /** 3〜8月に毎月出る支払い。動画・音楽・家賃振込は8月に値上がり、電話は据え置き。 */
    private fun fixedRows(withThisMonth: Boolean) = buildList {
        listOf("03", "04", "05", "06", "07", "08").forEach { m ->
            val up = m == "08"
            add(card("2026-$m-05", "動画サブスク", if (up) 1_290 else 990))
            add(card("2026-$m-06", "音楽サブスク", if (up) 1_180 else 980))
            add(card("2026-$m-07", "振込 ﾀﾅｶ ﾀﾛｳ", if (up) 60_000 else 50_000))
            add(card("2026-$m-08", "電話", 2_000))
        }
        if (withThisMonth) add(card("2026-09-02", "コンビニ", 500))
    }

    @Test
    fun `前の月に値上がりした固定費があれば、その月につき1度、名前だけで知らせる`() {
        val costs = NotificationRules.lastMonthFixedCosts(fixedRows(withThisMonth = true), today)!!
        val notice = NotificationRules.evaluate(today, fresh, emptyList(), emptyMap(), fixedCosts = costs).single()
        assertEquals("priceup:2026-08", notice.key)
        assertEquals("固定費が値上がりしました", notice.title)
        // 額の多い順に2つまで。振込の相手は伏せる。3つ目からは「など」
        assertEquals("振込(相手は伏せる)・動画サブスクなど。アプリの「固定費・サブスク」で確認してください。", notice.text)
        assertFalse(notice.text.contains("ﾀﾅｶ"))
        assertTrue(NotificationRules.evaluate(today, fresh, emptyList(), mapOf(notice.key to today), fixedCosts = costs).isEmpty())
    }

    @Test
    fun `前の月に予算を超えたカテゴリがあれば、その月につき1度、カテゴリ名だけで知らせる`() {
        val august = NotificationRules.lastMonthSpending(spendingRows(withThisMonth = true), today)!!
        // 8月: 外食5万、家具・家電30万。外食の予算4万は超え、家具・家電の予算50万は超えない。予算の無い食費は見ない
        val budgets = mapOf("外食" to 40_000L, "家具・家電" to 500_000L)
        val notice = NotificationRules.evaluate(today, fresh, emptyList(), mapOf("highspend:2026-08" to today), august, budgets = budgets).single()
        assertEquals("overbudget:2026-08", notice.key)
        assertEquals("8月は外食が予算を超えました", notice.title)
        assertFalse((notice.title + notice.text).contains("円"))
        assertTrue(NotificationRules.evaluate(today, fresh, emptyList(), mapOf("highspend:2026-08" to today, notice.key to today), august, budgets = budgets).isEmpty())
        // 予算が無ければ出さない
        assertEquals(emptyList<String>(), NotificationRules.overBudget(august, emptyMap()))
    }

    @Test
    fun `前の月が締まっていなければ固定費の値上がりは知らせない`() {
        assertNull(NotificationRules.lastMonthFixedCosts(fixedRows(withThisMonth = false), today))
        // 10月に見ると、見た月の最後(9月)に明細が無いので、古い月の値上がりで知らせない
        assertNull(NotificationRules.lastMonthFixedCosts(fixedRows(withThisMonth = false) + card("2026-10-01", "コンビニ", 500), LocalDate.of(2026, 10, 3)))
    }

    @Test
    fun `今月の明細がまだ無ければ、前の月は締まっていないとみて知らせない`() {
        assertNull(NotificationRules.lastMonthSpending(spendingRows(withThisMonth = false), today))
        // 比べる前の月が無ければ知らせない
        assertNull(NotificationRules.lastMonthSpending(listOf(tx("2026-08-10", 50_000, "外食", CategoryKind.DISCRETIONARY), tx("2026-09-01", 1, null, null)), today))
    }
}
