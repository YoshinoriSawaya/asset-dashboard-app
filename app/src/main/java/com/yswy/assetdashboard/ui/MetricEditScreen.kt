package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Item

/**
 * Metric項目の表示名・非表示(E07-14)・純資産に数えるか(E10-01)。CSVの列名(metricKey)は変えない。
 * 保存先はDriveの settings/items.json。
 */
@Composable
fun MetricEditScreen(
    metric: Item.Metric?,
    saving: Boolean,
    onSave: (MetricForm.Result, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        if (metric == null) {
            Text("この系列は見つかりません")
            return@Column
        }

        var name by rememberSaveable { mutableStateOf(metric.name) }
        var hidden by rememberSaveable { mutableStateOf(metric.hidden) }
        var inNetWorth by rememberSaveable { mutableStateOf(metric.inNetWorth) }
        var message by rememberSaveable { mutableStateOf<String?>(null) }

        Text("名前・表示を変更", style = MaterialTheme.typography.headlineSmall)
        Text(
            "CSVの列「${metric.metricKey}」の表示だけを変えます。値や列名は変わりません。" +
                "Driveの settings に保存します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("表示名") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = hidden, onCheckedChange = { hidden = it })
            Column {
                Text("一覧から隠す")
                Text(
                    "隠してもデータは残り、サマリーやAI用の書き出しからも外れます。トップの「隠している項目」から戻せます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = inNetWorth, onCheckedChange = { inNetWorth = it })
            Column {
                Text("純資産に数える")
                Text(
                    "トップの純資産に足します。「合計」とその内訳のように重なる系列は、どちらか一方だけにしてください。" +
                        "隠していても数えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !saving,
                onClick = {
                    val result = MetricForm.parse(metric, name, hidden, inNetWorth)
                    if (result is MetricForm.Result.Invalid) {
                        message = result.message
                    } else {
                        message = "保存中..."
                        onSave(result) { error -> if (error == null) onBack() else message = error }
                    }
                },
            ) { Text("保存") }
            // 列名に戻して、隠すのも純資産に数えるのもやめる(settingsから消す)
            TextButton(
                enabled = !saving && !MetricForm.isDefault(metric),
                onClick = {
                    message = "保存中..."
                    onSave(MetricForm.Result.Reset(metric.id)) { error -> if (error == null) onBack() else message = error }
                },
            ) { Text("元に戻す") }
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}
