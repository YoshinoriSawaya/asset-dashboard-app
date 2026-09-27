package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.MonthlyReview

/**
 * 前の月の振り返り(E03-10)。前の月の結果を1画面に集め、詳しい画面へ誘う。
 * いつもより多い月・固定費の値上がり・予算超えの通知(E05-08・E07-28・E07-29)を押すと、ここが開く。
 */
@Composable
fun MonthlyReviewScreen(
    load: suspend () -> MonthlyReview?,
    onOpenCategorySpending: () -> Unit,
    onOpenFixedCosts: () -> Unit,
    onOpenNetWorth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result by produceState<Pair<Boolean, MonthlyReview?>>(false to null) { value = true to load() }
    val money = LocalMoney.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        val (loaded, r) = result
        if (!loaded) {
            Text("読み込み中...")
            return@Column
        }
        if (r == null) {
            Text("振り返る月の明細がまだありません")
            return@Column
        }
        Text("${r.month.year}年${r.month.monthValue}月の振り返り", style = MaterialTheme.typography.headlineSmall)
        if (r.maybeIncomplete) {
            Label("今月の明細がまだ無いので、${r.month.monthValue}月の明細が出そろっていないかもしれません(同期すると揃います)")
        }

        Section("お金の流れ")
        Line("収入", money.amount(r.incomeYen))
        Line("消費(生活費 + 遊び代)", money.amount(r.consumptionYen))
        Line("残り(収入 − 消費)", money.change(r.surplusYen, r.incomeYen), strong = true)
        Line("積立投資", money.amount(r.investmentYen))
        r.netWorthChangeYen?.let { change ->
            Line("純資産の増減", money.change(change, null))
            TextButton(onClick = onOpenNetWorth) { Text("純資産を開く") }
        }

        Section("いつもより多かったカテゴリ")
        if (r.high.isEmpty()) {
            Label("ありません")
        } else {
            r.high.forEach { row ->
                Line(
                    "${row.category ?: "カテゴリなし"}(${row.kind?.label ?: "生活費"})",
                    row.averageYen?.let { avg -> if (avg == 0L) "前の月は無し" else money.change(row.yen - avg, avg) } ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Section("予算")
        if (r.budgets.isEmpty()) {
            Label("予算を決めたカテゴリはありません(カテゴリ別の支出の行から決められます)")
        } else {
            r.budgets.forEach { b ->
                Line(
                    b.category + if (b.over) "(超えた)" else "",
                    "予算の${money.percent(b.ratio)}",
                    color = if (b.over) MaterialTheme.colorScheme.error else null,
                )
            }
        }
        TextButton(onClick = onOpenCategorySpending) { Text("カテゴリ別の支出を開く") }

        Section("固定費の変化")
        if (r.increased.isEmpty() && r.stopped.isEmpty()) {
            Label("値上がりも、止まったものもありません")
        }
        r.increased.forEach { item ->
            val (old, new) = item.priceIncrease!!
            Line(AiExport.maskedName(item.description), "値上がり(${money.change(new - old, old)})", color = MaterialTheme.colorScheme.error)
        }
        r.stopped.forEach { item -> Line(AiExport.maskedName(item.description), "止まった(解約済み?)") }
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
