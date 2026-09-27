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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.CategorySpending
import com.yswy.assetdashboard.data.Summary
import com.yswy.assetdashboard.ui.chart.ColorDot
import com.yswy.assetdashboard.ui.chart.GoalColors
import com.yswy.assetdashboard.ui.chart.StackedBarChart
import java.time.YearMonth

/**
 * カテゴリ別の支出(E07-25)。月ごとに、カテゴリごとに使った額・その月の中の割合・直近の平均との差を並べる。
 * 平均よりはっきり多いカテゴリには印を付ける。行を押すと、その月・そのカテゴリの明細が開く。
 * 上に直近12か月の推移を積み上げ棒で出す(E07-27)。棒を押すとその月に切り替わる。
 *
 * 摘要はこの端末の画面に出すだけで、どこにも書き出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySpendingScreen(
    load: suspend () -> List<BankTransactionEntity>,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** カテゴリの月の予算(E07-29)。端末の控え */
    loadBudgets: () -> Map<String, Long> = { emptyMap() },
    /** 予算を保存する。(カテゴリ, 額かnull, 結果) */
    onSaveBudget: (String, Long?, (String?) -> Unit) -> Unit = { _, _, _ -> },
    saving: Boolean = false,
) {
    val transactions by produceState<List<BankTransactionEntity>?>(null) { value = load() }
    val money = LocalMoney.current
    var budgets by remember { mutableStateOf(loadBudgets()) }
    // 予算を決める小窓を開いているカテゴリと、その行
    var editing by remember { mutableStateOf<CategorySpending.Row?>(null) }
    var budgetMessage by remember { mutableStateOf<String?>(null) }
    // 何番目の月を見ているか(0が最新)
    var index by rememberSaveable { mutableStateOf(0) }
    // 明細を開いているカテゴリ。カテゴリなしは空文字で持つ
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("カテゴリ別の支出", style = MaterialTheme.typography.headlineSmall)
        }
        val all = transactions
        if (all == null) {
            item { Text("読み込み中...") }
            return@LazyColumn
        }
        val months = CategorySpending.months(all)
        if (months.isEmpty()) {
            item { Text("明細がまだありません") }
            return@LazyColumn
        }
        val month = months[index.coerceIn(months.indices)]
        val spending = CategorySpending.of(all, month)

        // 直近12か月の推移(E07-27)。期間は最新の月までで固定し、選んでいる月の棒を囲む。棒を押すとその月へ
        item {
            val trend = CategorySpending.trend(all, months.first())
            val palette = trend.categories.indices.map { GoalColors.of(it) } + GoalColors.of(null)
            StackedBarChart(
                labels = trend.months.map { "${it.monthValue}月" },
                stacks = trend.values,
                colors = palette,
                selected = trend.months.indexOf(month).takeIf { it >= 0 },
                onSelect = { i -> index = months.indexOf(trend.months[i]); open = null },
                axisLabel = money::barAxis,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                trend.categories.forEachIndexed { i, c -> Legend(palette[i], c ?: "カテゴリなし") }
                if (trend.hasOthers) Legend(palette.last(), "その他")
            }
            Label("棒を押すとその月に切り替わります")
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(enabled = index < months.lastIndex, onClick = { index++; open = null }) { Text("← 前の月") }
                Text(
                    "${month.year}年${month.monthValue}月" + if (month == YearMonth.now()) "(途中)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = index > 0, onClick = { index--; open = null }) { Text("次の月 →") }
            }
            Line("使った額(振替を除く)", money.amount(spending.totalYen), strong = true)
            Line("うち消費(生活費 + 遊び代)", money.amount(spending.consumptionYen))
            Label(
                if (spending.averageMonths > 0) {
                    "右の増減は、前の${spending.averageMonths}か月の平均との差です。平均よりはっきり多いものに ▲ を付けます。"
                } else {
                    "前の月の明細が無いので、平均とは比べません。"
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (spending.rows.isEmpty()) {
            item { Text("この月は振替のほかに出金がありません") }
        }
        items(spending.rows, key = { it.category ?: "" }) { row ->
            val key = row.category ?: ""
            Column(modifier = Modifier.fillMaxWidth().clickable { open = if (open == key) null else key }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (if (row.high) "▲ " else "") + (row.category ?: "カテゴリなし(生活費)"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (row.high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${row.kind?.label ?: "生活費"} ・ ${row.count}件 ・ ${money.share(row.yen.toDouble() / spending.totalYen.coerceAtLeast(1))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 予算(E07-29)。使った割合。超えたら赤
                        row.category?.let { budgets[it] }?.let { budget ->
                            Text(
                                "予算の${money.percent(row.yen.toDouble() / budget)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (row.yen > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.amount(row.yen), style = MaterialTheme.typography.bodyMedium)
                        row.averageYen?.let { avg ->
                            Text(
                                if (avg == 0L) "前の月は無し" else money.change(row.yen - avg, avg),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (open == key) {
                    // 予算を決める(E07-29)。カテゴリの無い明細には決められない
                    row.category?.let { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                budgets[name]?.let { "予算 月${money.amount(it)}" } ?: "予算なし",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(enabled = !saving, onClick = { budgetMessage = null; editing = row }) {
                                Text(if (budgets[name] == null) "予算を決める" else "予算を変える")
                            }
                        }
                    }
                    CategorySpending.transactions(all, month, row.category).forEach { t ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp)) {
                            Text("${t.date.dayOfMonth}日", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                            Text(t.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(money.amount(Summary.usedYen(t)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        item {
            TextButton(onClick = onOpenCategories, modifier = Modifier.padding(top = 8.dp)) { Text("明細のカテゴリを見直す") }
        }
    }

    // 予算を決める小窓(E07-29)
    val row = editing
    val name = row?.category
    if (row != null && name != null) {
        var text by remember(name) { mutableStateOf(budgets[name]?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("「$name」の月の予算") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.averageYen?.let { Label("前の月までの平均 月${money.amount(it)}") }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("円(空にすると予算をやめる)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Label("前の月の結果が予算を超えていたら、朝の通知で知らせます。Driveの settings に保存します。")
                    budgetMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    val yen = BudgetInput.parse(text)
                    if (yen is BudgetInput.Result.Invalid) {
                        budgetMessage = yen.message
                    } else {
                        budgetMessage = "保存中..."
                        val value = (yen as BudgetInput.Result.Ok).yen
                        onSaveBudget(name, value) { error ->
                            if (error == null) {
                                budgets = loadBudgets()
                                editing = null
                            } else {
                                budgetMessage = error
                            }
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("閉じる") } },
        )
    }
}

/** 予算の入力(E07-29)。空なら予算をやめる。 */
object BudgetInput {
    sealed interface Result {
        data class Ok(val yen: Long?) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(text: String): Result {
        val t = text.replace(",", "").replace("円", "").trim()
        if (t.isEmpty()) return Result.Ok(null)
        val yen = t.toLongOrNull()?.takeIf { it > 0 } ?: return Result.Invalid("1円以上の数で入れてください(例: 30000)")
        return Result.Ok(yen)
    }
}

@Composable
private fun Legend(color: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, size = 10.dp)
        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false) {
    val style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text(value, style = style)
    }
}
