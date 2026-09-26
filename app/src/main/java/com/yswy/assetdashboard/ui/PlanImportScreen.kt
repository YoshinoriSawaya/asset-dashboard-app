package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat

/**
 * 大型出費の予定をまとめて取り込む(E07-16)。Driveの `settings/plan.csv` を読んで、
 * 登録する中身を先に見せ、「登録する」で settings/items.json に足す。
 */
@Composable
fun PlanImportScreen(
    load: suspend () -> Result<DashboardViewModel.PlanPreview>,
    saving: Boolean,
    onImport: (PlanImport.Plan, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 読み直すときに増やす
    var reload by remember { mutableIntStateOf(0) }
    val result by produceState<Result<DashboardViewModel.PlanPreview>?>(null, reload) { value = null; value = load() }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("大型出費の予定を取り込む", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Driveの 資産アプリ/settings/${PlanImport.FILE_NAME} を読み、見込み額つきのリマインダーとして登録します。" +
                "列は「名前,次の時期,何年ごと,見込み額,積立先」。何年ごとは空なら毎年、0なら一度だけ。" +
                "同じ名前のリマインダーは置き換えます。取り込んだあとはアプリで編集してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val current = result
        when {
            current == null -> Text("Driveから読んでいます...")
            current.isFailure -> {
                Text("読めませんでした: ${current.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reload++ }) { Text("もう一度読む") }
            }
            else -> Preview(current.getOrThrow(), saving, message) { plan ->
                message = "登録中..."
                onImport(plan) { error ->
                    message = error ?: "登録しました(新しく ${plan.added.size}件、置き換え ${plan.replaced.size}件)"
                }
            }
        }
    }
}

@Composable
private fun Preview(
    preview: DashboardViewModel.PlanPreview,
    saving: Boolean,
    message: String?,
    onImport: (PlanImport.Plan) -> Unit,
) {
    val parsed = preview.parsed
    val plan = preview.plan
    if (parsed == null || plan == null) {
        Text("${PlanImport.FILE_NAME} がDriveの settings にありません。", color = MaterialTheme.colorScheme.error)
        return
    }
    val money = LocalMoney.current
    val fundNames = plan.fundNames

    Text(
        "新しく ${plan.added.size}件 / 同じ名前を置き換え ${plan.replaced.size}件 / 読めない行 ${parsed.problems.size}件",
        style = MaterialTheme.typography.titleMedium,
    )
    if (plan.newFunds.isNotEmpty()) {
        Text("新しく作る積立の目標", style = MaterialTheme.typography.titleSmall)
        plan.newFunds.forEach { Label("${it.name}(5年でならす・物価上昇0%。測る系列・率は目標の編集で決める)") }
    }
    PlanList("新しく登録", plan.added, fundNames, money)
    PlanList("置き換える", plan.replaced, fundNames, money)
    if (parsed.problems.isNotEmpty()) {
        Text("読めない行(登録しない)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        parsed.problems.forEach { Label("${it.lineNumber}行目: ${it.reason}") }
    }

    Button(
        enabled = !saving && plan.items.isNotEmpty(),
        onClick = { onImport(plan) },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("登録する") }
    message?.let {
        Text(it, color = if (it.startsWith("登録しました") || it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PlanList(title: String, reminders: List<Item.Reminder>, fundNames: Map<String, String>, money: MoneyFormat) {
    if (reminders.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    reminders.forEach { r ->
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.bodyMedium)
                Label(
                    buildString {
                        append(r.dueDate)
                        append(
                            when {
                                r.repeat == Repeat.NONE -> " 一度だけ"
                                r.repeatYears > 1 -> " ${r.repeatYears}年ごと"
                                else -> " 毎年"
                            },
                        )
                        r.fundId?.let { append(" / 積立: ${fundNames[it] ?: "(既存の積立)"}") }
                    },
                )
            }
            Text(r.amountYen?.let(money::amount).orEmpty(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
}
