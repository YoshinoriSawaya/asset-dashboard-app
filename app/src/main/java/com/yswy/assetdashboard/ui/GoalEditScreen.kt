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
import androidx.compose.material3.OutlinedButton
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
 * 目標の追加・編集・削除(E07-01)。保存先はDriveの settings/items.json(E02-01)。
 *
 * @param existing 編集する目標。新規ならnull
 * @param metricKeys 進捗を測る系列の候補(手元にある系列)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditScreen(
    existing: Item.Goal?,
    metricKeys: List<String>,
    saving: Boolean,
    onSave: (Item.Goal, (String?) -> Unit) -> Unit,
    onDelete: (String, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSpendingRules: () -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var target by rememberSaveable { mutableStateOf(existing?.targetYen?.toString().orEmpty()) }
    var metricKey by rememberSaveable { mutableStateOf(existing?.metricKey) }
    var due by rememberSaveable { mutableStateOf(existing?.dueDate?.toString().orEmpty()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // 目標額の決め方(E07-06)。生活防衛資金のように、生活費の何か月分かで決めたいとき
    var useAuto by rememberSaveable { mutableStateOf(existing?.autoTarget != null) }
    var averageMonths by rememberSaveable { mutableStateOf((existing?.autoTarget?.averageMonths ?: 6).toString()) }
    var resetsYearly by rememberSaveable { mutableStateOf(existing?.resetsYearly ?: false) }
    var coverMonths by rememberSaveable { mutableStateOf((existing?.autoTarget?.coverMonths ?: 6).toString()) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text(if (existing == null) "目標を追加" else "目標を編集", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Driveの settings に保存します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("名前(例: 車の購入)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text("目標額の決め方", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!useAuto, onClick = { useAuto = false }, label = { Text("金額を入れる") })
            FilterChip(useAuto, onClick = { useAuto = true }, label = { Text("生活費から出す") })
        }
        if (!useAuto) {
            OutlinedTextField(
                value = target, onValueChange = { target = it },
                label = { Text("目標額(円)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "直近の生活費の月平均 × 何か月分、を目標額にします。今月と、明細の無い月は平均に入れません。" +
                    "振替やカードの引き落としは、下の設定で生活費から外せます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = averageMonths, onValueChange = { averageMonths = it },
                    label = { Text("平均を取る月数") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = coverMonths, onValueChange = { coverMonths = it },
                    label = { Text("何か月分") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = onOpenSpendingRules) { Text("生活費から除く出金を設定") }
        }
        OutlinedTextField(
            value = due, onValueChange = { due = it },
            label = { Text("期日(任意。2030-04-01)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = resetsYearly, onCheckedChange = { resetsYearly = it })
            Column {
                Text("毎年1月にリセットする枠")
                Text(
                    "ふるさと納税の上限やNISAの年間枠など。進捗は今年使った額で数え、" +
                        "詳細画面の「使った分を足す」で記録します。系列を選ばなければ、目標の名前で記録します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("進捗を測る系列", style = MaterialTheme.typography.titleSmall)
        Text(
            "選んだ系列の最新値を、目標額に対する進捗として出します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(metricKey == null, onClick = { metricKey = null }, label = { Text("なし") })
            metricKeys.forEach { key ->
                FilterChip(metricKey == key, onClick = { metricKey = key }, label = { Text(key) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(
                enabled = !saving,
                onClick = {
                    val auto = if (useAuto) averageMonths to coverMonths else null
                    when (val input = GoalForm.parse(existing, name, target, metricKey, due, auto = auto, resetsYearly = resetsYearly)) {
                        is GoalForm.Result.Invalid -> message = input.message
                        is GoalForm.Result.Ok -> {
                            message = "保存中..."
                            onSave(input.goal) { error -> if (error == null) onBack() else message = error }
                        }
                    }
                },
            ) { Text("保存") }

            if (existing != null) {
                // 誤って消さないよう、2回押させる
                OutlinedButton(
                    enabled = !saving,
                    onClick = {
                        if (!confirmDelete) {
                            confirmDelete = true
                            message = "もう一度押すと削除します"
                        } else {
                            message = "削除中..."
                            onDelete(existing.id) { error -> if (error == null) onBack() else message = error }
                        }
                    },
                ) { Text(if (confirmDelete) "本当に削除" else "削除") }
            }
        }

        message?.let {
            val busy = it.endsWith("中...")
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}
