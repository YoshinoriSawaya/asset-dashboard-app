package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.YearlyReview
import com.yswy.assetdashboard.ui.chart.ChangeBarChart

/**
 * 1年の振り返り(E03-12)。前の月の振り返り(E03-10)の年版。期間は直近12か月と暦年を切り替える(本人が選んだ)。
 * 通知からは開かない(本人が選んだ: 通知しない)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearlyReviewScreen(
    loadPeriods: suspend () -> List<YearlyReview.Period>,
    load: suspend (YearlyReview.Period) -> YearlyReview,
    onOpenCategorySpending: () -> Unit,
    onOpenFixedCosts: () -> Unit,
    onOpenNetWorth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods by produceState<List<YearlyReview.Period>?>(null) { value = loadPeriods() }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val money = LocalMoney.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("1年の振り返り", style = MaterialTheme.typography.headlineSmall)
        val list = periods
        if (list == null) {
            Text("読み込み中...")
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEachIndexed { i, p -> FilterChip(selected == i, onClick = { selected = i }, label = { Text(p.label) }) }
        }
        val period = list.getOrNull(selected) ?: return@Column
        val review by produceState<YearlyReview?>(null, period) { value = load(period) }
        val r = review
        if (r == null) {
            Text("読み込み中...")
            return@Column
        }
        Label("${period.months.first()} 〜 ${period.months.last()}")
        if (r.monthsWithData == 0) {
            Text("この期間の明細がありません")
            return@Column
        }
        if (r.monthsWithData < period.months.size) {
            Label("明細のある月は${period.months.size}か月のうち${r.monthsWithData}か月だけです(無い月は数えていません)")
        }

        Section("お金の流れ")
        Line("収入", money.amount(r.incomeYen))
        Line("消費(生活費 + 遊び代)", money.amount(r.consumptionYen))
        Line("残り(収入 − 消費)", money.change(r.surplusYen, r.incomeYen), strong = true)
        Line("大型出費", money.amount(r.plannedYen))
        Line("積立投資", money.amount(r.investmentYen))
        Label("月ごとの残り(収入 − 消費)")
        ChangeBarChart(
            bars = r.monthly.map { (m, v) -> "${m.monthValue}月" to v },
            axisLabel = money::barAxis,
        )

        Section("純資産の増減")
        val change = r.netWorthChangeYen
        if (change == null) {
            Label("期間の前と終わりの純資産の値がそろわないので出せません")
        } else {
            Line("純資産の増減", money.change(change, null), strong = true)
            Line("貯めた分(収入 − 消費 − 大型出費)", money.change(r.savedYen, null))
            Line("評価額の増減など(残り)", money.change(r.otherChangeYen!!, null))
            Label("残りには相場の上下のほか、明細に無い出入りも入ります")
        }
        TextButton(onClick = onOpenNetWorth) { Text("純資産を開く") }

        Section("カテゴリ別")
        when {
            r.previousMonthsWithData == 0 -> Label("前の12か月の明細が無いので、前の期間とは比べません")
            r.previousMonthsWithData < 12 -> Label("前の12か月のうち明細があるのは${r.previousMonthsWithData}か月だけです(比べるときは注意)")
            else -> Label("右は前の12か月との差")
        }
        r.categories.forEach { c ->
            val name = "${c.category ?: "カテゴリなし"}(${c.kind?.label ?: "生活費"})"
            val diff = if (r.previousMonthsWithData == 0) "" else " ・ ${if (c.previousYen == 0L) "前は無し" else money.change(c.changeYen, c.previousYen)}"
            // 消費が増えたものだけ目立たせる(収入や積立投資が増えるのは悪くない)
            val worse = r.previousMonthsWithData > 0 && c.changeYen > 0 && (c.kind?.isConsumption ?: true) && c.previousYen != 0L
            Line(name, money.amount(c.yen) + diff, color = if (worse) MaterialTheme.colorScheme.error else null)
        }
        TextButton(onClick = onOpenCategorySpending) { Text("カテゴリ別の支出を開く") }

        Section("固定費の変化")
        val f = r.fixed
        if (f == null) {
            Label("期間の終わりの明細が3か月に満たないので、固定費を決められません")
        } else {
            Line(
                "固定費(月の目安)",
                money.amount(f.monthlyTotalYen) + (f.previousMonthlyTotalYen?.let { " ・ 前 ${money.amount(it)}" } ?: ""),
                strong = true,
            )
            if (f.previousMonthlyTotalYen == null) Label("期間の前の明細が足りないので、増えた・止まったは比べていません")
            f.added.forEach { Line(AiExport.maskedName(it.description), "増えた(月 ${money.amount(it.monthlyYen)})") }
            f.stopped.forEach { Line(AiExport.maskedName(it.description), "止まった") }
            f.changed.forEach { (before, after) ->
                Line(
                    AiExport.maskedName(after.description),
                    "変わった(${money.change(after.monthlyYen - before.monthlyYen, before.monthlyYen)})",
                    color = if (after.monthlyYen > before.monthlyYen) MaterialTheme.colorScheme.error else null,
                )
            }
            if (f.previousMonthlyTotalYen != null && f.added.isEmpty() && f.stopped.isEmpty() && f.changed.isEmpty()) {
                Label("増えた・止まった・金額の変わった固定費はありません")
            }
        }
        TextButton(onClick = onOpenFixedCosts) { Text("固定費・サブスクを開く") }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false, color: Color? = null) {
    val style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, color = color ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = style, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
