package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp

/**
 * 明細のカテゴリの決まりをまとめて取り込む(E07-22)。Driveの `settings/category_rules.csv` を読んで、
 * 新しく作るカテゴリ・足す決まり・外す決まりを先に見せ、「取り込む」で categories.json に書く。
 */
@Composable
fun CategoryImportScreen(
    load: suspend () -> Result<DashboardViewModel.CategoryImportPreview>,
    saving: Boolean,
    onImport: (CategoryImport.Plan, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var reload by remember { mutableIntStateOf(0) }
    val result by produceState<Result<DashboardViewModel.CategoryImportPreview>?>(null, reload) { value = null; value = load() }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("カテゴリの決まりを取り込む", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Driveの 資産アプリ/settings/${CategoryImport.FILE_NAME} を読みます。列は「言葉,カテゴリ,種類」。" +
                "種類は 生活費・遊び代・大型出費・振替・積立投資。カテゴリが空の行は、その言葉に当たる決まりを外します。" +
                "取り込む言葉を含む細かい決まりは外して、取り込む言葉に寄せます。",
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
            else -> {
                val preview = current.getOrThrow()
                val parsed = preview.parsed
                val plan = preview.plan
                if (parsed == null || plan == null) {
                    Text("${CategoryImport.FILE_NAME} がDriveの settings にありません。", color = MaterialTheme.colorScheme.error)
                    return@Column
                }
                Text(
                    "決まりを足す ${plan.rules.size}件 / 外す ${plan.removed.size}件 / 新しいカテゴリ ${plan.newCategories.size}つ / 読めない行 ${parsed.problems.size}件",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (plan.newCategories.isNotEmpty()) {
                    Text("新しく作るカテゴリ", style = MaterialTheme.typography.titleSmall)
                    plan.newCategories.forEach { Label("${it.name}(${it.kind.label})") }
                }
                if (plan.kindMismatches.isNotEmpty()) {
                    Text(
                        "同じ名前のカテゴリが別の種類で既にあるので、今の種類のまま使います: " + plan.kindMismatches.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("足す決まり", style = MaterialTheme.typography.titleSmall)
                plan.rules.forEach { Label("${it.keyword} → ${it.category}") }
                if (plan.removed.isNotEmpty()) {
                    Text("外す決まり", style = MaterialTheme.typography.titleSmall)
                    plan.removed.forEach { Label("${it.keyword}(${it.category})") }
                }
                if (parsed.problems.isNotEmpty()) {
                    Text("読めない行(取り込まない)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    parsed.problems.forEach { Label("${it.lineNumber}行目: ${it.reason}") }
                }
                Button(
                    enabled = !saving && (plan.rules.isNotEmpty() || plan.removed.isNotEmpty()),
                    onClick = {
                        message = "取り込み中..."
                        onImport(plan) { error -> message = error ?: "取り込みました" }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("取り込む") }
                message?.let {
                    Text(it, color = if (it == "取り込みました" || it.endsWith("中...")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
