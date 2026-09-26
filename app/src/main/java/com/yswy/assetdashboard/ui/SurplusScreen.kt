package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.AutoTarget
import com.yswy.assetdashboard.data.AutoTargets
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.SurplusPlan
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import java.time.LocalDate

/**
 * 余剰資金の配分案(E10-02)。額を入れると、その場で[SurplusPlan]を出す。
 *
 * 案を出すだけで何も保存しない(振替は本人が口座でする)。決まりは段の順と目標の一覧の順で、
 * 設定の画面は持たない。順番を変えたいときは目標の詳細の「上へ・下へ」で並べ替える。
 */
@Composable
fun SurplusScreen(
    goals: List<ItemOverview.Goal>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var amount by rememberSaveable { mutableStateOf("") }
    val money = LocalMoney.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("余剰資金の配分案", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ボーナスなどの額を入れると、どの目標にいくら回すかの案を出します。" +
                "①下限を割った目標を下限まで ②積み増し中の目標に今月の分 ③残りを各目標の不足分まで、の順で、" +
                "同じ段の中は目標の一覧の順です。案を出すだけで、何も保存しません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text("余剰資金(円)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        val yen = CorrectionForm.parseYen(amount)
        if (amount.isNotBlank() && (yen == null || yen <= 0)) {
            Text("0より大きい額を数字で入れてください(例: 300,000)", color = MaterialTheme.colorScheme.error)
        }
        if (goals.isEmpty()) {
            Text("目標がまだありません。トップの「+ 目標を追加」から作れます。")
        }
        if (yen == null || yen <= 0) return@Column

        val plan = SurplusPlan.of(yen, goals, today)
        SurplusPlan.Tier.entries.forEach { tier ->
            val lines = plan.lines.filter { it.tier == tier }
            if (lines.isEmpty()) return@forEach
            Text(tierLabel(tier), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            lines.forEach { PlanRow(it.goal.name, money.amount(it.amountYen)) }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        PlanRow("④ 余り(NISAの上乗せなど、自由に使える)", money.amount(plan.freeYen))

        if (plan.lines.isNotEmpty()) {
            Text("目標ごとの合計", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            plan.lines.map { it.goal }.distinctBy { it.id }.forEach { goal ->
                PlanRow(goal.name, money.amount(plan.totalFor(goal.id)))
            }
        }
        if (plan.unknown.isNotEmpty()) {
            Text(
                "目標額か今の値が分からないので案に入れていない目標: " + plan.unknown.joinToString("、") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun tierLabel(tier: SurplusPlan.Tier): String = when (tier) {
    SurplusPlan.Tier.FLOOR -> "① 下限の回復"
    SurplusPlan.Tier.RAMP_UP -> "② 積み増し(今月の分・期日を過ぎた分)"
    SurplusPlan.Tier.SHORTFALL -> "③ 不足分を一覧の順に"
}

@Composable
private fun PlanRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Preview(showBackground = true)
@Composable
private fun SurplusScreenPreview() {
    val fund = Item.Goal("f", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6, 3))
    AssetDashboardTheme {
        SurplusScreen(
            goals = listOf(
                ItemOverview.Goal(fund, 600_000, auto = AutoTargets.Result(targetYen = 1_200_000, monthsUsed = 6, monthlyAverageYen = 200_000)),
                ItemOverview.Goal(Item.Goal("c", "車購入", 1_000_000, "車"), 400_000),
            ),
            onBack = {},
        )
    }
}
