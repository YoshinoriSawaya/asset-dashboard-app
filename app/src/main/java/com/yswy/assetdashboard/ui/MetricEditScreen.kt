package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Item

/**
 * Metric項目の表示名・非表示(E07-14)・純資産に数えるか(E10-01)・まとめ先(E07-18)・想定利回り(E09-03)。CSVの列名(metricKey)は変えない。
 * 保存先はDriveの settings/items.json。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricEditScreen(
    metric: Item.Metric?,
    saving: Boolean,
    onSave: (MetricForm.Result, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** まとめ先に選べるほかの系列(E07-18) */
    others: List<Item.Metric> = emptyList(),
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
        var groupKey by rememberSaveable { mutableStateOf(metric.groupKey) }
        var returnRate by rememberSaveable { mutableStateOf(MetricForm.rateText(metric.expectedReturnBp)) }
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

        // まとめ先(E07-18)。トップでは親の1行にまとめ、親の詳細に内訳として出す
        if (others.isNotEmpty() || groupKey != null) {
            Text("まとめ先", style = MaterialTheme.typography.titleSmall)
            Text(
                "選ぶと、トップではまとめ先の系列の1行にまとめ、まとめ先の詳細に内訳として出します。" +
                    "NISAの区分を「投資信託」に、銀行ごとの残高を「預金・現金」に、のように使います。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(groupKey == null, onClick = { groupKey = null }, label = { Text("なし") })
                others.forEach { other ->
                    FilterChip(groupKey == other.metricKey, onClick = { groupKey = other.metricKey }, label = { Text(other.name) })
                }
            }
        }

        // 想定利回り(E09-03)。入れた系列だけ、詳細に将来の評価額の目安を出す
        Text("想定利回り", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = returnRate, onValueChange = { returnRate = it },
            label = { Text("年%(例: 3)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "入れると、詳細にこのまま積み立てたときの10・20・30年後の評価額の目安を出します。" +
                "積立額は明細の「積立投資」の月平均です。NISAなど投資の系列に使ってください。出さないなら空にします。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !saving,
                onClick = {
                    val result = MetricForm.parse(metric, name, hidden, inNetWorth, groupKey, returnRate)
                    if (result is MetricForm.Result.Invalid) {
                        message = result.message
                    } else {
                        message = "保存中..."
                        onSave(result) { error -> if (error == null) onBack() else message = error }
                    }
                },
            ) { Text("保存") }
            // 列名に戻して、隠す・純資産に数える・まとめ先・想定利回りをやめる(settingsから消す)
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
