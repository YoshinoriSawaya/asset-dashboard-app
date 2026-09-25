package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.drive.SpendingRules

/**
 * 生活費から除く出金の決まり(E07-06)。摘要にこの言葉を含む出金を除く。
 * 保存先はDriveの settings/spending_rules.json。
 *
 * 言葉を考えやすいよう、手元の出金の摘要を多い順に並べ、タップで入力欄に入れる。
 * 摘要はこの端末の画面に出すだけで、どこにも書き出さない。
 *
 * @param load Driveから今の言葉を読む。読めなければ理由
 * @param withdrawals 手元の出金の摘要と件数(多い順)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingRulesScreen(
    load: suspend () -> Result<List<String>>,
    withdrawals: suspend () -> List<Pair<String, Int>>,
    saving: Boolean,
    onSave: (List<String>, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keywords = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        candidates = withdrawals()
        load().fold(
            onSuccess = { keywords.clear(); keywords.addAll(it); loaded = true; loadError = null },
            onFailure = { loadError = it.message },
        )
    }

    val excludedCount = candidates.filter { (d, _) -> SpendingRules.matches(d, keywords) }.sumOf { it.second }
    val totalCount = candidates.sumOf { it.second }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("生活費から除く出金", style = MaterialTheme.typography.headlineSmall)
            Text(
                "摘要にこの言葉を含む出金は、生活費(生活防衛資金の目標額の計算など)に数えません。" +
                    "振替・カードの引き落としなどに。全角と半角の違いは気にしなくてよいです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!loaded) {
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(loadError?.let { "読めませんでした: $it" } ?: "Driveから読み込み中...")
                    if (loadError != null) TextButton(onClick = { attempt++ }) { Text("もう一度") }
                }
            }
            return@LazyColumn
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                if (keywords.isEmpty()) Text("まだありません", style = MaterialTheme.typography.bodySmall)
                keywords.forEach { word ->
                    InputChip(selected = true, onClick = { keywords.remove(word) }, label = { Text("$word ×") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text("除く言葉") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    val word = input.trim()
                    if (word.isNotEmpty() && word !in keywords) keywords.add(word)
                    input = ""
                }) { Text("追加") }
            }
            Text(
                "今の言葉で除く出金: ${excludedCount}件 / 全${totalCount}件",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                enabled = !saving,
                onClick = {
                    message = "保存中..."
                    onSave(keywords.toList()) { error -> if (error == null) onBack() else message = error }
                },
            ) { Text("保存") }
            message?.let {
                Text(
                    it,
                    color = if (it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "出金の摘要(多い順。タップで入力欄へ)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }

        items(candidates, key = { it.first }) { (description, count) ->
            val excluded = SpendingRules.matches(description, keywords)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { input = description }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    (if (excluded) "除く・" else "") + "${count}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (excluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
