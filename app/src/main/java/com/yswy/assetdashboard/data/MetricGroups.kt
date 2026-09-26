package com.yswy.assetdashboard.data

/**
 * 系列のまとめ(E07-18)。まとめ先([Item.Metric.groupKey])のある系列は、トップでは親の1行にまとめ、
 * 親の詳細に内訳として並べる。
 *
 * 親が一覧に出ていなければ(隠している・系列が消えた)、子は自分の行で出す。
 * 親を隠したら子まで見えなくなる、ということが起きないようにするため。
 */
object MetricGroups {

    /** 親のmetricKey → 内訳の系列(一覧の順)。親が一覧に出ている子だけ。 */
    fun children(overviews: List<ItemOverview>): Map<String, List<ItemOverview.Metric>> {
        val metrics = overviews.filterIsInstance<ItemOverview.Metric>()
        val shown = metrics.map { it.item.metricKey }.toSet()
        return metrics
            .filter { m -> m.item.groupKey?.let { it != m.item.metricKey && it in shown } == true }
            .groupBy { it.item.groupKey!! }
    }

    /** トップに並べる項目。内訳としてまとめた系列を除く。 */
    fun topLevel(overviews: List<ItemOverview>): List<ItemOverview> {
        val grouped = children(overviews).values.flatten().map { it.item.id }.toSet()
        return overviews.filterNot { it.item.id in grouped }
    }
}
