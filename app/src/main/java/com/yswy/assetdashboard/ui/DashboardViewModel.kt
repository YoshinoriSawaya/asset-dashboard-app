package com.yswy.assetdashboard.ui

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.AutoSyncPrefs
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.BudgetStore
import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.FundLocalStore
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.NavBase
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.drive.FundSourceStore
import com.yswy.assetdashboard.notify.NavFetcher
import com.yswy.assetdashboard.notify.FundSearch
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.GoalOrder
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemDetail
import com.yswy.assetdashboard.data.ItemEntity
import com.yswy.assetdashboard.data.CategorySettings
import com.yswy.assetdashboard.data.CategorySpending
import com.yswy.assetdashboard.data.FixedCosts
import com.yswy.assetdashboard.data.InvestPlan
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.Summary
import com.yswy.assetdashboard.data.MonthlyReview
import com.yswy.assetdashboard.data.YearlyReview
import com.yswy.assetdashboard.data.NetWorth
import com.yswy.assetdashboard.data.NetWorthOutlook
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
import java.time.YearMonth
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
    private val budgetStore = BudgetStore(app)
    private val fundStore = FundLocalStore(app)
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

    /** カテゴリの月の予算(E07-29)。端末の控えから読む。 */
    fun budgets(): Map<String, Long> = budgetStore.load()

    /**
     * カテゴリの月の予算を保存する(E07-29)。Driveの categories.json を読み直して、そのカテゴリの予算だけを変える
     * (画面が古い内容を持ったまま、ほかの決まりを上書きしないように)。nullなら予算をやめる。
     */
    fun saveBudget(category: String, yen: Long?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(
                editOnDrive { api, folders ->
                    val current = CategoryStore.load(api, folders) ?: return@editOnDrive "明細のカテゴリを読めないので保存しない"
                    if (current.categories.none { it.name == category }) return@editOnDrive "カテゴリ「$category」が見つからない"
                    if (CategoryStore.save(api, folders, current.withBudget(category, yen))) null else "Driveに書けないので保存しない"
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

    /** 前の月の振り返り(E03-10)。手元のキャッシュと端末の予算の控えから作る。 */
    suspend fun monthlyReview(): MonthlyReview? = MonthlyReview.of(
        transactions = db.bankTransactionDao().all(),
        today = LocalDate.now(),
        budgets = budgetStore.load(),
        netWorthSeries = _state.value.netWorth?.series.orEmpty(),
    )

    /** 1年の振り返り(E03-12)の選べる期間。 */
    suspend fun yearlyPeriods(): List<YearlyReview.Period> = YearlyReview.periods(db.bankTransactionDao().all(), LocalDate.now())

    /** 1年の振り返り(E03-12)。手元のキャッシュから作る。 */
    suspend fun yearlyReview(period: YearlyReview.Period): YearlyReview =
        YearlyReview.of(db.bankTransactionDao().all(), period, _state.value.netWorth?.series.orEmpty())

    /** 手元の明細の全部。カテゴリ別の支出(E07-25)に使う。 */
    suspend fun transactions(): List<BankTransactionEntity> = db.bankTransactionDao().all()

    /** ファンドごとの取得額と評価額(E01-17)。 */
    suspend fun fundHoldings(): FundHoldings = FundHoldings.of(db.metricPointDao().withPrefix(FundHoldings.PREFIX))

    /** 基準価額の取り先(E05-09)。端末の控え。 */
    fun fundSources(): List<FundSource> = fundStore.sources()

    /** 取れた最新の基準価額(E05-09)。端末の控え。 */
    fun navs(): Map<String, Nav> = fundStore.navs()

    /** 設定来の最高値(E05-12)。端末の控え(E05-13。朝の確認と「今すぐ基準価額を取る」で更新)。 */
    fun navPeaks(): Map<String, Nav> = fundStore.peaks()

    /**
     * 今すぐ基準価額を取る(E05-09)。通知はしない。取れた本数を返す。
     * 設定来の推移を取って、最新の点と最高値を控える(E05-12・E05-13。最新の1点を取るのと同じCSV)。
     */
    suspend fun refreshNavs(): Int {
        val histories = NavFetcher.fetchAllHistory(fundStore.sources())
        fundStore.saveFromHistories(histories)
        return histories.size
    }

    /**
     * ファンドの基準価額の取り先を保存する(E05-09)。Driveの funds.json を読み直して、そのファンドだけ変える。
     * ISINと協会コードが空ならやめる。形が合わなければ保存しない。
     */
    fun saveFundSource(section: String, name: String, isin: String, code: String, onResult: (String?) -> Unit) {
        val i = isin.trim().uppercase()
        val c = code.trim().uppercase()
        val remove = i.isEmpty() && c.isEmpty()
        if (!remove && (!FundSource.isValidIsin(i) || !FundSource.isValidCode(c))) {
            onResult("ISINは英数字12文字(例: JP90C000H1T1)、協会コードは英数字8文字(例: 0331418A)で入れてください")
            return
        }
        viewModelScope.launch {
            onResult(
                editOnDrive { api, folders ->
                    val current = FundSourceStore.load(api, folders) ?: return@editOnDrive "基準価額の取り先を読めないので保存しない"
                    val updated = FundSourceStore.upsert(current, FundSource(section, name, i, c), remove)
                    if (FundSourceStore.save(api, folders, updated)) null else "Driveに書けないので保存しない"
                },
            )
        }
    }

    /** ファンドの比べる基準を保存する(E05-14)。Driveの funds.json を読み直して、そのファンドだけ変える。 */
    fun saveFundBase(section: String, name: String, base: NavBase, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(
                editOnDrive { api, folders ->
                    val current = FundSourceStore.load(api, folders) ?: return@editOnDrive "基準価額の取り先を読めないので保存しない"
                    val updated = FundSourceStore.setBase(current, FundSource.keyOf(section, name), base)
                        ?: return@editOnDrive "取り先が無いので保存しない"
                    if (FundSourceStore.save(api, folders, updated)) null else "Driveに書けないので保存しない"
                },
            )
        }
    }

    /** 基準価額の推移(E05-11)。見るときに取り、端末には残さない。取れなければnull。 */
    suspend fun navHistory(source: FundSource): List<Nav>? = NavFetcher.fetchHistory(source)

    /** 基準価額の取り先をファンド名で探す(E05-10)。探せなければnull。 */
    suspend fun searchFunds(keyword: String): List<FundSearch.Candidate>? = FundSearch.search(keyword)

    /**
     * 取り先の無いファンドをまとめて名前で探し、書き方まで同じ候補がちょうど1本のものだけDriveに保存する(E05-10)。
     * 保存できたら、そのまま基準価額も取る。結果の一言を返す。
     */
    suspend fun findMissingSources(): String {
        val known = fundStore.sources().map { it.fundKey }.toSet()
        val missing = FundHoldings.of(db.metricPointDao().withPrefix(FundHoldings.PREFIX)).funds
            .filterNot { FundSource.keyOf(it.section, it.name) in known }
        if (missing.isEmpty()) return "取り先の無いファンドはありません"
        // 同じファンドを別の区分で持っていることがあるので、名前ごとに1回だけ探す
        val results = missing.map { it.name }.distinct().associateWith { FundSearch.search(it) }
        if (results.values.all { it == null }) return "投資信託協会につながらないので探せませんでした"
        val found = missing.mapNotNull { f ->
            results[f.name]?.let { FundSearch.exactMatch(f.name, it) }?.let { FundSource(f.section, f.name, it.isin, it.code) }
        }
        val rest = missing.size - found.size
        val restText = if (rest > 0) "。${rest}本は決められないので、行を開いて探してください" else ""
        if (found.isEmpty()) return "${missing.size}本とも決められませんでした。行を開いて探してください"
        val error = editOnDrive { api, folders ->
            val current = FundSourceStore.load(api, folders) ?: return@editOnDrive "基準価額の取り先を読めないので保存しない"
            if (FundSourceStore.save(api, folders, FundSourceStore.addMissing(current, found))) null else "Driveに書けないので保存しない"
        }
        if (error != null) return error
        val navs = refreshNavs()
        return "${missing.size}本のうち${found.size}本の取り先を保存し、基準価額を${navs}本取りました$restText"
    }

    /** 純資産の将来の見通し(E09-06)。純資産に数える系列が無い・明細が無ければnull。 */
    suspend fun netWorthOutlook(): NetWorthOutlook? {
        val netWorth = _state.value.netWorth ?: return null
        // 大型出費の予定(E09-07)は、隠しているリマインダーも数える(隠しても予定のうち。E09-04と同じ)
        val items = db.itemDao().getAll().mapNotNull { it.toItem() }
        return NetWorthOutlook.build(netWorth, db.bankTransactionDao().all(), LocalDate.now(), items)
    }

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
                is CacheSync.Outcome.Rebuilt -> {
                    budgetStore.save(cache.snapshot.budgets)
                    cache.snapshot.fundSources?.let { fundStore.saveSources(it) }
                    null
                }
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
        val transactions = db.bankTransactionDao().all()
        // カテゴリ別の支出は、最後のまるまる1か月(今月は途中なので使わない。E03-09)
        val lastFullMonth = CategorySpending.months(transactions).firstOrNull { it < YearMonth.from(today) }
        return AiExport.build(
            today = today,
            latestDataDate = SyncStatus.load(db, today).latestDataDate,
            months = SummaryBoard.load(db, PeriodUnit.MONTH, today),
            goals = ItemOverview.load(db, today).filterIsInstance<ItemOverview.Goal>(),
            spending = lastFullMonth?.let { CategorySpending.of(transactions, it) },
            fixedCosts = FixedCosts.of(transactions, today),
        )
    }

    /** サマリー画面(E03-03)の中身。 */
    suspend fun summaries(unit: PeriodUnit): List<PeriodSummary> = SummaryBoard.load(db, unit)

    private suspend fun runSync(askConsent: Boolean) {
        _state.update { it.copy(syncing = true, message = "同期中...") }
        val outcome = DriveSession.withDrive(getApplication()) { FullSync.run(it, db) }
        val now = Instant.now()
        // 同期を試し終えたものだけ記録する。同意待ちは試し終えていないので残さない。
        // カテゴリの予算(E07-29)を端末に控える。毎朝の確認はDriveを読まないため
        ((outcome as? DriveSession.Outcome.Success)?.value?.cache as? CacheSync.Outcome.Rebuilt)
            ?.let {
                budgetStore.save(it.snapshot.budgets)
                // 基準価額の取り先(E05-09)も端末に控える。読めなかったときは前のまま
                it.snapshot.fundSources?.let { s -> fundStore.saveSources(s) }
            }
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
