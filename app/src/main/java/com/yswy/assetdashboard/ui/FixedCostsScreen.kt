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
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.FixedCosts
import java.time.LocalDate

/**
 * 固定費・サブスクの一覧(E07-26)。毎月出ていく支払いを明細から見つけ、月の平均と年額の見込みを並べる。
 * 行を押すと、月ごとの額が開く(値上げや、止まったかどうかが分かる)。
 *
 * 摘要はこの端末の画面に出すだけで、どこにも書き出さない。
 */
@Composable
fun FixedCostsScreen(
    load: suspend () -> List<BankTransactionEntity>,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result by produceState<Pair<Boolean, FixedCosts?>>(false to null) { value = true to FixedCosts.of(load(), LocalDate.now()) }
    val money = LocalMoney.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("固定費・サブスク", style = MaterialTheme.typography.headlineSmall)
        }
        val (loaded, costs) = result
        if (!loaded) {
            item { Text("読み込み中...") }
            return@LazyColumn
        }
        if (costs == null) {
            item { Text("明細のある月が3か月に満たないので、毎月の支払いを決められません") }
            return@LazyColumn
        }

        item {
            val first = costs.months.first()
            val last = costs.months.last()
            Label(
                "${first.year}年${first.monthValue}月〜${last.year}年${last.monthValue}月の${costs.months.size}か月のうち、" +
                    "${FixedCosts.minMonths(costs.months.size)}か月以上出ている支払いです(よく買う店・振替・積立投資は除く)。" +
                    "年に1回の支払いは入りません。",
            )
            Line("合計(月)", money.amount(costs.monthlyTotalYen), strong = true)
            Line("合計(年の見込み)", money.amount(costs.yearlyTotalYen))
            costs.consumptionShare?.let { Line("消費に占める割合", money.share(it)) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (costs.items.isEmpty()) {
            item { Text("毎月の支払いは見つかりませんでした") }
        }
        items(costs.items, key = { it.description }) { item ->
            Column(modifier = Modifier.fillMaxWidth().clickable { open = if (open == item.description) null else item.description }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.description, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.category ?: "カテゴリなし"} ・ ${item.hitMonths}/${costs.months.size}か月 ・ " +
                                FixedCostsText.amountKind(item) + " ・ 最後 ${item.lastDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 値上がり(E07-28)。%は前の額に対する増え方(金額を隠す表示でも見える)
                        item.priceIncrease?.let { (old, new) ->
                            Text(
                                "値上がり(${money.change(new - old, old)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (item.missingLastMonth) {
                            Text(
                                "先月は無し(解約済み?)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("月 ${money.amount(item.monthlyYen)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "年 ${money.amount(item.yearlyYen)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (open == item.description) {
                    costs.months.zip(item.byMonth).reversed().forEach { (m, yen) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp)) {
                            Text("${m.year}年${m.monthValue}月", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(if (yen == 0L) "無し" else money.amount(yen), style = MaterialTheme.typography.bodySmall)
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

/** 固定費の見せ方の言葉(E07-26・E07-28)。画面とAI用の書き出しで同じにする。 */
object FixedCostsText {
    /** 定額か変動か。値上がりした支払いは額の幅が広がって変動に見えるので「定額(値上がり)」にする。 */
    fun amountKind(item: FixedCosts.Item): String = when {
        item.priceIncrease != null -> "定額(値上がり)"
        item.fixedAmount -> "定額"
        else -> "変動"
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
