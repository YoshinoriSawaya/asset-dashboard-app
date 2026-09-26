package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Cashflow
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.MetricChange
import com.yswy.assetdashboard.data.PeriodSummary
import com.yswy.assetdashboard.data.PeriodUnit
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import java.time.YearMonth

/**
 * 月次・年次サマリー(E03-03)。期間ごとに、系列の増減と入出金を並べる。
 * 先頭が今月(今年)。グラフはE03-06で足す。
 */
@Composable
fun SummaryScreen(
    unit: PeriodUnit,
    summaries: List<PeriodSummary>?,
    onUnitChange: (PeriodUnit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSpendingRules: () -> Unit = {},
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("月次・年次サマリー", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(unit == PeriodUnit.MONTH, onClick = { onUnitChange(PeriodUnit.MONTH) }, label = { Text("月次") })
                FilterChip(unit == PeriodUnit.YEAR, onClick = { onUnitChange(PeriodUnit.YEAR) }, label = { Text("年次") })
            }
            TextButton(onClick = onOpenSpendingRules) { Text("明細のカテゴリ(生活費から除くもの)を設定") }
        }

        when {
            summaries == null -> item { Text("読み込み中...") }
            summaries.isEmpty() -> item { Text("まだデータがありません") }
            else -> items(summaries, key = { it.period.start.toString() }) { summary ->
                PeriodBlock(summary)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun PeriodBlock(summary: PeriodSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(summary.label, style = MaterialTheme.typography.titleMedium)
        summary.metrics.forEach { (item, change) -> MetricLine(item.name, change) }
        val cashflow = summary.cashflow
        if (cashflow == null) {
            SubLine("入出金", "明細なし")
        } else {
            summary.cashflowCoverage?.let { SubLine("明細の期間", "${it.start}〜${it.endInclusive}") }
            CashflowLines(cashflow)
        }
    }
}

@Composable
private fun MetricLine(name: String, change: MetricChange) {
    val money = LocalMoney.current
    Line(
        label = name,
        value = change.closingYen?.let(money::amount) ?: "データなし",
        change = change.changeYen,
        // %のときは期首の値に対する増減率(E06-04)
        changeText = change.changeYen?.let { money.change(it, change.openingYen) },
    )
}

@Composable
private fun CashflowLines(cashflow: Cashflow) {
    val money = LocalMoney.current
    SubLine("収入", money.amount(cashflow.incomeYen))
    SubLine("支出", money.amount(cashflow.spendingYen))
    // 振替などを除いた生活費(E07-06)。除く決まりに当たる出金があるときだけ出す
    if (cashflow.livingSpendingYen != cashflow.spendingYen) {
        SubLine("うち生活費", money.amount(cashflow.livingSpendingYen))
    }
    // %のときは収入に対する収支の割合
    Line(label = "収支", value = "${cashflow.count}件", change = cashflow.netYen, changeText = money.change(cashflow.netYen, cashflow.incomeYen))
}

/** 名前・その期間の最後の値・増減の3列。 */
@Composable
private fun Line(label: String, value: String, change: Long?, changeText: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
        Text(
            changeText ?: "−",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = changeColor(change),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun SubLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(2.5f),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 増減の色。減ったほうはエラー色にする。増えたほうは色を付けない
 * (色だけで意味を伝えないよう、符号は必ず文字でも出している)。
 */
@Composable
private fun changeColor(change: Long?): Color =
    if (change != null && change < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

@Preview(showBackground = true)
@Composable
private fun SummaryPreview() {
    val m = YearMonth.of(2026, 9)
    val items = listOf(Item.Metric(Item.metricId("合計"), "合計", "合計"), Item.Metric(Item.metricId("年金"), "年金", "年金"))
    val period = m.atDay(1)..m.atEndOfMonth()
    AssetDashboardTheme {
        SummaryScreen(
            unit = PeriodUnit.MONTH,
            summaries = listOf(
                PeriodSummary(
                    period, "2026年9月",
                    items.map { it to MetricChange(it.metricKey, period, 1_000_000, 1_020_000) },
                    Cashflow(period, 300_000, 180_000, 12),
                ),
            ),
            onUnitChange = {},
            onBack = {},
        )
    }
}
