package com.yswy.assetdashboard.ui.chart

import com.yswy.assetdashboard.data.Item
import org.junit.Assert.assertEquals
import org.junit.Test

/** 系列の色の番号(E03-08)。 */
class SeriesColorsTest {

    private fun metric(key: String, hidden: Boolean = false) = Item.Metric(Item.metricId(key), key, key, hidden = hidden)

    @Test
    fun `並び順で番号を振り、隠している系列も数える`() {
        val metrics = listOf(metric("合計"), metric("年金", hidden = true), metric("投資信託"), metric("預金・現金"))
        assertEquals(mapOf("合計" to 0, "年金" to 1, "投資信託" to 2, "預金・現金" to 3), SeriesColors.indexOf(metrics))
    }

    @Test
    fun `隠しても戻しても、ほかの系列の番号は変わらない`() {
        val shown = listOf(metric("合計"), metric("年金"), metric("投資信託"))
        val hidden = listOf(metric("合計"), metric("年金", hidden = true), metric("投資信託"))
        assertEquals(SeriesColors.indexOf(shown), SeriesColors.indexOf(hidden))
    }
}
