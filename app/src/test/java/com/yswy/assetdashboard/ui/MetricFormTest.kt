package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricFormTest {

    private val auto = Item.Metric(Item.metricId("投資信託"), "投資信託", "投資信託")

    @Test
    fun `名前を変えたらsettingsに書く。列名は変えない`() {
        val result = MetricForm.parse(auto, " NISA ", hidden = false) as MetricForm.Result.Save
        assertEquals("NISA", result.metric.name)
        assertEquals("投資信託", result.metric.metricKey)
        assertEquals(auto.id, result.metric.id)
    }

    @Test
    fun `隠すだけでもsettingsに書く`() {
        val result = MetricForm.parse(auto, "投資信託", hidden = true) as MetricForm.Result.Save
        assertTrue(result.metric.hidden)
    }

    @Test
    fun `既定に戻したらsettingsから消す`() {
        val customized = auto.copy(name = "NISA", hidden = true)
        assertEquals(MetricForm.Result.Reset(auto.id), MetricForm.parse(customized, "投資信託", hidden = false))
    }

    @Test
    fun `名前が空なら通さない`() {
        assertTrue(MetricForm.parse(auto, "  ", hidden = false) is MetricForm.Result.Invalid)
    }

    @Test
    fun `既定かどうか`() {
        assertTrue(MetricForm.isDefault(auto))
        assertFalse(MetricForm.isDefault(auto.copy(name = "NISA")))
        assertFalse(MetricForm.isDefault(auto.copy(hidden = true)))
        assertFalse(MetricForm.isDefault(auto.copy(sortOrder = 2)))
        assertFalse(MetricForm.isDefault(auto.copy(inNetWorth = true)))
    }

    @Test
    fun `純資産に数えるだけでもsettingsに書き、やめれば消す`() {
        val result = MetricForm.parse(auto, "投資信託", hidden = false, inNetWorth = true) as MetricForm.Result.Save
        assertTrue(result.metric.inNetWorth)
        assertEquals(MetricForm.Result.Reset(auto.id), MetricForm.parse(result.metric, "投資信託", hidden = false, inNetWorth = false))
    }

    @Test
    fun `数える印を指定しなければ今のまま`() {
        val counted = auto.copy(name = "NISA", inNetWorth = true)
        val result = MetricForm.parse(counted, "NISA", hidden = true) as MetricForm.Result.Save
        assertTrue(result.metric.inNetWorth)
    }
}
