package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ItemTest {

    @Test
    fun `3種類とも行に変換して戻すと同じものになる`() {
        val items = listOf(
            Item.Metric(Item.metricId("投資信託"), "NISA", "投資信託", sortOrder = 1),
            Item.Goal("g1", "車購入", targetYen = 1_000_000, metricKey = "預金・現金",
                dueDate = LocalDate.of(2030, 4, 1)),
            Item.Reminder("r1", "車の点検", LocalDate.of(2027, 3, 1), Repeat.YEARLY, hidden = true),
        )
        for (item in items) {
            assertEquals(item, item.toEntity().toItem())
        }
    }

    @Test
    fun `種類に必要な列が欠けた行はnullにしてスキップさせる`() {
        assertNull(ItemEntity("m", ItemType.METRIC, "系列なし").toItem())
        assertNull(ItemEntity("g", ItemType.GOAL, "目標額なし", metricKey = "合計").toItem())
        assertNull(ItemEntity("r", ItemType.REMINDER, "期日なし", repeat = Repeat.MONTHLY).toItem())
    }

    @Test
    fun `Goalは系列が未設定でもよい`() {
        val goal = ItemEntity("g", ItemType.GOAL, "PC買い替え", targetYen = 200_000).toItem()
        assertEquals(Item.Goal("g", "PC買い替え", 200_000), goal)
    }

    @Test
    fun `Reminderの繰り返しが空なら一回きりとして読む`() {
        val reminder = ItemEntity("r", ItemType.REMINDER, "申告", dueDate = LocalDate.of(2027, 2, 16))
            .toItem() as Item.Reminder
        assertEquals(Repeat.NONE, reminder.repeat)
    }

    @Test
    fun `Metric項目のidはmetricKeyから決まる`() {
        assertEquals("metric:預金・現金", Item.metricId("預金・現金"))
    }

    @Test
    fun `日付は並べ替えが効く文字列で持つ`() {
        val c = Converters()
        assertEquals("2026-09-05", c.fromDate(LocalDate.of(2026, 9, 5)))
        assertEquals(LocalDate.of(2026, 9, 5), c.toDate("2026-09-05"))
        assertNull(c.toDate(null))
    }
}
