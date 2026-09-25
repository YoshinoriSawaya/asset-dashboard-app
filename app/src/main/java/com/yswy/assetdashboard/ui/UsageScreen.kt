package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.yswy.assetdashboard.data.ItemOverview

/**
 * 毎年リセットする枠に、使った分を足す(E07-09)。ふるさと納税で寄付したときなど。
 * 今回の額だけを入れれば、今年の累計に足して今日の点として保存する
 * (Driveの corrections に手入力として残る)。
 */
@Composable
fun UsageScreen(
    goal: ItemOverview.Goal?,
    saving: Boolean,
    onSave: (String, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        if (goal == null) {
            Text("この目標は見つかりません")
            return@Column
        }
        Text("${goal.item.name}: 使った分を足す", style = MaterialTheme.typography.headlineSmall)
        val used = goal.currentYen ?: 0L
        val limit = goal.targetYen
        Text("今年使った額 ${Formatters.yen(used)}" + (limit?.let { " / 枠 ${Formatters.yen(it)}" } ?: ""))
        limit?.let {
            Text(
                "残り ${Formatters.yen((it - used).coerceAtLeast(0))}",
                color = if (used > it) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text("今回の額(円)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "今日の日付で、今年の累計に足して記録します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(enabled = !saving, onClick = {
            message = "保存中..."
            onSave(amount) { error -> if (error == null) onBack() else message = error }
        }) { Text("足す") }
        message?.let {
            Text(it, color = if (it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }
    }
}
