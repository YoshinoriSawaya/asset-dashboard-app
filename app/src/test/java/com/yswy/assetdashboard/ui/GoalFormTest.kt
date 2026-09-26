package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.toEntity
import com.yswy.assetdashboard.drive.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalFormTest {

    private fun ok(existing: Item.Goal?, name: String, target: String, key: String?, due: String) =
        (GoalForm.parse(existing, name, target, key, due, newId = { "new-id" }) as GoalForm.Result.Ok).goal

    @Test
    fun `新規は新しいidで、一覧の上に出る並び順`() {
        val goal = ok(null, " 車の購入 ", "1,000,000", "預金・現金", "2030-04-01")
        assertEquals(Item.Goal("new-id", "車の購入", 1_000_000, "預金・現金", LocalDate.of(2030, 4, 1), sortOrder = -1), goal)
    }

    @Test
    fun `編集はidと並び順・非表示を引き継ぐ`() {
        val before = Item.Goal("g1", "PC", 200_000, sortOrder = 3, hidden = true)
        val after = ok(before, "PC買い替え", "250000", null, "")
        assertEquals("g1", after.id)
        assertEquals(3, after.sortOrder)
        assertTrue(after.hidden)
        assertNull(after.dueDate)
        assertNull(after.metricKey)
    }

    @Test
    fun `入力の誤りは通さない`() {
        for ((name, target, due) in listOf(
            Triple("", "1000", ""),
            Triple("車", "", ""),
            Triple("車", "0", ""),
            Triple("車", "-5000", ""),
            Triple("車", "1O00", ""),
            Triple("車", "1000", "2030/04/31"), // 存在しない日(E07-17で区切りのある形は通す)
        )) {
            assertTrue("$name $target $due", GoalForm.parse(null, name, target, null, due) is GoalForm.Result.Invalid)
        }
    }

    @Test
    fun `settingsの項目は同じidなら置き換え、消すと無くなる`() {
        val a = Item.Goal("a", "A", 1).toEntity()
        val b = Item.Goal("b", "B", 2).toEntity()
        val a2 = Item.Goal("a", "A2", 3).toEntity()

        val replaced = Settings.upsert(listOf(a, b), a2)
        assertEquals(listOf("b", "a"), replaced.map { it.id })
        assertEquals("A2", replaced.last().name)
        assertEquals(listOf("a"), Settings.remove(replaced, "b").map { it.id })
    }

    @Test
    fun `積み増しの時期は期日の何か月前かで、期日と金額の目標が要る`() {
        fun parse(due: String, rampUp: String, auto: Pair<String, String>? = null, yearly: Boolean = false) =
            GoalForm.parse(null, "車", "1,000,000", null, due, newId = { "new-id" }, auto = auto, resetsYearly = yearly, rampUp = rampUp)

        assertEquals(12, ((parse("2030-04-01", " 12 ")) as GoalForm.Result.Ok).goal.rampUpMonths)
        assertNull(((parse("2030-04-01", "")) as GoalForm.Result.Ok).goal.rampUpMonths)
        assertTrue(parse("", "12") is GoalForm.Result.Invalid)
        assertTrue(parse("2030-04-01", "0") is GoalForm.Result.Invalid)
        assertTrue(parse("2030-04-01", "121") is GoalForm.Result.Invalid)
        assertTrue(parse("2030-04-01", "12", auto = "6" to "6") is GoalForm.Result.Invalid)
        assertTrue(parse("2030-04-01", "12", yearly = true) is GoalForm.Result.Invalid)
    }

    @Test
    fun `下限は生活費から出す目標で、何か月分より少なく`() {
        fun parse(floor: String, cover: String = "6") =
            GoalForm.parse(null, "生活防衛資金", "", null, "", newId = { "new-id" }, auto = "6" to cover, floor = floor)

        assertEquals(3, ((parse("3")) as GoalForm.Result.Ok).goal.autoTarget?.floorMonths)
        assertNull(((parse(" ")) as GoalForm.Result.Ok).goal.autoTarget?.floorMonths)
        assertTrue(parse("6") is GoalForm.Result.Invalid)
        assertTrue(parse("0") is GoalForm.Result.Invalid)
        assertTrue(parse("1", cover = "1") is GoalForm.Result.Invalid)
    }
}
