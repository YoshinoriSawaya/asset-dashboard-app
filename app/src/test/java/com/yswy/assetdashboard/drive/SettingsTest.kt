package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.data.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SettingsTest {

    @Test
    fun `3種類とも書いたとおりに読み戻せる`() {
        val items = listOf(
            Item.Metric(Item.metricId("投資信託"), "NISA", "投資信託", sortOrder = 2, hidden = true),
            Item.Goal("g1", "車購入", 1_000_000, "預金・現金", LocalDate.of(2030, 4, 1), sortOrder = 1),
            Item.Reminder("r1", "車の点検", LocalDate.of(2027, 3, 1), Repeat.YEARLY),
        ).map { it.toEntity() }

        assertEquals(items, Settings.parse(Settings.render(items)))
    }

    @Test
    fun `読めない項目だけ落として残りは使う`() {
        val json = """
            {"formatVersion":1,"items":[
              {"id":"g1","type":"GOAL","name":"車購入","targetYen":1000000},
              {"id":"g2","type":"GOAL","name":"目標額なし"},
              {"id":"x","type":"UNKNOWN","name":"知らない種類"},
              {"type":"REMINDER","name":"idなし","dueDate":"2027-01-01"},
              {"id":"r1","type":"REMINDER","name":"期日が壊れている","dueDate":"来年"}
            ]}
        """.trimIndent()

        assertEquals(listOf("g1"), Settings.parse(json).map { it.id })
    }

    @Test
    fun `名前が空ならidで代用する`() {
        val json = """{"formatVersion":1,"items":[{"id":"metric:合計","type":"METRIC","metricKey":"合計"}]}"""
        assertEquals("metric:合計", Settings.parse(json).single().name)
    }

    @Test
    fun `積み増しの時期を読み戻せ、書いていない古い設定では無し`() {
        val goal = Item.Goal("g", "車", 1_000_000, dueDate = java.time.LocalDate.of(2030, 4, 1), rampUpMonths = 18).toEntity()
        assertEquals(listOf(goal), Settings.parse(Settings.render(listOf(goal))))
        val old = Item.Goal("g", "車", 1_000_000).toEntity()
        assertEquals(null, Settings.parse(Settings.render(listOf(old))).single().rampUpMonths)
    }

    @Test
    fun `下限を読み戻せ、書いていない古い設定では無し`() {
        val fund = Item.Goal("f", "生活防衛資金", null, autoTarget = com.yswy.assetdashboard.data.AutoTarget(6, 6, 3)).toEntity()
        assertEquals(listOf(fund), Settings.parse(Settings.render(listOf(fund))))
        val old = fund.copy(autoFloorMonths = null)
        assertEquals(null, Settings.parse(Settings.render(listOf(old))).single().autoFloorMonths)
    }

    @Test
    fun `Metricの想定利回りを読み戻せ、書いていない古い設定では無し`() {
        val metric = Item.Metric(Item.metricId("投資信託"), "NISA", "投資信託", expectedReturnBp = 300).toEntity()
        val json = Settings.render(listOf(metric))
        assertEquals(true, json.contains("\"expectedReturnBp\": 300"))
        assertEquals(listOf(metric), Settings.parse(json))
        val old = """{"formatVersion":1,"items":[{"id":"metric:合計","type":"METRIC","name":"合計","metricKey":"合計"}]}"""
        assertEquals(null, Settings.parse(old).single().growthRateBp)
    }

    @Test
    fun `Metricの積立額のカテゴリはinvestCategoryで書き、リマインダーの積立先はfundIdのまま`() {
        val metric = Item.Metric(Item.metricId("投資信託"), "NISA", "投資信託", expectedReturnBp = 300, investCategory = "NISA積立").toEntity()
        val reminder = Item.Reminder("r", "車検", LocalDate.of(2027, 3, 1), Repeat.YEARLY, fundId = "g").toEntity()
        val json = Settings.render(listOf(metric, reminder))
        assertEquals(true, json.contains("\"investCategory\": \"NISA積立\""))
        assertEquals(true, json.contains("\"fundId\": \"g\""))
        assertEquals(listOf(metric, reminder), Settings.parse(json))
    }

    @Test
    fun `純資産に数える印を読み戻せ、書いていない古い設定では数えない`() {
        val metric = Item.Metric(Item.metricId("合計"), "合計", "合計", inNetWorth = true).toEntity()
        assertEquals(listOf(metric), Settings.parse(Settings.render(listOf(metric))))
        val old = """{"formatVersion":1,"items":[{"id":"metric:合計","type":"METRIC","name":"合計","metricKey":"合計"}]}"""
        assertEquals(false, Settings.parse(old).single().inNetWorth)
    }
}
