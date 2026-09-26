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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat

/**
 * リマインダーの追加・編集・削除(E05-05/06)。保存先はDriveの settings/items.json。
 * 期日の7日前と当日に、端末が通知する(E05)。
 *
 * @param funds 大型出費の積立の目標(E07-15)。見込み額の積立先に選べる
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditScreen(
    existing: Item.Reminder?,
    saving: Boolean,
    onSave: (Item.Reminder, (String?) -> Unit) -> Unit,
    onDelete: (String, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    funds: List<Item.Goal> = emptyList(),
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var due by rememberSaveable { mutableStateOf(existing?.dueDate?.let(DateInput::format).orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.amountYen?.toString().orEmpty()) }
    var repeat by rememberSaveable { mutableStateOf(existing?.repeat ?: Repeat.YEARLY) }
    var everyYears by rememberSaveable { mutableStateOf(existing?.repeatYears?.takeIf { it > 1 }?.toString().orEmpty()) }
    var fundId by rememberSaveable { mutableStateOf(existing?.fundId) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text(if (existing == null) "リマインダーを追加" else "リマインダーを編集", style = MaterialTheme.typography.headlineSmall)
        Text(
            "期日の7日前と当日に通知します。Driveの settings に保存します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("名前(例: 車の任意保険の更新)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = due, onValueChange = { due = it },
            label = { Text("期日(例: ${DateInput.EXAMPLE})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text("見込み額(任意。大型出費の予定に載る)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("繰り返し", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Repeat.YEARLY to "毎年", Repeat.MONTHLY to "毎月", Repeat.NONE to "しない").forEach { (value, label) ->
                FilterChip(repeat == value, onClick = { repeat = value }, label = { Text(label) })
            }
        }
        if (repeat == Repeat.YEARLY) {
            OutlinedTextField(
                value = everyYears, onValueChange = { everyYears = it },
                label = { Text("何年ごと(空なら毎年。車検は2、洗濯機は10など)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 積立先(E07-15)。積立の目標があるときだけ出す
        if (funds.isNotEmpty() || fundId != null) {
            Text("積立先", style = MaterialTheme.typography.titleSmall)
            Text(
                "見込み額を、大型出費の積立の目標で準備します。その目標の目標額と月々の積立額に入ります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(fundId == null, onClick = { fundId = null }, label = { Text("なし") })
                funds.forEach { goal ->
                    FilterChip(fundId == goal.id, onClick = { fundId = goal.id }, label = { Text(goal.name) })
                }
                // 積立先の目標が消えていても、選んだままなのが分かるように出す
                if (fundId != null && funds.none { it.id == fundId }) {
                    FilterChip(true, onClick = { fundId = null }, label = { Text("(見つからない目標)") })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(enabled = !saving, onClick = {
                when (val input = ReminderForm.parse(existing, name, due, repeat, amount = amount, everyYears = everyYears, fundId = fundId)) {
                    is ReminderForm.Result.Invalid -> message = input.message
                    is ReminderForm.Result.Ok -> {
                        message = "保存中..."
                        onSave(input.reminder) { error -> if (error == null) onBack() else message = error }
                    }
                }
            }) { Text("保存") }
            if (existing != null) {
                OutlinedButton(enabled = !saving, onClick = {
                    if (!confirmDelete) {
                        confirmDelete = true
                        message = "もう一度押すと削除します"
                    } else {
                        message = "削除中..."
                        onDelete(existing.id) { error -> if (error == null) onBack() else message = error }
                    }
                }) { Text(if (confirmDelete) "本当に削除" else "削除") }
            }
        }
        message?.let {
            Text(it, color = if (it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }
    }
}
