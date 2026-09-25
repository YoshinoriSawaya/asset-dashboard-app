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
            Triple("車", "1000", "2030/04/01"),
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
}
