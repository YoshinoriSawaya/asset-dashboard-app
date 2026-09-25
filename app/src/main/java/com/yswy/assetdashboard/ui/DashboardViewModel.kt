package com.yswy.assetdashboard.ui

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.AutoSyncPrefs
import com.yswy.assetdashboard.data.ItemDetail
import com.yswy.assetdashboard.data.PeriodSummary
import com.yswy.assetdashboard.data.PeriodUnit
import com.yswy.assetdashboard.data.SummaryBoard
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.SyncPolicy
import com.yswy.assetdashboard.data.SyncStatus
import com.yswy.assetdashboard.drive.CacheSync
import com.yswy.assetdashboard.drive.Corrections
import com.yswy.assetdashboard.drive.DriveFolderSetup
import com.yswy.assetdashboard.drive.DriveSession
import com.yswy.assetdashboard.drive.FullSync
import com.yswy.assetdashboard.drive.LastSync
import com.yswy.assetdashboard.drive.LastSyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

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
    suspend fun detail(overview: ItemOverview): ItemDetail = ItemDetail.load(db, overview)

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

    /** 補正を取り消す。CSVの点があればその値に戻り、手入力だけの点なら消える。 */
    fun deleteCorrection(metricKey: String, date: LocalDate, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(editCorrections { Corrections.remove(it, metricKey, date) })
        }
    }

    private suspend fun editCorrections(change: (List<Corrections.Entry>) -> List<Corrections.Entry>): String? {
        _state.update { it.copy(syncing = true) }
        val outcome = DriveSession.withDrive(getApplication()) { api ->
            val folders = DriveFolderSetup.ensure(api).folders
            // 読めないまま書くと、既存の補正を全部消してしまう
            val current = Corrections.load(api, folders) ?: return@withDrive "Driveの補正を読めないので保存しない"
            if (!Corrections.save(api, folders, change(current))) return@withDrive "Driveに書けないので保存しない"
            when (val cache = CacheSync.rebuild(api, folders, db)) {
                is CacheSync.Outcome.Rebuilt -> null
                is CacheSync.Outcome.Kept -> "Driveには保存したが、画面の更新に失敗: ${cache.reason}(次の同期で反映)"
            }
        }
        _state.update { it.copy(syncing = false) }
        refresh()
        return when (outcome) {
            is DriveSession.Outcome.Success -> outcome.value
            is DriveSession.Outcome.Offline -> "オフラインなので保存しない(補正はDriveに置くため)"
            is DriveSession.Outcome.Failed -> "失敗: ${outcome.message}"
            is DriveSession.Outcome.ConsentRequired -> {
                _state.update { it.copy(consentRequest = outcome.pendingIntent) }
                "Googleの同意が必要。同意してからもう一度保存してください"
            }
        }
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
        _state.update { it.copy(overviews = overviews, syncStatus = status) }
    }
}
