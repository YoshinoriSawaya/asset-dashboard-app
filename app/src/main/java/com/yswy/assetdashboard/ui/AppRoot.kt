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
import com.yswy.assetdashboard.data.ExpenseCalendar
import com.yswy.assetdashboard.data.Item
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
    const val SYNC_LOG = "synclog"
    const val EXPORT = "export"
    const val SPENDING_RULES = "spending_rules"
    const val CALENDAR = "calendar"
    private const val ITEM = "item/"

    fun item(id: String) = ITEM + id

    private const val CORRECT = "correct/"
    private const val GOAL = "goal/"
    private const val METRIC = "metric/"
    private const val USAGE = "usage/"
    private const val REMINDER = "reminder/"

    /** リマインダーの編集(E05-05/06)。[id]がnullなら新規。 */
    fun reminder(id: String?) = REMINDER + id.orEmpty()

    fun reminderId(route: String): String? = route.removePrefix(REMINDER).takeIf { route.startsWith(REMINDER) }

    /** 毎年リセットする枠に使った分を足す(E07-09)。 */
    fun usage(goalId: String) = USAGE + goalId

    fun usageGoalId(route: String): String? = route.removePrefix(USAGE).takeIf { route.startsWith(USAGE) }

    /** Metric項目の表示名・非表示(E07-14)。 */
    fun metric(metricKey: String) = METRIC + metricKey

    fun metricKey(route: String): String? = route.removePrefix(METRIC).takeIf { route.startsWith(METRIC) }

    /** 目標の編集(E07-01)。[id]がnullなら新規。 */
    fun goal(id: String?) = GOAL + id.orEmpty()

    /** `goal/<id>` なら id(新規なら空文字)、違えばnull。 */
    fun goalId(route: String): String? = route.removePrefix(GOAL).takeIf { route.startsWith(GOAL) }

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
    val goalId = Routes.goalId(route)
    val metricKey = Routes.metricKey(route)
    val usageGoalId = Routes.usageGoalId(route)
    val reminderId = Routes.reminderId(route)
    /** [target]がまだ一番上なら閉じる。保存の完了が画面を離れた後に届いても、別の画面を閉じない。 */
    fun close(target: String) {
        if (stack.size > 1 && stack.last() == target) stack.removeAt(stack.lastIndex)
    }
    when {
        reminderId != null -> ReminderEditScreen(
            existing = state.overviews.map { it.item }.filterIsInstance<Item.Reminder>().firstOrNull { it.id == reminderId },
            saving = state.syncing,
            onSave = viewModel::saveReminder,
            onDelete = { id, onResult ->
                viewModel.deleteItem(id) { error ->
                    if (error == null) while (stack.size > 1) stack.removeAt(stack.lastIndex)
                    onResult(error)
                }
            },
            onBack = { close(route) },
            modifier = modifier,
        )
        usageGoalId != null -> {
            val goal = state.overviews.filterIsInstance<ItemOverview.Goal>().firstOrNull { it.item.id == usageGoalId }
            UsageScreen(
                goal = goal,
                saving = state.syncing,
                onSave = { amount, onResult ->
                    val key = goal?.item?.metricKey
                    if (key == null) onResult("記録する系列が無い") else viewModel.addUsage(key, amount, onResult)
                },
                onBack = { close(route) },
                modifier = modifier,
            )
        }
        metricKey != null -> MetricEditScreen(
            // 一覧にある項目と、隠している項目の両方から探す
            metric = (state.overviews.map { it.item }.filterIsInstance<Item.Metric>() + state.hiddenMetrics)
                .firstOrNull { it.metricKey == metricKey },
            saving = state.syncing,
            onSave = viewModel::saveMetric,
            onBack = { close(route) },
            modifier = modifier,
        )
        goalId != null -> GoalEditScreen(
            existing = state.overviews.map { it.item }.filterIsInstance<Item.Goal>().firstOrNull { it.id == goalId },
            metricKeys = state.overviews.map { it.item }.filterIsInstance<Item.Metric>().map { it.metricKey },
            saving = state.syncing,
            onSave = viewModel::saveGoal,
            // 削除したら詳細画面も意味が無いので、トップまで戻る
            onDelete = { id, onResult ->
                viewModel.deleteItem(id) { error ->
                    if (error == null) {
                        while (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                    onResult(error)
                }
            },
            onBack = { close(route) },
            modifier = modifier,
            onOpenSpendingRules = { stack.add(Routes.SPENDING_RULES) },
        )
        correctKey != null -> {
            val latest = state.overviews
                .filterIsInstance<ItemOverview.Metric>()
                .firstOrNull { it.item.metricKey == correctKey }
                ?.latest
            CorrectionScreen(
                fixedKey = correctKey.ifEmpty { null },
                initialDate = CorrectionForm.defaultDate(latest, LocalDate.now()).toString(),
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
                onEditGoal = { stack.add(Routes.goal(it)) },
                onEditMetric = { stack.add(Routes.metric(it)) },
                onAddUsage = { stack.add(Routes.usage(it)) },
                onEditReminder = { stack.add(Routes.reminder(it)) },
                onCompleteReminder = { reminder, onResult -> viewModel.completeReminder(reminder, onResult) },
                onDeleteCorrection = { key, date, onResult -> viewModel.deleteCorrection(key, date, onResult) },
                busy = state.syncing,
                modifier = modifier,
            )
        }
        route == Routes.CALENDAR -> ExpenseCalendarScreen(
            entries = ExpenseCalendar.build(state.overviews.map { it.item }, LocalDate.now()),
            onOpenItem = { entry -> stack.add(Routes.item(entry.itemId)) },
            onBack = { close(route) },
            modifier = modifier,
        )
        route == Routes.SPENDING_RULES -> SpendingRulesScreen(
            load = viewModel::loadSpendingRules,
            withdrawals = viewModel::withdrawalDescriptions,
            saving = state.syncing,
            onSave = viewModel::saveSpendingRules,
            onBack = { close(route) },
            modifier = modifier,
        )
        route == Routes.EXPORT -> {
            val text by produceState<String?>(null, state.overviews) { value = viewModel.aiExport() }
            ExportScreen(text = text, onBack = { close(route) }, modifier = modifier)
        }
        route == Routes.SYNC_LOG -> SyncLogScreen(
            lastSync = state.lastSync,
            onBack = { close(route) },
            modifier = modifier,
        )
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
                onOpenSpendingRules = { stack.add(Routes.SPENDING_RULES) },
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
            onOpenSyncLog = { stack.add(Routes.SYNC_LOG) },
            onOpenExport = { stack.add(Routes.EXPORT) },
            onAddGoal = { stack.add(Routes.goal(null)) },
            onAddReminder = { stack.add(Routes.reminder(null)) },
            onOpenCalendar = { stack.add(Routes.CALENDAR) },
            onRunDailyCheck = viewModel::runDailyCheckNow,
            onEditMetric = { stack.add(Routes.metric(it)) },
            modifier = modifier,
        )
    }
}

private val routeStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf(*it.toTypedArray()) },
)
