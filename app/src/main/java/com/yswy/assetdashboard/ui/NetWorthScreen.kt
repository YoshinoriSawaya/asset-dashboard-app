package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemDetail
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.NetWorth
import com.yswy.assetdashboard.ui.chart.ColorDot
import com.yswy.assetdashboard.ui.chart.DonutChart
import com.yswy.assetdashboard.ui.chart.GoalColors
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import java.time.LocalDate

/**
 * 純資産の詳細(E10-01)。推移の折れ線・月ごとの増減・月次の表は系列の詳細(E03-06)と同じもの。
 *
 * 合算した値なので、補正の入口は出さない(直すなら元の系列を直す)。
 * 何を足しているかは見えないと信用できないので、数えている系列を並べ、そこから外せるようにする。
 */
@Composable
fun NetWorthScreen(
    netWorth: NetWorth?,
    onEditMetric: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { TextButton(onClick = onBack) { Text("← 戻る") } }
        item { Text("純資産", style = MaterialTheme.typography.headlineSmall) }

        if (netWorth == null) {
            item { Text("数える系列が選ばれていません。系列の詳細の「名前・表示を変更」から選べます。") }
            return@LazyColumn
        }

        item {
            val money = LocalMoney.current
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer8()
                val latest = netWorth.latest
                if (latest == null) {
                    Text("データなし", style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(money.amount(latest.valueYen), style = MaterialTheme.typography.titleLarge)
                    Label("${latest.date}時点。各系列のその日までの最新値の合計")
                }
                netWorth.lateStarts.forEach { (metric, first) ->
                    // 途中から加わった系列のぶん、推移に段が付く。増えたように見えるので断っておく
                    Text(
                        "「${metric.name}」は${first}からの数字です。それより前は足していません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer8()
                Text("配分", style = MaterialTheme.typography.titleMedium)
                // 配分(E10-03)。数えている系列の最新値の比率
                val pie = netWorth.pieParts
                if (pie.isNotEmpty()) {
                    val colors = pie.indices.map { GoalColors.of(it) }
                    DonutChart(
                        pie.mapIndexed { i, (_, ratio) -> ratio to colors[i] },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                    )
                }
                Label("系列をタップすると、名前の変更や純資産から外すことができます")
            }
        }
        items(netWorth.metrics, key = { "nw-" + it.id }) { metric ->
            val pie = netWorth.pieParts
            val index = pie.indexOfFirst { it.first.metric.id == metric.id }
            val part = netWorth.breakdown.firstOrNull { it.metric.id == metric.id }
            LegendRow(
                metric = metric,
                color = index.takeIf { it >= 0 }?.let { GoalColors.of(it) },
                ratio = pie.getOrNull(index)?.second,
                part = part,
                latestDate = netWorth.latest?.date,
                onClick = { onEditMetric(metric.metricKey) },
            )
        }
        if (netWorth.excludedParts.isNotEmpty()) {
            item {
                Label("マイナスや0の系列は円グラフに入れていません(純資産の額には含めています)")
            }
        }

        metricContent(asMetricDetail(netWorth))
    }
}

/** 凡例の1行。色の丸・系列名・割合・額。円グラフに入らない系列は丸を出さない。 */
@Composable
private fun LegendRow(
    metric: Item.Metric,
    color: Color?,
    ratio: Double?,
    part: NetWorth.Part?,
    latestDate: LocalDate?,
    onClick: () -> Unit,
) {
    val money = LocalMoney.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (color != null) ColorDot(color) else Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(metric.name, style = MaterialTheme.typography.bodyMedium)
            val notes = buildList {
                if (metric.hidden) add("一覧では隠している")
                // 更新の止まった系列は、古い値のまま比率に入っている
                if (part != null && latestDate != null && part.date < latestDate) add("${part.date}時点の値")
                if (part == null) add("データなし")
            }
            if (notes.isNotEmpty()) Label(notes.joinToString(" / "))
        }
        Column(horizontalAlignment = Alignment.End) {
            ratio?.let { Text(money.share(it), style = MaterialTheme.typography.bodyMedium) }
            part?.let { Text(money.amount(it.valueYen), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** 系列の詳細と同じ部品で描くために、合算をひとつの系列に見立てる。 */
private fun asMetricDetail(netWorth: NetWorth): ItemDetail.Metric {
    val item = Item.Metric(id = NetWorth.KEY, name = "純資産", metricKey = NetWorth.KEY)
    return ItemDetail.Metric(
        overview = ItemOverview.Metric(item, netWorth.latest, netWorth.monthChangeYen),
        monthly = netWorth.monthly,
        series = netWorth.series,
        pointCount = netWorth.series.size,
        correctedCount = 0,
    )
}

@Preview(showBackground = true)
@Composable
private fun NetWorthScreenPreview() {
    val today = LocalDate.of(2026, 9, 25)
    val cash = Item.Metric(Item.metricId("預金・現金"), "預金・現金", "預金・現金", inNetWorth = true)
    val fund = Item.Metric(Item.metricId("投資信託"), "投資信託", "投資信託", inNetWorth = true)
    val points = mapOf(
        "預金・現金" to (0L..5L).map { MetricPointEntity("預金・現金", today.minusMonths(5 - it), 1_000_000 + it * 20_000, MetricOrigin.CSV) },
        "投資信託" to (0L..2L).map { MetricPointEntity("投資信託", today.minusMonths(2 - it), 500_000 + it * 30_000, MetricOrigin.CSV) },
    )
    AssetDashboardTheme {
        NetWorthScreen(netWorth = NetWorth.of(listOf(cash, fund), points, today), onEditMetric = {}, onBack = {})
    }
}
