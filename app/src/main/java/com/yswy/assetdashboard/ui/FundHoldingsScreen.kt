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
import com.yswy.assetdashboard.data.FundHoldings

/**
 * 投資信託の損益(E01-17)。ファンドごとの評価額・取得額・含み益と、平均取得単価・現在値(本人が選んだ: ファンドごとだけ)。
 * 行を押すと、取り込んだ日ごとの推移が開く。値は証券口座の保有商品一覧(E01-15)を取り込んだときのもの。
 */
@Composable
fun FundHoldingsScreen(
    load: suspend () -> FundHoldings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val holdings by produceState<FundHoldings?>(null) { value = load() }
    val money = LocalMoney.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("投資信託の損益", style = MaterialTheme.typography.headlineSmall)
        }
        val h = holdings
        if (h == null) {
            item { Text("読み込み中...") }
            return@LazyColumn
        }
        if (h.funds.isEmpty()) {
            item {
                Text(
                    "まだありません。証券口座の保有商品一覧(ポートフォリオ)のCSVを inbox に置いて同期すると、" +
                        "ファンドごとの取得額と評価額が出ます(前に取り込んだファイルは、processed から inbox に戻すと読み直します)。",
                )
            }
            return@LazyColumn
        }
        item {
            val latest = h.funds.maxOf { it.latest.date }
            Label("${latest}時点の保有商品一覧から。含み益の%は取得額に対する割合です。")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        items(h.funds, key = { it.section + "|" + it.name }) { fund ->
            val key = fund.section + "|" + fund.name
            val s = fund.latest
            Column(modifier = Modifier.fillMaxWidth().clickable { open = if (open == key) null else key }.padding(vertical = 8.dp)) {
                Text(fund.name, style = MaterialTheme.typography.bodyLarge)
                Label("${fund.section} ・ ${s.date}")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Label("評価額 ${money.amount(s.valueYen)}")
                        s.costYen?.let { Label("取得額 ${money.amount(it)}") }
                    }
                    s.gainYen?.let { gain ->
                        Text(
                            // 実額では額と%、%表示では%だけ(取得額に対する割合)
                            "含み益 ${gainText(money, gain, s.costYen)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (gain < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (s.unitCost != null && s.price != null) {
                    // 単価は金額ではない(1万口あたりの値など)が、実額の表示でだけ出す
                    // 単価の差は円ではないので、割合で出す
                    Label("平均取得単価 ${money.unit(s.unitCost)} → 現在値 ${money.unit(s.price)}(${ratioText(money, s.price - s.unitCost, s.unitCost)})")
                }
                if (open == key) {
                    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        fund.history.forEach { past ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("${past.date}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    "評価額 ${money.amount(past.valueYen)}" +
                                        (past.gainYen?.let { " ・ 含み益 ${gainText(money, it, past.costYen)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (fund.history.size == 1) Label("推移は、保有商品一覧を取り込むたびに1点ずつ増えます")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** 基準に対する増減の割合(E01-17)。割合は金額ではないので、マスクのときだけ伏せる。 */
internal fun ratioText(money: MoneyFormat, diff: Long, base: Long?): String = when {
    money.mode == PrivacyMode.MASK -> MoneyFormat.HIDDEN
    base == null || base == 0L -> MoneyFormat.HIDDEN_SHORT
    else -> MoneyFormat.signedPercent(diff.toDouble() / kotlin.math.abs(base))
}

/** 含み益(E01-17)。実額では額と割合、%表示では割合だけ、マスクでは伏せる。 */
internal fun gainText(money: MoneyFormat, gain: Long, cost: Long?): String = when (money.mode) {
    PrivacyMode.REAL -> "${money.change(gain, cost)}(${ratioText(money, gain, cost)})"
    PrivacyMode.PERCENT -> ratioText(money, gain, cost)
    PrivacyMode.MASK -> MoneyFormat.HIDDEN
}
