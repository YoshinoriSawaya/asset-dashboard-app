package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.Repeat

/**
 * リマインダーの一覧(E05-07)。トップには件数と次の1件だけを出し、全部はここで見る。
 * 期日の近い順(過ぎたものが先頭)。タップで詳細(済みにする・編集)へ。
 */
@Composable
fun ReminderListScreen(
    reminders: List<ItemOverview.Reminder>,
    onOpenItem: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoney.current
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("リマインダー(全${reminders.size}件)", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onAdd) { Text("+ リマインダーを追加") }
            HorizontalDivider()
        }
        if (reminders.isEmpty()) {
            item { Text("リマインダーはまだありません", modifier = Modifier.padding(vertical = 16.dp)) }
        }
        items(reminders.sortedWith(compareBy({ it.daysLeft }, { it.item.name })), key = { it.item.id }) { overview ->
            val r = overview.item
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenItem(r.id) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(r.dueDate)
                            append(
                                when {
                                    r.repeat == Repeat.MONTHLY -> " 毎月"
                                    r.repeat == Repeat.YEARLY && r.repeatYears > 1 -> " ${r.repeatYears}年ごと"
                                    r.repeat == Repeat.YEARLY -> " 毎年"
                                    else -> ""
                                },
                            )
                            r.amountYen?.let { append(" / ").append(money.amount(it)) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    Formatters.daysLeft(overview.daysLeft),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overview.daysLeft < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider()
        }
    }
}
