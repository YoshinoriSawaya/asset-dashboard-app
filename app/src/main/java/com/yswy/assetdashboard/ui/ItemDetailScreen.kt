package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.ItemOverview

/**
 * 項目の詳細画面。
 *
 * E03-01では遷移先として置いただけ。種類ごとの中身(Metricの推移グラフ、
 * Goalの進捗バー、Reminderの履歴)はE03-02で作る。
 */
@Composable
fun ItemDetailScreen(overview: ItemOverview?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        if (overview == null) {
            // 同期で項目が消えた、など
            Text("この項目は見つかりません", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(overview.item.name, style = MaterialTheme.typography.headlineSmall)
        Text("詳細はE03-02で作る", style = MaterialTheme.typography.bodySmall)
    }
}
