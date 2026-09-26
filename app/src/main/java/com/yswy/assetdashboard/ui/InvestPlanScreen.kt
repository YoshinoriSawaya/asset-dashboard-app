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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.InvestPlan

/**
 * 積立投資の目安(E10-04)。収入から消費と守りのお金を引いた残りの8割を、今の積立額と比べて出す。
 * 計算の中身([InvestPlan])をそのまま並べ、どこから出た数字か分かるようにする。
 */
@Composable
fun InvestPlanScreen(
    load: suspend () -> InvestPlan?,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan by produceState<InvestPlan?>(null) { value = load() }
    val money = LocalMoney.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("積立投資の目安", style = MaterialTheme.typography.headlineSmall)
        val p = plan
        if (p == null) {
            Text("使える月の明細がまだありません(今月と、明細の無い月は使いません)")
            return@Column
        }

        Text("月々 ${money.amount(p.suggestedYen)}", style = MaterialTheme.typography.titleLarge)
        if (p.surplusYen <= 0) {
            // 残りが無い。振替・積立投資が消費に入ったままのことが多い
            Text(
                "収入から引くと残りがありません。振替・積立投資のカテゴリが付いていないと、消費が多めに出ます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (p.currentYen == 0L) {
            Text(
                "今の積立額はまだ分かりません(種類が「積立投資」のカテゴリが付いた明細がありません)",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val diff = p.suggestedYen - p.currentYen
            Text(
                "今の積立 月々 ${money.amount(p.currentYen)} → " +
                    when {
                        diff > 0 -> "${money.amount(diff)} 増やせる"
                        diff < 0 -> "${money.amount(-diff)} 減らしたほうがよい"
                        else -> "今のままでよい"
                    },
                style = MaterialTheme.typography.titleSmall,
                color = if (diff < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("計算(直近${p.months}か月の月平均)", style = MaterialTheme.typography.titleSmall)
        Line("収入(ボーナスを含む。振替は除く)", money.amount(p.incomeYen))
        Line("− 消費(生活費 + 遊び代)", money.amount(p.consumptionYen))
        // カテゴリの無い明細は生活費として数える。振替・大型出費・積立投資が混ざると消費が多めに出る(E07-23)
        p.uncategorizedShare?.takeIf { p.uncategorizedYen > 0 }?.let {
            Text(
                "　うちカテゴリなし ${money.share(it)}(生活費として数えています)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Line("− 生活防衛資金の月額(足りない分)", money.amount(p.refillYen))
        Line("− 大型出費の積立の月額", money.amount(p.sinkingYen))
        Line("− 積み増し中の目標の月額(車の頭金など)", money.amount(p.rampUpYen))
        HorizontalDivider()
        Line("= 残り", money.amount(p.surplusYen))
        Line("× 8割(ゆとり2割)", money.amount(p.suggestedYen))

        Text(
            "今の積立は、カテゴリの種類が「積立投資」の明細の月平均です。振替・積立投資・大型出費のカテゴリが" +
                "付いていないと、収入や消費が多めに出ます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onOpenCategories) { Text("明細のカテゴリを見直す(カテゴリなしだけに絞れます)") }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
