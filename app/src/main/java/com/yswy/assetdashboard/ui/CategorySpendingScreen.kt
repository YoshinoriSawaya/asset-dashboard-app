package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.CategorySpending
import com.yswy.assetdashboard.data.Summary
import java.time.YearMonth

/**
 * カテゴリ別の支出(E07-25)。月ごとに、カテゴリごとに使った額・その月の中の割合・直近の平均との差を並べる。
 * 平均よりはっきり多いカテゴリには印を付ける。行を押すと、その月・そのカテゴリの明細が開く。
 *
 * 摘要はこの端末の画面に出すだけで、どこにも書き出さない。
 */
@Composable
fun CategorySpendingScreen(
    load: suspend () -> List<BankTransactionEntity>,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transactions by produceState<List<BankTransactionEntity>?>(null) { value = load() }
    val money = LocalMoney.current
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
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false) {
    val style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text(value, style = style)
    }
}
