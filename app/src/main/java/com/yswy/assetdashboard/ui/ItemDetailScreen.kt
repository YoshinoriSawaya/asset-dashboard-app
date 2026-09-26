package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.FundOutlook
import com.yswy.assetdashboard.data.Allocation
import com.yswy.assetdashboard.data.Drawdown
import com.yswy.assetdashboard.data.GoalForecast
import com.yswy.assetdashboard.data.RampUp
import com.yswy.assetdashboard.data.RecoveryPlan
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemDetail
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.MetricChange
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.ui.chart.ChangeBarChart
import com.yswy.assetdashboard.ui.chart.LineChart
import com.yswy.assetdashboard.ui.chart.ColorDot
import com.yswy.assetdashboard.ui.chart.GoalColors
import com.yswy.assetdashboard.ui.chart.StackedChart
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * 項目の詳細画面(E03-02)。種類ごとに中身を切り替える共通テンプレート。
 *
 * Metricは推移の折れ線と月ごとの増減の棒(E03-06)を、月次の表の上に出す。
 */
@Composable
fun ItemDetailScreen(
    detail: ItemDetail?,
    loading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCorrect: (String) -> Unit = {},
    onDeleteCorrection: (String, LocalDate, (String?) -> Unit) -> Unit = { _, _, _ -> },
    busy: Boolean = false,
    onEditGoal: (String) -> Unit = {},
    onEditMetric: (String) -> Unit = {},
    onAddUsage: (String) -> Unit = {},
    onEditReminder: (String) -> Unit = {},
    onCompleteReminder: (Item.Reminder, (String?) -> Unit) -> Unit = { _, _ -> },
    /** 目標を一覧で1つ上(true)・下(false)へ。配分の順番になる(E07-10) */
    onMoveGoal: (String, Boolean, (String?) -> Unit) -> Unit = { _, _, _ -> },
) {
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { TextButton(onClick = onBack) { Text("← 戻る") } }

        if (detail == null) {
            item {
                // 読み込み中か、同期で項目が消えたか
                Text(if (loading) "読み込み中..." else "この項目は見つかりません")
            }
            return@LazyColumn
        }

        item {
            Text(detail.overview.item.name, style = MaterialTheme.typography.headlineSmall)
            Spacer8()
        }

        when (detail) {
            is ItemDetail.Metric -> {
                metricHeader(detail)
                // 補正の入口は上に置く。月次の表の下だと、スクロールしないと見つからない
                item {
                    TextButton(onClick = { onEditMetric(detail.overview.item.metricKey) }, enabled = !busy) {
                        Text("名前・表示を変更")
                    }
                }
                correctionContent(detail, busy, message, onCorrect) { key, date ->
                    message = "取り消し中..."
                    onDeleteCorrection(key, date) { error -> message = error ?: "取り消しました" }
                }
                metricContent(detail)
            }
            is ItemDetail.Goal -> item {
                GoalContent(detail)
                ShareContent(detail, busy, message) { up ->
                    message = "保存中..."
                    onMoveGoal(detail.overview.item.id, up) { error -> message = error ?: "順番を変えました" }
                }
                if (detail.overview.item.resetsYearly) {
                    Button(onClick = { onAddUsage(detail.overview.item.id) }, enabled = !busy) { Text("使った分を足す") }
                }
                OutlinedButton(onClick = { onEditGoal(detail.overview.item.id) }, enabled = !busy) {
                    Text("編集・削除")
                }
            }
            is ItemDetail.Reminder -> item {
                ReminderContent(detail)
                val reminder = detail.overview.item
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(enabled = !busy, onClick = {
                        message = "保存中..."
                        onCompleteReminder(reminder) { error -> message = error ?: "済みにしました" }
                    }) { Text(if (reminder.repeat == Repeat.NONE) "済みにする(消す)" else "済みにする(次の期日へ)") }
                    OutlinedButton(onClick = { onEditReminder(reminder.id) }, enabled = !busy) { Text("編集・削除") }
                }
                message?.let { Label(it) }
            }
        }
    }
}

