package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.ExpenseCalendar

/**
 * 向こう数年の大型出費(E09-04)。年ごとに、月・名前・見込み額を並べる。
 * 見込み額を入れたリマインダーと、期日のある目標から作る。
 */
@Composable
fun ExpenseCalendarScreen(
    entries: List<ExpenseCalendar.Entry>,
    onOpenItem: (ExpenseCalendar.Entry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totals = ExpenseCalendar.totalsByYear(entries)
    val money = LocalMoney.current
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("大型出費の予定", style = MaterialTheme.typography.headlineSmall)
            Text(
                "向こう${ExpenseCalendar.DEFAULT_YEARS}年。見込み額を入れたリマインダー(毎年のものは各年に)と、" +
                    "期日のある目標を載せます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entries.isEmpty()) {
                Text(
                    "まだありません。リマインダーに見込み額を入れるか、目標に期日を入れてください。",
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        entries.groupBy { it.date.year }.forEach { (year, list) ->
            item(key = "y$year") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                    Text("${year}年", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("計 ${money.amount(totals[year] ?: 0)}", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
            items(list, key = { "${it.date}-${it.name}-${it.source}" }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenItem(entry) }.padding(vertical = 8.dp),
                ) {
                    Text("${entry.date.monthValue}月", modifier = Modifier.weight(0.6f))
                    Text(
                        entry.name + if (entry.source == ExpenseCalendar.Source.GOAL) "(目標)" else "",
                        modifier = Modifier.weight(2f),
                    )
                    Text(money.amount(entry.amountYen), textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
                }
            }
        }
    }
}
