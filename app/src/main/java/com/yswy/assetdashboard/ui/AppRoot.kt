package com.yswy.assetdashboard.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import com.yswy.assetdashboard.data.ItemDetail
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.PeriodSummary
import com.yswy.assetdashboard.data.PeriodUnit

/**
 * 画面遷移。
 *
 * ## ライブラリを使わず、ルート文字列のスタックで持つ
 * 画面はトップ・項目詳細・サマリー・補正の数枚で、深い階層も外部からの
 * 遷移(ディープリンク)も無い。Navigationライブラリを入れるほどではないので、
 * `"top"` `"item/<id>"` のような文字列を積むだけにした(CLAUDE.mdの
 * 「過度な抽象化を避ける」)。文字列なので回転しても`rememberSaveable`で残る。
 */
object Routes {
    const val TOP = "top"
    const val SUMMARY = "summary"
    private const val ITEM = "item/"

    fun item(id: String) = ITEM + id

    private const val CORRECT = "correct/"

    /** 補正画面。[metricKey]がnullなら新しい系列の手入力。 */
    fun correct(metricKey: String?) = CORRECT + metricKey.orEmpty()

    /** `correct/<key>` なら key(新規なら空文字)、違えばnull。 */
    fun correctKey(route: String): String? = route.removePrefix(CORRECT).takeIf { route.startsWith(CORRECT) }

    /** `item/<id>` ならid、違えばnull。 */
    fun itemId(route: String): String? = route.removePrefix(ITEM).takeIf { route.startsWith(ITEM) }
}

@Composable
fun AppRoot(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    val stack = rememberSaveable(saver = routeStackSaver) { mutableStateListOf(Routes.TOP) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    // 同意画面はActivityからしか出せないので、ViewModelの依頼をここで受ける。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { viewModel.onConsentResult() }
    LaunchedEffect(state.consentRequest) {
        val intent = state.consentRequest ?: return@LaunchedEffect
        viewModel.consentLaunched()
        consentLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    val route = stack.last()
    val itemId = Routes.itemId(route)
    val correctKey = Routes.correctKey(route)
    /** [target]がまだ一番上なら閉じる。保存の完了が画面を離れた後に届いても、別の画面を閉じない。 */
    fun close(target: String) {
        if (stack.size > 1 && stack.last() == target) stack.removeAt(stack.lastIndex)
    }
    when {
        correctKey != null -> {
            val latest = state.overviews
                .filterIsInstance<ItemOverview.Metric>()
                .firstOrNull { it.item.metricKey == correctKey }
                ?.latest
            CorrectionScreen(
                fixedKey = correctKey.ifEmpty { null },
                initialDate = (latest?.date ?: LocalDate.now()).toString(),
                initialValue = latest?.valueYen?.toString().orEmpty(),
                saving = state.syncing,
                onSave = viewModel::saveCorrection,
                onBack = { close(route) },
                modifier = modifier,
            )
        }
        itemId != null -> {
            val overview = state.overviews.firstOrNull { it.item.id == itemId }
            // 同期で一覧が更新されたら、詳細も読み直す
            val detail by produceState<ItemDetail?>(null, overview) {
                value = overview?.let { viewModel.detail(it) }
            }
            ItemDetailScreen(
                detail = detail,
                loading = overview != null && detail == null,
                onBack = { stack.removeAt(stack.lastIndex) },
                onCorrect = { stack.add(Routes.correct(it)) },
                onDeleteCorrection = { key, date, onResult -> viewModel.deleteCorrection(key, date, onResult) },
                busy = state.syncing,
                modifier = modifier,
            )
        }
        route == Routes.SUMMARY -> {
            var unit by rememberSaveable { mutableStateOf(PeriodUnit.MONTH) }
            // 同期でデータが変わったら(一覧が更新されたら)読み直す
            val summaries by produceState<List<PeriodSummary>?>(null, unit, state.overviews) {
                value = viewModel.summaries(unit)
            }
            SummaryScreen(
                unit = unit,
                summaries = summaries,
                onUnitChange = { unit = it },
                onBack = { stack.removeAt(stack.lastIndex) },
                modifier = modifier,
            )
        }
        else -> TopScreen(
            state = state,
            onSync = viewModel::sync,
            onOpenItem = { stack.add(Routes.item(it)) },
            onOpenSummary = { stack.add(Routes.SUMMARY) },
            onAddManual = { stack.add(Routes.correct(null)) },
            modifier = modifier,
        )
    }
}

private val routeStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf(*it.toTypedArray()) },
)
