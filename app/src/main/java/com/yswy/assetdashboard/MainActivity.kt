package com.yswy.assetdashboard

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
import com.yswy.assetdashboard.drive.CacheSync
import com.yswy.assetdashboard.drive.CacheSync.summary
import com.yswy.assetdashboard.drive.DriveFolderSetup
import com.yswy.assetdashboard.drive.DriveSession
import com.yswy.assetdashboard.drive.InboxSync
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme
import kotlinx.coroutines.launch

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
 * 今はDrive連携(E01)の疎通確認だけができる。
 */
@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("未同期") }

    suspend fun sync() {
        status = when (val outcome = DriveSession.withDrive(context) { syncInbox(context, it) }) {
            is DriveSession.Outcome.Success -> outcome.value
            is DriveSession.Outcome.Offline ->
                "オフライン: 同期をスキップしました\n(${outcome.message})"
            is DriveSession.Outcome.Failed -> "失敗: ${outcome.message}"
            // 同意画面の起動は呼び出し元で扱う
            is DriveSession.Outcome.ConsentRequired -> "同意が必要"
        }
    }

    // 同意画面から戻ってきたら、そのまま同期をやり直す。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        scope.launch { sync() }
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

        Button(
            onClick = {
                status = "同期中..."
                scope.launch {
                    val outcome = DriveSession.withDrive(context) { syncInbox(context, it) }
                    if (outcome is DriveSession.Outcome.ConsentRequired) {
                        status = "同意画面を表示中"
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build(),
                        )
                    } else {
                        status = when (outcome) {
                            is DriveSession.Outcome.Success -> outcome.value
                            is DriveSession.Outcome.Offline ->
                                "オフライン: 同期をスキップしました\n(${outcome.message})"
                            is DriveSession.Outcome.Failed -> "失敗: ${outcome.message}"
                            else -> status
                        }
                    }
                }
            },
        ) {
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
