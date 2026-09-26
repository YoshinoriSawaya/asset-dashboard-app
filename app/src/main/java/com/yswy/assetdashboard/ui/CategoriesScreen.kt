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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Category
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.CategorySettings
import com.yswy.assetdashboard.drive.SpendingRules

/**
 * 明細のカテゴリ分け(E07-21)。前の「生活費から除く出金」(E07-06・E07-20)を置き換えたもの。
 * 保存先はDriveの settings/categories.json。
 *
 * - カテゴリは自分で足せる(食費・遊び代・家具など)。種類(生活費・遊び代・大型出費・振替・積立投資)で計算での扱いが決まる
 * - 明細を摘要ごとにまとめ、件数・合計・最後に使った日を出す。行をタップしてカテゴリを選ぶと、
 *   同じ摘要の明細が(過去もこれからも)そのカテゴリになる。部分の言葉で広く付けることもできる
 * - 「カテゴリなしの出金だけ」に絞れる(E07-23)。カテゴリの無い出金は生活費として数えるので、
 *   振替・大型出費・積立投資が混ざっていないかを見直す
 * - 保存ボタンは画面の下に固定する
 *
 * 摘要はこの端末の画面に出すだけで、どこにも書き出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    load: suspend () -> Result<CategorySettings>,
    candidatesOf: suspend () -> List<SpendingRules.Candidate>,
    saving: Boolean,
    onSave: (CategorySettings, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** CSVからまとめて取り込む(E07-22) */
    onOpenImport: () -> Unit = {},
) {
    var settings by remember { mutableStateOf<CategorySettings?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<SpendingRules.Candidate>>(emptyList()) }
    var attempt by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var order by rememberSaveable { mutableStateOf(SpendingRules.Order.COUNT) }
    var onlyUncategorized by rememberSaveable { mutableStateOf(false) }
    // カテゴリを選ぶ小窓を開いている摘要
    var choosing by remember { mutableStateOf<String?>(null) }
    val money = LocalMoney.current

    LaunchedEffect(attempt) {
        candidates = candidatesOf()
        load().fold(onSuccess = { settings = it; loadError = null }, onFailure = { loadError = it.message })
    }

    val current = settings
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item {
                TextButton(onClick = onBack) { Text("← 戻る") }
                Text("明細のカテゴリ", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onOpenImport) { Text("CSV(settings/${CategoryImport.FILE_NAME})からまとめて取り込む") }
                Text(
                    "明細の摘要にカテゴリを付けます。同じ摘要の明細は、過去もこれからも同じカテゴリになります。" +
                        "カテゴリの種類で、生活費(生活防衛資金の元)・消費(積立投資の目安)に入るかが決まります。" +
                        "カテゴリの無い明細は生活費として数えます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (current == null) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(loadError?.let { "読めませんでした: $it" } ?: "Driveから読み込み中...")
                        if (loadError != null) TextButton(onClick = { attempt++ }) { Text("もう一度") }
                    }
                }
                return@LazyColumn
            }

            item { CategoryEditor(current) { settings = it } }

            item {
                Text("明細の摘要(タップでカテゴリを選ぶ)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    SpendingRules.Order.entries.forEach { o ->
                        FilterChip(order == o, onClick = { order = o }, label = { Text(o.label) })
                    }
                }
                val uncategorized = SpendingRules.uncategorizedSpending(candidates) { current.categoryOf(it) != null }
                FilterChip(
                    onlyUncategorized,
                    onClick = { onlyUncategorized = !onlyUncategorized },
                    label = { Text("カテゴリなしの出金だけ(${uncategorized.size})") },
                )
                if (onlyUncategorized) {
                    Text(
                        "生活費として数えている出金です。振替・大型出費(積立で準備するもの)・積立投資が混ざっていれば" +
                            "付け直します。付けると一覧から消えます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }

            val shown = if (onlyUncategorized) {
                SpendingRules.uncategorizedSpending(candidates) { current.categoryOf(it) != null }
            } else {
                candidates
            }
            items(SpendingRules.sorted(shown, order), key = { it.description }) { c ->
                val category = current.categoryOf(c.description)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !c.cardPaymentOnly) { choosing = c.description }
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
                        if (c.totalYen > 0) Text("出 ${money.amount(c.totalYen)}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        if (c.depositYen > 0) Text("入 ${money.amount(c.depositYen)}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        Text(
                            when {
                                c.cardPaymentOnly -> "カードの引き落とし(内訳はカードの明細)"
                                category != null -> "${category.name}(${category.kind.label})"
                                else -> "カテゴリなし(生活費)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (category != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }

        // 下に固定。リストが伸び縮みしても位置が変わらない(E07-20)
        if (current != null) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val tagged = candidates.filter { current.categoryOf(it.description) != null }
                        Text(
                            "カテゴリ付き ${tagged.size} / ${candidates.size}種類の摘要",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = !saving,
                            onClick = {
                                message = "保存中..."
                                onSave(current) { error -> if (error == null) onBack() else message = error }
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

    // カテゴリを選ぶ小窓
    val description = choosing
    if (current != null && description != null) {
        val now = current.categoryOf(description)
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("カテゴリを選ぶ") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(description, style = MaterialTheme.typography.bodySmall)
                    current.categories.forEach { cat ->
                        TextButton(onClick = { settings = current.assign(description, cat.name); choosing = null }) {
                            Text((if (now?.name == cat.name) "● " else "") + "${cat.name}(${cat.kind.label})")
                        }
                    }
                    TextButton(onClick = { settings = current.assign(description, null); choosing = null }) {
                        Text("カテゴリなし(生活費)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("閉じる") } },
        )
    }
}

/** カテゴリの一覧・追加・削除と、部分の言葉での決まり。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditor(settings: CategorySettings, onChange: (CategorySettings) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CategoryKind.LIVING) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var keywordCategory by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("カテゴリ(タップで消す)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.categories.forEach { cat ->
                InputChip(selected = true, onClick = { onChange(settings.withoutCategory(cat.name)) }, label = { Text("${cat.name}(${cat.kind.label}) ×") })
            }
        }
        Text(
            CategoryKind.entries.joinToString("\n") { "・${it.label}: ${it.note}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("新しいカテゴリ") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val n = name.trim()
                if (n.isNotEmpty()) onChange(settings.withCategory(Category(n, kind)))
                name = ""
            }) { Text("追加") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryKind.entries.forEach { k -> FilterChip(kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
        }

        Text("部分の言葉でまとめて付ける(例: 投信積立)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        // 決まりは多くなりやすく(前の除く言葉を引き継ぐと数十件)、並べると下の明細が遠くなるので、畳んでおく
        var showRules by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showRules = !showRules }) {
            Text(if (showRules) "決まりを畳む" else "決まり(${settings.rules.size}件)を表示")
        }
        if (showRules) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.rules.forEach { r ->
                    InputChip(selected = true, onClick = { onChange(settings.assign(r.keyword, null)) }, label = { Text("${r.keyword} → ${r.category} ×") })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("言葉") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(
                enabled = keyword.isNotBlank() && keywordCategory != null,
                onClick = { onChange(settings.assign(keyword.trim(), keywordCategory)); keyword = "" },
            ) { Text("追加") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.categories.forEach { cat -> FilterChip(keywordCategory == cat.name, onClick = { keywordCategory = cat.name }, label = { Text(cat.name) }) }
        }
    }
}