private fun LazyListScope.metricHeader(detail: ItemDetail.Metric) {
    val latest = detail.overview.latest
    item {
        val money = LocalMoney.current
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (latest == null) {
                Text("データなし", style = MaterialTheme.typography.titleLarge)
            } else {
                Text(money.amount(latest.valueYen), style = MaterialTheme.typography.titleLarge)
                Label("${latest.date}時点")
            }
            // 名前を変えていても、どのCSV列から来ているか分かるように出す
            if (detail.overview.item.name != detail.overview.item.metricKey) {
                Label("系列: ${detail.overview.item.metricKey}")
            }
            Label(
                buildString {
                    append("${detail.pointCount}点")
                    if (detail.correctedCount > 0) append(" / うち手動補正 ${detail.correctedCount}点")
                },
            )
        }
    }
}

private fun LazyListScope.metricContent(detail: ItemDetail.Metric) {
    item {
        val money = LocalMoney.current
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (detail.series.size >= 2) {
                Spacer8()
                val stack = detail.stack
                if (stack != null) {
                    // 複数の目標で分け合っていれば、配分で塗り分ける(E07-13)
                    StackedChart(stack, axisLabel = money::shareAxis)
                    StackLegend(stack)
                } else {
                    LineChart(detail.series.map { it.date to it.valueYen }, axisLabel = money::lineAxis)
                }
                Spacer8()
                Text("月ごとの増減", style = MaterialTheme.typography.titleMedium)
                // グラフは古い月から右へ。表(新しい月が先頭)とは逆順
                ChangeBarChart(
                    detail.monthly.reversed().map { "${it.period.start.monthValue}月" to it.changeYen },
                    axisLabel = money::barAxis,
                )
            }
            Spacer8()
            Text("月ごとの推移", style = MaterialTheme.typography.titleMedium)
            MonthlyRow("月", "最後の値", "増減", header = true)
            HorizontalDivider()
        }
    }
    items(detail.monthly, key = { it.period.start.toString() }) { change ->
        val money = LocalMoney.current
        MonthlyRow(
            month = YearMonth.from(change.period.start).toString(),
            value = change.closingYen?.let(money::amount) ?: "データなし",
            change = change.changeYen?.let { money.change(it, change.openingYen) } ?: "−",
        )
    }
}

/** 補正の入口と、手で直した・足した点の一覧(E03-04)。 */
private fun LazyListScope.correctionContent(
    detail: ItemDetail.Metric,
    busy: Boolean,
    message: String?,
    onCorrect: (String) -> Unit,
    onDelete: (String, LocalDate) -> Unit,
) {
    val key = detail.overview.item.metricKey
    val corrected = detail.series.filter { it.origin != MetricOrigin.CSV }.sortedByDescending { it.date }
    item {
        Spacer8()
        OutlinedButton(onClick = { onCorrect(key) }, enabled = !busy) { Text("値を補正・手入力") }
        message?.let { Label(it) }
    }
    items(corrected, key = { "corr-" + it.date }) { point ->
        val money = LocalMoney.current
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${point.date}  ${money.amount(point.valueYen)}", style = MaterialTheme.typography.bodyMedium)
                Label(if (point.origin == MetricOrigin.OVERRIDE) "CSVの値を補正" else "手入力")
            }
            TextButton(onClick = { onDelete(key, point.date) }, enabled = !busy) { Text("取り消す") }
        }
    }
}

