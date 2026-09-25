package com.yswy.assetdashboard

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.AutoSyncPrefs
import com.yswy.assetdashboard.data.SyncPolicy
import com.yswy.assetdashboard.data.SyncStatus
import com.yswy.assetdashboard.drive.CacheSync
import com.yswy.assetdashboard.drive.CacheSync.summary
import com.yswy.assetdashboard.drive.DriveFolderSetup
import com.yswy.assetdashboard.drive.DriveSession
import com.yswy.assetdashboard.drive.InboxSync
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssetDashboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Placeholder(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * E03でダッシュボード本体に置き換わる暫定画面。
 * 今はDrive連携(E01)と、開いたときの同期判定(E02-04)の確認ができる。
 */
@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf<SyncStatus?>(null) }

    /**
     * 同期して結果を出す。
     * @param onConsent 同意画面が要るときの起動方法。同意から戻った直後の
     *   再試行ではnull(もう一度同意画面を出して無限に回さない)。
     */
    suspend fun runSync(onConsent: ((PendingIntent) -> Unit)?) {
        status = "同期中..."
        val outcome = DriveSession.withDrive(context) { syncInbox(context, it) }
        status = when (outcome) {
            is DriveSession.Outcome.Success -> outcome.value
            is DriveSession.Outcome.Offline ->
                "オフライン: 同期をスキップしました\n(${outcome.message})"
            is DriveSession.Outcome.Failed -> "失敗: ${outcome.message}"
            is DriveSession.Outcome.ConsentRequired ->
                if (onConsent != null) {
                    onConsent(outcome.pendingIntent)
                    "同意画面を表示中"
                } else {
                    "同意が必要"
                }
        }
        syncStatus = SyncStatus.load(AppDatabase.get(context))
    }

    // 同意画面から戻ってきたら、そのまま同期をやり直す。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        scope.launch { runSync(onConsent = null) }
    }
    val launchConsent: (PendingIntent) -> Unit = { intent ->
        consentLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    // 開いたときに「同期が必要か」を判定し、必要なら同期する(E02-04)。
    // バックグラウンドの定期実行には頼らない(CLAUDE.md)。
    LaunchedEffect(Unit) {
        val current = SyncStatus.load(AppDatabase.get(context))
        syncStatus = current

        val prefs = AutoSyncPrefs(context)
        val now = Instant.now()
        val auto = SyncPolicy.shouldAutoSync(
            latestDataDate = current.latestDataDate,
            today = LocalDate.now(),
            lastAutoSyncAt = prefs.lastAttemptAt,
            now = now,
        )
        if (auto) {
            // 試した時点で記録する。失敗しても1時間は自動では試さない。
            prefs.lastAttemptAt = now
            runSync(launchConsent)
        } else {
            status = if (current.isDue) {
                "自動同期は1時間以内に試したので見送り"
            } else {
                "期限内なので同期しない"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "資産ダッシュボード",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
            style = MaterialTheme.typography.labelSmall,
        )
        syncStatus?.let {
            Text(text = it.describe(), style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = { scope.launch { runSync(launchConsent) } }) {
            Text("Driveと同期")
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** フォルダを用意してinboxを一周し、Driveの中身でキャッシュを作り直す。 */
private suspend fun syncInbox(
    context: Context,
    api: com.yswy.assetdashboard.drive.DriveApi,
): String {
    val about = api.about()
    val setup = DriveFolderSetup.ensure(api)
    val folderNote = if (setup.created.isEmpty()) {
        "フォルダは作成済み"
    } else {
        "作成: ${setup.created.joinToString(", ")}"
    }

    val db = AppDatabase.get(context)
    val report = InboxSync.run(api, setup.folders, db.ingestedFileDao())

    // 取り込みで増えたbackupも含めて作り直す。取り込みが全部失敗していても、
    // 既存のbackupからキャッシュは作れるので必ず走らせる。
    val cache = CacheSync.rebuild(api, setup.folders, db)

    val inboxNote = buildString {
        append(report.summary())
        report.entries.forEach { entry ->
            append("\n・${entry.fileName}")
            append("\n　 ${entry.status.label}: ${entry.detail}")
        }
    }

    return "接続OK: ${about.email}\n$folderNote\n$inboxNote\n${cache.summary()}"
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    AssetDashboardTheme {
        Placeholder()
    }
}
