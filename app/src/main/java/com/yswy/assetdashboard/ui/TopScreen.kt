package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.BuildConfig
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.data.SyncStatus
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import java.time.LocalDate

/** トップ画面(E03-01)。同期の状態と、項目の一覧。 */
@Composable
fun TopScreen(
    state: DashboardViewModel.UiState,
    onSync: () -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            SyncHeader(state, onSync)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (state.overviews.isEmpty()) {
            item {
                Text(
                    "まだ項目がありません。CSVを inbox に置いて同期してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        items(state.overviews, key = { it.item.id }) { overview ->
            ItemRow(overview, onClick = { onOpenItem(overview.item.id) })
            HorizontalDivider()
        }

        item {
            Text(
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SyncHeader(state: DashboardViewModel.UiState, onSync: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("資産ダッシュボード", style = MaterialTheme.typography.headlineSmall)
        state.syncStatus?.let {
            Text(
                it.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = if (it.isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSync, enabled = !state.syncing) { Text("Driveと同期") }
        }
        if (state.message.isNotBlank()) {
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ItemRow(overview: ItemOverview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(overview.item.name, style = MaterialTheme.typography.titleMedium)
            Text(kindLabel(overview.item), style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            val (main, sub) = values(overview)
            Text(main, style = MaterialTheme.typography.titleMedium)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun kindLabel(item: Item): String = when (item) {
    is Item.Metric -> "推移"
    is Item.Goal -> "目標"
    is Item.Reminder -> "リマインダー"
}

/** 右側に出す主な値と、補足。 */
private fun values(overview: ItemOverview): Pair<String, String?> = when (overview) {
    is ItemOverview.Metric -> {
        val latest = overview.latest
        if (latest == null) {
            "データなし" to null
        } else {
            Formatters.yen(latest.valueYen) to
                (overview.monthChangeYen?.let { "今月 ${Formatters.yenChange(it)}" } ?: "${latest.date}時点")
        }
    }
    is ItemOverview.Goal -> {
        val progress = overview.progress
        (progress?.let(Formatters::percent) ?: "進捗不明") to "目標 ${Formatters.yen(overview.item.targetYen)}"
    }
    is ItemOverview.Reminder ->
        Formatters.daysLeft(overview.daysLeft) to overview.item.dueDate.toString()
}

@Preview(showBackground = true)
@Composable
private fun TopScreenPreview() {
    val today = LocalDate.of(2026, 9, 25)
    val metric = Item.Metric(Item.metricId("合計"), "合計", "合計")
    AssetDashboardTheme {
        TopScreen(
            state = DashboardViewModel.UiState(
                overviews = listOf(
                    ItemOverview.Metric(metric, MetricPointEntity("合計", today, 1_500_000, MetricOrigin.CSV), 25_000),
                    ItemOverview.Goal(Item.Goal("g", "車購入", 1_000_000, "預金・現金"), 420_000),
                    ItemOverview.Reminder(Item.Reminder("r", "車の点検", today.plusDays(12), Repeat.YEARLY), 12),
                ),
                syncStatus = SyncStatus(today, isDue = false),
                message = "期限内なので同期しない",
            ),
            onSync = {},
            onOpenItem = {},
        )
    }
}
