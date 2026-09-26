package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.drive.Settings
import com.yswy.assetdashboard.ui.MetricForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 系列のまとめ(E07-18)。 */
class MetricGroupsTest {

    private fun metric(key: String, group: String? = null) =
        ItemOverview.Metric(Item.Metric(Item.metricId(key), key, key, groupKey = group), null, null)

    private val fund = metric("投資信託")
    private val nisa1 = metric("NISAつみたて", group = "投資信託")
    private val nisa2 = metric("NISA成長", group = "投資信託")
    private val cash = metric("預金・現金")

    @Test
    fun `まとめ先のある系列はトップから外し、親の内訳にする`() {
        val all = listOf(fund, nisa1, nisa2, cash)
        assertEquals(mapOf("投資信託" to listOf(nisa1, nisa2)), MetricGroups.children(all))
        assertEquals(listOf(fund, cash), MetricGroups.topLevel(all))
    }

    @Test
    fun `親が一覧に無ければ子は自分の行で出す`() {
        val all = listOf(nisa1, cash) // 投資信託を隠している
        assertEquals(emptyMap<String, List<ItemOverview.Metric>>(), MetricGroups.children(all))
        assertEquals(all, MetricGroups.topLevel(all))
    }

    @Test
    fun `まとめ先の入力、自分自身は選べず、外せばsettingsから消せる`() {
        val m = Item.Metric(Item.metricId("NISA"), "NISA", "NISA")
        assertTrue(MetricForm.parse(m, "NISA", false, groupKey = "NISA") is MetricForm.Result.Invalid)
        val saved = MetricForm.parse(m, "NISA", false, groupKey = "投資信託") as MetricForm.Result.Save
        assertEquals("投資信託", saved.metric.groupKey)
        assertEquals(MetricForm.Result.Reset(m.id), MetricForm.parse(saved.metric, "NISA", false, groupKey = null))
    }

    @Test
    fun `まとめ先をsettingsに書いて読み戻せる`() {
        val entity = Item.Metric(Item.metricId("NISA"), "NISA", "NISA", groupKey = "投資信託").toEntity()
        assertEquals(listOf(entity), Settings.parse(Settings.render(listOf(entity))))
        assertEquals("投資信託", (entity.toItem() as Item.Metric).groupKey)
    }
}