@Composable
private fun MonthlyRow(month: String, value: String, change: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(month, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
        Text(change, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun GoalContent(detail: ItemDetail.Goal) {
    val goal = detail.overview.item
    val progress = detail.overview.progress
    val money = LocalMoney.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(progress?.let(money::percent) ?: "進捗不明", style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(
            progress = { (progress ?: 0.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        val yearly = goal.resetsYearly
        detail.overview.currentYen?.let { Label((if (yearly) "今年使った額 " else "現在 ") + money.amount(it)) }
        Label(detail.overview.targetYen?.let { (if (yearly) "枠 " else "目標 ") + money.amount(it) } ?: "目標 不明")
        // 支出から出した目標なら、どう計算したかを出す(E07-06)
        goal.autoTarget?.let { rule ->
            val auto = detail.overview.auto
            Label(
                if (auto?.monthlyAverageYen == null) {
                    "生活費の${rule.averageMonths}か月平均 × ${rule.coverMonths}か月(使える月の明細がまだ無い)"
                } else {
                    "生活費の平均 ${money.amount(auto.monthlyAverageYen)}(${auto.monthsUsed}か月分)× ${rule.coverMonths}か月" +
                        if (auto.monthsUsed < rule.averageMonths) "。明細が${rule.averageMonths}か月分そろうまでは少ない月で平均" else ""
                },
            )
        }
        FloorLabels(detail.overview)
        detail.remainingYen?.let {
            Label(
                when {
                    // 毎年の枠(E07-09)は「使い切った」、目標は「達成」
                    yearly -> if (it == 0L) "枠を使い切った" else "残りの枠 ${money.amount(it)}(${LocalDate.now().year}年末まで)"
                    it == 0L -> "達成済み"
                    else -> "あと ${money.amount(it)}"
                },
            )
        }
        val rampUp = detail.rampUp(LocalDate.now())
        RampUpLabels(goal, rampUp)
        detail.cover?.let { CoverLabels(it) }
        // 届いていなければ、月々いくらで何か月で届くか(E07-07)
        // 毎年の枠では「残り」は埋めるものではないので出さない。
        // 期日に向けて積み増す目標(E07-11)は、期日から出した月額があるので出さない
        // 下限を割っていれば、まず下限まで戻す案を先に出す(E07-12)
        if (Drawdown.stateOf(detail.overview) == Drawdown.FundState.BELOW_FLOOR) {
            RecoveryPlan.of(detail.overview.floorYen, detail.overview.currentYen)?.let { plan ->
                Text("まず下限まで戻すには", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                plan.options.forEach { option ->
                    Label("${option.months}か月で: 月々 ${money.amount(option.monthlyYen)}")
                }
            }
        }
        detail.recovery?.takeIf { it.isShort && !yearly && rampUp == null }?.let { plan ->
            Text("不足分を埋めるには", style = MaterialTheme.typography.titleSmall)
            plan.options.forEach { option ->
                Label("${option.months}か月で: 月々 ${money.amount(option.monthlyYen)}")
            }
        }
        goal.dueDate?.let { Label("期日 $it") }
        OutlookLabels(detail)
        ForecastLabels(detail)
        Label(goal.metricKey?.let { "進捗を測る系列: $it" } ?: "進捗を測る系列が未設定")
    }
}

/** 積み上げグラフの凡例。上の層(自由に使えるお金)から、最新の点の内訳を出す。 */
@Composable
private fun StackLegend(stack: Allocation.Stack) {
    val money = LocalMoney.current
    val total = stack.layers.sumOf { it.values.last() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        stack.layers.reversed().forEach { layer ->
            val value = layer.values.last()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorDot(GoalColors.of(layer.colorIndex))
                Text(
                    "${layer.name}  ${money.amount(value)}" +
                        (total.takeIf { it > 0 }?.let { "(${money.percent(value.toDouble() / it)})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Label("一覧で上の目標から順に、目標額まで割り当てています。過去の点にも今の目標額を当てています")
    }
}

/** 同じ系列を分け合っているときの内訳と、配分の順番(E07-10)。 */
@Composable
private fun ShareContent(detail: ItemDetail.Goal, busy: Boolean, message: String?, onMove: (Boolean) -> Unit) {
    val share = detail.overview.share ?: return
    val money = LocalMoney.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorDot(GoalColors.of(detail.colorIndex))
            Text("${share.metricKey}を${share.count}つの目標で分けています", style = MaterialTheme.typography.titleSmall)
        }
        Label("${share.metricKey} ${money.amount(share.seriesYen)}のうち、上から${share.position}番目。この目標に ${detail.overview.currentYen?.let(money::amount) ?: "不明"}")
        Label("上の目標に回した分 ${money.amount(share.aheadYen.coerceAtMost(share.seriesYen))} / 自由に使えるお金 ${money.amount(share.freeYen)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMove(true) }, enabled = !busy && share.position > 1) { Text("上へ") }
            OutlinedButton(onClick = { onMove(false) }, enabled = !busy && share.position < share.count) { Text("下へ") }
        }
        Label("上の目標から先に満たします。生活防衛資金を上に置くと、余った分が下の目標に回ります")
        message?.let { Label(it) }
    }
}

/** 生活防衛資金の下限と、今どこにいるか(E07-12)。 */
@Composable
private fun FloorLabels(overview: ItemOverview.Goal) {
    val months = overview.item.autoTarget?.floorMonths ?: return
    val money = LocalMoney.current
    Label("下限 " + (overview.floorYen?.let(money::amount) ?: "不明") + "(生活費の${months}か月分)")
    when (Drawdown.stateOf(overview)) {
        Drawdown.FundState.DRAWN -> Label("取り崩し中。下限まではまだ余裕があります")
        Drawdown.FundState.BELOW_FLOOR -> Text("下限を割っています", color = MaterialTheme.colorScheme.error)
        else -> Unit
    }
}

/** 期日に向けた目標の足りない分を、生活防衛資金で補えるか(E07-12)。 */
@Composable
private fun CoverLabels(cover: Drawdown.Cover) {
    val money = LocalMoney.current
    Text("足りなければ", style = MaterialTheme.typography.titleSmall)
    when (cover.fits) {
        true -> Label(
            "足りない ${money.amount(cover.shortfallYen)} は、${cover.fundName}から補えます" +
                "(下限まで ${money.amount(cover.availableYen ?: 0)} 取り崩せる)",
        )
        false -> Text(
            "足りない ${money.amount(cover.shortfallYen)} を${cover.fundName}から補うと、下限を割ります" +
                "(下限まで ${money.amount(cover.availableYen ?: 0)})",
            color = MaterialTheme.colorScheme.error,
        )
        null -> Label("${cover.fundName}に下限を決めると、どこまで取り崩して補えるかを出せます")
    }
}

/** 期日に向けた積み増し(E07-11)。 */
@Composable
private fun RampUpLabels(goal: Item.Goal, rampUp: RampUp?) {
    val months = goal.rampUpMonths ?: return
    val money = LocalMoney.current
    Text("期日に向けた積み増し", style = MaterialTheme.typography.titleSmall)
    Label("期日の${months}か月前から")
    when (rampUp) {
        is RampUp.Waiting -> Label("${rampUp.startMonth.year}年${rampUp.startMonth.monthValue}月から始める(それまでは何もしなくてよい)")
        is RampUp.Active -> Text(
            "積み増しの期間中: 期日まで月々 ${money.amount(rampUp.monthlyYen)}(残り${rampUp.monthsLeft}か月)",
            style = MaterialTheme.typography.bodyMedium,
        )
        RampUp.Overdue -> Text("期日を過ぎましたが届いていません", color = MaterialTheme.colorScheme.error)
        RampUp.Achieved -> Label("届いています")
        null -> Label("目標額か現在の値が分からず、月額を出せません")
    }
}

/** 生活防衛資金の見通し(E09-02)。生活費から目標額を出す目標にだけ出す。 */
@Composable
private fun OutlookLabels(detail: ItemDetail.Goal) {
    val outlook = FundOutlook.of(detail.overview) ?: return
    Text("見通し", style = MaterialTheme.typography.titleSmall)
    outlook.monthsCovered?.let {
        Label("収入が止まっても、生活費の約${"%.1f".format(it)}か月分")
    }
    outlook.monthsUntilBelowTarget?.let { months ->
        Text(
            if (months == 0) "このペースだと今月中に目標を割ります" else "このペースだと約${months}か月後に目標を割ります",
            style = MaterialTheme.typography.bodyMedium,
            color = if (months <= FundOutlook.WARN_MONTHS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 今のペースならいつ届くか(E09-01)。 */
@Composable
private fun ForecastLabels(detail: ItemDetail.Goal) {
    val forecast = detail.forecast ?: return
    val money = LocalMoney.current
    Text("今のペースなら", style = MaterialTheme.typography.titleSmall)
    when (forecast) {
        GoalForecast.Achieved -> Label("達成済み")
        GoalForecast.NotEnoughData -> Label("データが短く、まだペースを出せない(2か月分ほど要る)")
        is GoalForecast.NotReaching ->
            Label("届かない(この半年は月あたり ${money.change(forecast.monthlyPaceYen, detail.overview.currentYen)})")
        is GoalForecast.Reaching -> {
            Label("${forecast.month.year}年${forecast.month.monthValue}月ごろ達成(月あたり ${money.change(forecast.monthlyPaceYen, detail.overview.currentYen)})")
            when (forecast.onTime) {
                true -> Label("期日に間に合う見込み")
                false -> Label("期日には間に合わない見込み")
                null -> Unit
            }
        }
    }
    // 期日に間に合わないなら、間に合わせるための月額
    val goal = detail.overview.item
    val target = detail.overview.targetYen
    val current = detail.overview.currentYen
    val late = forecast is GoalForecast.NotReaching || (forecast is GoalForecast.Reaching && forecast.onTime == false)
    // 積み増しの時期を決めた目標(E07-11)は、その欄で月額を出している
    if (late && goal.rampUpMonths == null && goal.dueDate != null && target != null && current != null) {
        GoalForecast.monthlyNeededForDue(target, current, goal.dueDate, LocalDate.now())?.let {
            Label("期日に間に合わせるには 月々 ${money.amount(it)}")
        }
    }
    Label("積立だけでなく値動きも入った目安です")
}

@Composable
private fun ReminderContent(detail: ItemDetail.Reminder) {
    val reminder = detail.overview.item
    val money = LocalMoney.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Formatters.daysLeft(detail.overview.daysLeft), style = MaterialTheme.typography.titleLarge)
        Label("期日 ${reminder.dueDate}")
        reminder.amountYen?.let { Label("見込み額 ${money.amount(it)}") }
        Label(
            when (reminder.repeat) {
                Repeat.NONE -> "繰り返さない"
                Repeat.MONTHLY -> "毎月"
                Repeat.YEARLY -> "毎年"
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Spacer8() {
    Spacer(modifier = Modifier.height(8.dp))
}

@Preview(showBackground = true)
@Composable
private fun MetricDetailPreview() {
    val today = LocalDate.of(2026, 9, 25)
    val metric = Item.Metric(Item.metricId("投資信託"), "NISA", "投資信託")
    val months = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7))
    AssetDashboardTheme {
        ItemDetailScreen(
            detail = ItemDetail.Metric(
                overview = ItemOverview.Metric(metric, MetricPointEntity("投資信託", today, 1_500_000, MetricOrigin.CSV), 20_000),
                monthly = months.mapIndexed { i, m ->
                    MetricChange("投資信託", m.atDay(1)..m.atEndOfMonth(), 1_480_000L - i * 10_000, 1_500_000L - i * 10_000)
                },
                pointCount = 37,
                correctedCount = 1,
            ),
            loading = false,
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalDetailPreview() {
    AssetDashboardTheme {
        ItemDetailScreen(
            detail = ItemDetail.Goal(
                ItemOverview.Goal(Item.Goal("g", "車購入", 1_000_000, "預金・現金", LocalDate.of(2030, 4, 1)), 420_000),
            ),
            loading = false,
            onBack = {},
        )
    }
}
