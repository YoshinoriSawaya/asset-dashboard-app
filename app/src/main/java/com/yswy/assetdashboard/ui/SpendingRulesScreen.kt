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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.drive.SpendingRules

/**
 * 生活費から除く出金の決まり(E07-06)。摘要にこの言葉を含む出金を除く。
 * 保存先はDriveの settings/spending_rules.json。
 *
 * ## 登録を楽にする(E07-20)
 * - 手元の出金を摘要ごとにまとめ、件数・合計・最後に使った日・カードか銀行かを出す。並びは件数・金額・最近で選ぶ
 * - 行をタップすると、その摘要を除く・除かないに切り替わる(入力欄を通さない)。部分の言葉は入力欄から足す
 * - 保存ボタンは画面の下に固定する(リストの長さで位置が動かない)
 *
 * 摘要はこの端末の画面に出すだけで、どこにも書き出さない。
 *
 * @param load Driveから今の言葉を読む。読めなければ理由
 * @param withdrawals 手元の出金を摘要ごとにまとめたもの
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingRulesScreen(
    load: suspend () -> Result<List<String>>,
    withdrawals: suspend () -> List<SpendingRules.Candidate>,
    saving: Boolean,
    onSave: (List<String>, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keywords = remember { mutableStateListOf<String>() }
    // 開いたときに保存済みだった言葉。キャッシュで外れている理由(言葉か、カードの引き落としか)を見分けるのに使う
    var savedKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<SpendingRules.Candidate>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var order by rememberSaveable { mutableStateOf(SpendingRules.Order.COUNT) }
    val money = LocalMoney.current

    LaunchedEffect(attempt) {
        candidates = withdrawals()
        load().fold(
            onSuccess = { keywords.clear(); keywords.addAll(it); savedKeywords = it; loaded = true; loadError = null },
            onFailure = { loadError = it.message },
        )
    }

    val excluded = candidates.filter { SpendingRules.matches(it.description, keywords) }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item {
                TextButton(onClick = onBack) { Text("← 戻る") }
                Text("生活費から除く出金", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "摘要にこの言葉を含む出金は、生活費(生活防衛資金の目標額の計算など)に数えません。" +
                        "下の出金をタップすると、除く・除かないが切り替わります。全角と半角の違いは気にしなくてよいです。",
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
                Text("除く言葉(タップで外す)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    if (keywords.isEmpty()) Text("まだありません", style = MaterialTheme.typography.bodySmall)
                    keywords.forEach { word ->
                        InputChip(selected = true, onClick = { keywords.remove(word) }, label = { Text("$word ×") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        label = { Text("部分の言葉を足す(例: 投信積立)") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        val word = input.trim()
                        if (word.isNotEmpty() && word !in keywords) keywords.add(word)
                        input = ""
                    }) { Text("追加") }
                }
                Text("出金の摘要", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    SpendingRules.Order.entries.forEach { o ->
                        FilterChip(order == o, onClick = { order = o }, label = { Text(o.label) })
                    }
                }
                HorizontalDivider()
            }

            items(SpendingRules.sorted(candidates, order), key = { it.description }) { c ->
                val byWord = SpendingRules.matches(c.description, keywords)
                // 保存済みの言葉でも当たらないのに、キャッシュでは外れている = カードの引き落としの突き合わせ(E01-14)
                val auto = !byWord && c.excludedNow && !SpendingRules.matches(c.description, savedKeywords)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            val next = SpendingRules.toggle(keywords.toList(), c.description)
                            keywords.clear(); keywords.addAll(next)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${if (c.fromCard) "カード" else "銀行"} ・ ${c.count}件 ・ 最後 ${c.lastDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.amount(c.totalYen), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                        when {
                            byWord -> Text("除く", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            auto -> Text("自動で除外(カードの引き落とし)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        // 下に固定。リストが伸び縮みしても位置が変わらない(E07-20)
        if (loaded) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "除く出金 ${excluded.sumOf { it.count }}件 ・ ${money.amount(excluded.sumOf { it.totalYen })}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = !saving,
                            onClick = {
                                message = "保存中..."
                                onSave(keywords.toList()) { error -> if (error == null) onBack() else message = error }
                            },
                        ) { Text("保存") }
                    }
                    message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
