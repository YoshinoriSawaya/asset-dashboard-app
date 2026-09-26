package com.yswy.assetdashboard.ui

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.AutoSyncPrefs
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.GoalOrder
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemDetail
import com.yswy.assetdashboard.data.ItemEntity
import com.yswy.assetdashboard.data.CategorySettings
import com.yswy.assetdashboard.data.InvestPlan
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.Summary
import com.yswy.assetdashboard.data.NetWorth
import com.yswy.assetdashboard.data.PeriodSummary
import com.yswy.assetdashboard.data.PeriodUnit
import com.yswy.assetdashboard.data.SummaryBoard
import com.yswy.assetdashboard.data.SyncPolicy
import com.yswy.assetdashboard.data.SyncStatus
import com.yswy.assetdashboard.data.toEntity
import com.yswy.assetdashboard.data.toItem
import com.yswy.assetdashboard.drive.AppFolders
import com.yswy.assetdashboard.drive.CacheSync
import com.yswy.assetdashboard.drive.CategoryStore
import com.yswy.assetdashboard.drive.Corrections
import com.yswy.assetdashboard.drive.DriveApi
import com.yswy.assetdashboard.drive.DriveFolderSetup
import com.yswy.assetdashboard.drive.DriveSession
import com.yswy.assetdashboard.drive.FullSync
import com.yswy.assetdashboard.drive.LastSync
import com.yswy.assetdashboard.drive.LastSyncStore
import com.yswy.assetdashboard.drive.Settings
import com.yswy.assetdashboard.drive.SpendingRules
import com.yswy.assetdashboard.notify.DailyCheck
import com.yswy.assetdashboard.ui.chart.SeriesColors
import com.yswy.assetdashboard.widget.SyncStatusWidget
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * アプリ全体の状態。キャッシュの読み出しと同期を受け持つ。
 *
 * 画面ではなくViewModelに置くのは、画面の回転などでActivityが作り直されても
 * 開いたときの判定(E02-04)をやり直さないため。ViewModelの初期化は
 * アプリを開いたときに1回だけ走る。
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val overviews: List<ItemOverview> = emptyList(),
        val syncStatus: SyncStatus? = null,
        val syncing: Boolean = false,
        /** 今起きていること(同期中、同期しなかった理由など)の短い一言。 */
        val message: String = "",
        /** 一覧から隠しているMetric項目(E07-14)。トップから戻せるように持っておく。 */
        val hiddenMetrics: List<Item.Metric> = emptyList(),
        /** 系列の色の番号(E03-08)。metricKey → 番号。 */
        val seriesColors: Map<String, Int> = emptyMap(),
        /** 純資産の推移(E10-01)。数える系列を選んでいなければnull。 */
        val netWorth: NetWorth? = null,
        /** 前回の同期の結果(E03-05)。まだ一度も同期していなければnull。 */
        val lastSync: LastSync? = null,
        /** 同意画面を出してほしい。出したら[consentLaunched]を呼ぶ。 */
        val consentRequest: PendingIntent? = null,
    )

    private val db = AppDatabase.get(app)
    private val lastSyncStore = LastSyncStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(lastSync = lastSyncStore.load()) }
        // 端末で出す通知(E05)の毎日の確認を予約する。予約は再起動で消えるので、開くたびに入れ直す
        DailyCheck.schedule(app)
        viewModelScope.launch {
            refresh()
            autoSyncIfNeeded()
        }
    }

    /** 開いたときに同期が必要か判定し、必要なら同期する(E02-04)。 */
    private suspend fun autoSyncIfNeeded() {
        val status = _state.value.syncStatus ?: return
        val prefs = AutoSyncPrefs(getApplication())
        val now = Instant.now()
        val auto = SyncPolicy.shouldAutoSync(status.latestDataDate, LocalDate.now(), prefs.lastAttemptAt, now)
        if (auto) {
            // 試した時点で記録する。失敗しても1時間は自動では試さない。
            prefs.lastAttemptAt = now
            runSync(askConsent = true)
        } else {
            _state.update {
                it.copy(message = if (status.isDue) "自動同期は1時間以内に試したので見送り" else "期限内なので同期しない")
            }
        }
    }

    /** 同期ボタン。 */
    fun sync() {
        if (_state.value.syncing) return
        viewModelScope.launch { runSync(askConsent = true) }
    }

    /** 同意画面から戻ってきた。もう一度同意を求めると回り続けるので、今回は求めない。 */
    fun onConsentResult() {
        viewModelScope.launch { runSync(askConsent = false) }
    }

    fun consentLaunched() {
        _state.update { it.copy(consentRequest = null) }
    }

    /** 詳細画面(E03-02)の中身。一覧の行に、系列の点などを足して組み立てる。 */
    suspend fun detail(overview: ItemOverview): ItemDetail = ItemDetail.load(db, overview, _state.value.overviews)

    /**
     * 補正を保存する(E03-04)。
     *
     * Driveのcorrectionsを読み、足して丸ごと書き戻し、キャッシュを作り直す。
     * **Driveに書けなければローカルも変えない。** ローカルだけ直すと、次の同期で
     * キャッシュを作り直したときに黙って消える(Driveが正)。
     *
     * 画面を離れても途中で止まらないよう、viewModelScopeで走らせて結果を返す。
     * @param onResult 失敗の理由。保存できたらnull
     */
    fun saveCorrection(input: CorrectionForm.Result.Ok, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val origin = db.metricPointDao().find(input.metricKey, input.date)?.origin
            val entry = Corrections.Entry(
                metricKey = input.metricKey,
                date = input.date,
                valueYen = input.valueYen,
                kind = CorrectionForm.kindFor(origin),
                note = input.note,
                correctedAt = System.currentTimeMillis(),
            )
            onResult(editCorrections { Corrections.upsert(it, entry) })
        }
    }

    /**
     * 毎年リセットする枠に使った分を足す(E07-09)。今年の累計に足した値を、
     * 今日の手入力の点として補正と同じ道で保存する。
     */
    fun addUsage(metricKey: String, amount: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val points = db.metricPointDao().series(metricKey)
            when (val input = UsageForm.parse(points, amount, LocalDate.now())) {
                is UsageForm.Result.Invalid -> onResult(input.message)
                is UsageForm.Result.Ok -> saveCorrection(
                    CorrectionForm.Result.Ok(metricKey, input.date, input.newTotalYen, note = "使った分を足す"),
                    onResult,
                )
            }
        }
    }

    /** 補正を取り消す。CSVの点があればその値に戻り、手入力だけの点なら消える。 */
    fun deleteCorrection(metricKey: String, date: LocalDate, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(editCorrections { Corrections.remove(it, metricKey, date) })
        }
    }

    private suspend fun editCorrections(change: (List<Corrections.Entry>) -> List<Corrections.Entry>): String? =
        editOnDrive { api, folders ->
            // 読めないまま書くと、既存の補正を全部消してしまう
            val current = Corrections.load(api, folders) ?: return@editOnDrive "Driveの補正を読めないので保存しない"
            if (Corrections.save(api, folders, change(current))) null else "Driveに書けないので保存しない"
        }

    /**
     * 目標を保存する(E07-01)。Driveの settings/items.json に足すか置き換える。
     * 補正(E03-04)と同じく、Driveに書けなければローカルも変えない。
     */
    fun saveGoal(goal: Item.Goal, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(editSettings { Settings.upsert(it, goal.toEntity()) }) }
    }

    /**
     * 目標を一覧で1つ上・下へ動かす(E07-10)。同じ系列を分け合う目標の配分の順番になる。
     *
     * 目標の並び順は、作ったときは全部同じ(-1)で名前順に並んでいる。動かすときに
     * 一覧に出ている目標全部へ、今の並びどおりの番号を振り直してまとめて保存する。
     */
    fun moveGoal(id: String, up: Boolean, onResult: (String?) -> Unit) {
        val goals = _state.value.overviews.filterIsInstance<ItemOverview.Goal>().map { it.item }
        val reordered = GoalOrder.move(goals, id, up) ?: return onResult(null)
        viewModelScope.launch {
            onResult(editSettings { items -> reordered.fold(items) { acc, goal -> Settings.upsert(acc, goal.toEntity()) } })
        }
    }

    /** Metric項目の表示名・非表示(E07-14)。既定に戻すならsettingsから消す。 */
    fun saveMetric(result: MetricForm.Result, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(
                when (result) {
                    is MetricForm.Result.Save -> editSettings { Settings.upsert(it, result.metric.toEntity()) }
                    is MetricForm.Result.Reset -> editSettings { Settings.remove(it, result.id) }
                    is MetricForm.Result.Invalid -> result.message
                },
            )
        }
    }

    /** 明細のカテゴリ(E07-21)をDriveから読む。まだ無ければ前の除く言葉(E07-06)から作る。 */
    suspend fun loadCategories(): Result<CategorySettings> {
        val outcome = DriveSession.withDrive(getApplication()) { api ->
            CategoryStore.load(api, DriveFolderSetup.ensure(api).folders)
        }
        return when (outcome) {
            is DriveSession.Outcome.Success ->
                outcome.value?.let { Result.success(it) } ?: Result.failure(Exception("Driveの設定を読めない"))
            is DriveSession.Outcome.Offline -> Result.failure(Exception("オフライン"))
            is DriveSession.Outcome.Failed -> Result.failure(Exception(outcome.message))
            is DriveSession.Outcome.ConsentRequired -> {
                _state.update { it.copy(consentRequest = outcome.pendingIntent) }
                Result.failure(Exception("Googleの同意が必要"))
            }
        }
    }

    /** カテゴリの決まりの取り込み(E07-22)で読んだもの。ファイルが無ければ[plan]がnull。 */
    data class CategoryImportPreview(val parsed: CategoryImport.Parsed?, val plan: CategoryImport.Plan?)

    /** Driveの `settings/category_rules.csv` と今のカテゴリを読んで突き合わせる。まだ何も書かない。 */
    suspend fun loadCategoryImport(): Result<CategoryImportPreview> {
        val outcome = DriveSession.withDrive(getApplication()) { api ->
            val folders = DriveFolderSetup.ensure(api).folders
            val fileId = api.findFile(CategoryImport.FILE_NAME, folders.settings)
                ?: return@withDrive CategoryImportPreview(null, null)
            val current = CategoryStore.load(api, folders) ?: error("明細のカテゴリを読めない")
            val parsed = CategoryImport.parse(api.download(fileId))
            CategoryImportPreview(parsed, CategoryImport.plan(parsed.rows, current))
        }
        return when (outcome) {
            is DriveSession.Outcome.Success -> Result.success(outcome.value)
            is DriveSession.Outcome.Offline -> Result.failure(Exception("オフライン"))
            is DriveSession.Outcome.Failed -> Result.failure(Exception(outcome.message))
            is DriveSession.Outcome.ConsentRequired -> {
                _state.update { it.copy(consentRequest = outcome.pendingIntent) }
                Result.failure(Exception("Googleの同意が必要"))
            }
        }
    }

    /** 予定の取り込み(E07-16)で読んだもの。ファイルが無ければ[plan]がnull。 */
    data class PlanPreview(val parsed: PlanImport.Parsed?, val plan: PlanImport.Plan?)

    /**
     * Driveの `settings/plan.csv` を読み、今の項目と突き合わせる。まだ何も登録しない。
     * 突き合わせる相手は、Driveのsettingsそのもの(キャッシュではなく)。
     */
    suspend fun loadPlan(): Result<PlanPreview> {
        val outcome = DriveSession.withDrive(getApplication()) { api ->
            val folders = DriveFolderSetup.ensure(api).folders
            val fileId = api.findFile(PlanImport.FILE_NAME, folders.settings)
                ?: return@withDrive PlanPreview(null, null)
            val settings = Settings.load(api, folders) ?: error("Driveの設定を読めない")
            val parsed = PlanImport.parse(api.download(fileId))
            PlanPreview(parsed, PlanImport.plan(parsed.rows, settings.mapNotNull { it.toItem() }))
        }
        return when (outcome) {
            is DriveSession.Outcome.Success -> Result.success(outcome.value)
            is DriveSession.Outcome.Offline -> Result.failure(Exception("オフライン"))
            is DriveSession.Outcome.Failed -> Result.failure(Exception(outcome.message))
            is DriveSession.Outcome.ConsentRequired -> {
                _state.update { it.copy(consentRequest = outcome.pendingIntent) }
                Result.failure(Exception("Googleの同意が必要"))
            }
        }
    }

    /** 取り込みの中身をsettingsに足す(同じidは置き換える)。 */
    fun importPlan(plan: PlanImport.Plan, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(editSettings { items -> plan.items.fold(items) { acc, item -> Settings.upsert(acc, item.toEntity()) } })
        }
    }

    /** 明細のカテゴリを保存し、キャッシュを作り直してカテゴリを付け直す(E07-21)。 */
    fun saveCategories(categories: CategorySettings, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(
                editOnDrive { api, folders ->
                    if (CategoryStore.save(api, folders, categories)) null else "Driveに書けないので保存しない"
                },
            )
        }
    }

    /** 積立投資の目安(E10-04)。手元のキャッシュから計算する。 */
    /** 明細に付いている、種類が積立投資のカテゴリの名前(E09-05)。将来の評価額の積立額に選べる。 */
    suspend fun investmentCategories(): List<String> =
        db.bankTransactionDao().all()
            .filter { it.categoryKind == CategoryKind.INVESTMENT }
            .mapNotNull { it.category }
            .distinct()
            .sorted()

    suspend fun investPlan(): InvestPlan? {
        val monthly = Summary.monthlyCashflow(db.bankTransactionDao().all())
        return InvestPlan.of(monthly, _state.value.overviews.filterIsInstance<ItemOverview.Goal>(), LocalDate.now())
    }

    /** 手元の明細を摘要ごとにまとめたもの(E07-20・E07-21)。カテゴリを決める手がかりに、画面にだけ出す。 */
    suspend fun withdrawalDescriptions(): List<SpendingRules.Candidate> =
        SpendingRules.candidates(db.bankTransactionDao().all())

    /** リマインダーの保存(E05-05/06)。目標と同じくDriveの settings に書く。 */
    fun saveReminder(reminder: Item.Reminder, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(editSettings { Settings.upsert(it, reminder.toEntity()) }) }
    }

    /** 済みにする。繰り返すものは次の期日へ、繰り返さないものは消す。 */
    fun completeReminder(reminder: Item.Reminder, onResult: (String?) -> Unit) {
        val next = ReminderForm.completed(reminder, LocalDate.now())
        viewModelScope.launch {
            onResult(
                editSettings {
                    if (next == null) Settings.remove(it, reminder.id) else Settings.upsert(it, next.toEntity())
                },
            )
        }
    }

    /** デバッグ用: 毎日の確認を今すぐ走らせる。出した通知の数を返す。 */
    fun runDailyCheckNow(onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(DailyCheck.run(getApplication())) }
    }

    /** 目標・リマインダーを消す(settingsから外す)。 */
    fun deleteItem(id: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(editSettings { Settings.remove(it, id) }) }
    }

    private suspend fun editSettings(change: (List<ItemEntity>) -> List<ItemEntity>): String? =
        editOnDrive { api, folders ->
            // 読めないまま書くと、既存の項目の設定を全部消してしまう
            val current = Settings.load(api, folders) ?: return@editOnDrive "Driveの設定を読めないので保存しない"
            if (Settings.save(api, folders, change(current))) null else "Driveに書けないので保存しない"
        }

    /**
     * Driveにある人が入力したもの(corrections・settings)を書き換え、キャッシュを作り直す。
     *
     * **Driveに書けなければローカルも変えない。** ローカルだけ直すと、次の同期で
     * キャッシュをDriveから作り直したときに黙って消える(Driveが正)。
     * @param write 書き換える。失敗の理由を返す(成功ならnull)
     */
    private suspend fun editOnDrive(write: suspend (DriveApi, AppFolders) -> String?): String? {
        _state.update { it.copy(syncing = true) }
        val outcome = DriveSession.withDrive(getApplication()) { api ->
            val folders = DriveFolderSetup.ensure(api).folders
            write(api, folders)?.let { return@withDrive it }
            when (val cache = CacheSync.rebuild(api, folders, db)) {
                is CacheSync.Outcome.Rebuilt -> null
                is CacheSync.Outcome.Kept -> "Driveには保存したが、画面の更新に失敗: ${cache.reason}(次の同期で反映)"
            }
        }
        _state.update { it.copy(syncing = false) }
        refresh()
        return when (outcome) {
            is DriveSession.Outcome.Success -> outcome.value
            is DriveSession.Outcome.Offline -> "オフラインなので保存しない(Driveに置くため)"
            is DriveSession.Outcome.Failed -> "失敗: ${outcome.message}"
            is DriveSession.Outcome.ConsentRequired -> {
                _state.update { it.copy(consentRequest = outcome.pendingIntent) }
                "Googleの同意が必要。同意してからもう一度保存してください"
            }
        }
    }

    /** AI相談用の書き出し(E03-07)。 */
    suspend fun aiExport(): String {
        val today = LocalDate.now()
        return AiExport.build(
            today = today,
            latestDataDate = SyncStatus.load(db, today).latestDataDate,
            months = SummaryBoard.load(db, PeriodUnit.MONTH, today),
            goals = ItemOverview.load(db, today).filterIsInstance<ItemOverview.Goal>(),
        )
    }

    /** サマリー画面(E03-03)の中身。 */
    suspend fun summaries(unit: PeriodUnit): List<PeriodSummary> = SummaryBoard.load(db, unit)

    private suspend fun runSync(askConsent: Boolean) {
        _state.update { it.copy(syncing = true, message = "同期中...") }
        val outcome = DriveSession.withDrive(getApplication()) { FullSync.run(it, db) }
        val now = Instant.now()
        // 同期を試し終えたものだけ記録する。同意待ちは試し終えていないので残さない。
        val last = when (outcome) {
            is DriveSession.Outcome.Success -> LastSync.of(outcome.value, now)
            is DriveSession.Outcome.Offline -> LastSync.notSynced("オフラインでスキップ", now, isProblem = false)
            is DriveSession.Outcome.Failed -> LastSync.notSynced("失敗: ${outcome.message}", now, isProblem = true)
            is DriveSession.Outcome.ConsentRequired -> null
        }
        last?.let {
            lastSyncStore.save(it)
            _state.update { s -> s.copy(lastSync = it) }
        }
        val message = when (outcome) {
            is DriveSession.Outcome.Success -> ""
            is DriveSession.Outcome.Offline -> "オフライン: 同期をスキップしました(${outcome.message})"
            is DriveSession.Outcome.Failed -> "失敗: ${outcome.message}"
            is DriveSession.Outcome.ConsentRequired -> {
                if (askConsent) {
                    _state.update { it.copy(consentRequest = outcome.pendingIntent) }
                    "同意画面を表示中"
                } else {
                    "同意が必要"
                }
            }
        }
        _state.update { it.copy(syncing = false, message = message) }
        refresh()
    }

    private suspend fun refresh() {
        val overviews = ItemOverview.load(db)
        val status = SyncStatus.load(db)
        val metrics = db.itemDao().getAll().mapNotNull { it.toItem() as? Item.Metric }
        val hidden = metrics.filter { it.hidden }
        val netWorth = NetWorth.load(db)
        _state.update {
            it.copy(
                overviews = overviews, syncStatus = status, hiddenMetrics = hidden, netWorth = netWorth,
                seriesColors = SeriesColors.indexOf(metrics),
            )
        }
        // ウィジェットの色も同じ判定なので、キャッシュが変わるたびに描き直す(E04)
        SyncStatusWidget.refresh(getApplication())
    }
}
