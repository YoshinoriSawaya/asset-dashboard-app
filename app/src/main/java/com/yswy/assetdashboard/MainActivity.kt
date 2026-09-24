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
import com.yswy.assetdashboard.drive.DriveApi
import com.yswy.assetdashboard.drive.DriveAuth
import com.yswy.assetdashboard.csv.CsvIngest
import com.yswy.assetdashboard.csv.ParsedData
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.drive.DriveFolderSetup
import com.yswy.assetdashboard.drive.InboxScanner
import kotlinx.coroutines.launch
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme

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
    var status by remember { mutableStateOf("未接続") }

    // 同意画面から戻ってきたら、もう一度authorizeし直してトークンを取る。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        scope.launch {
            status = when (val result = DriveAuth.authorize(context)) {
                is DriveAuth.Result.Authorized -> setUpDrive(context, result.accessToken)
                is DriveAuth.Result.ConsentRequired -> "同意が完了しなかった"
                is DriveAuth.Result.Failed -> "失敗: ${result.message}"
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

        Button(
            onClick = {
                status = "接続中..."
                scope.launch {
                    when (val result = DriveAuth.authorize(context)) {
                        is DriveAuth.Result.Authorized -> status = setUpDrive(context, result.accessToken)
                        is DriveAuth.Result.ConsentRequired -> {
                            status = "同意画面を表示中"
                            consentLauncher.launch(
                                IntentSenderRequest.Builder(result.pendingIntent.intentSender)
                                    .build(),
                            )
                        }
                        is DriveAuth.Result.Failed -> status = "失敗: ${result.message}"
                    }
                }
            },
        ) {
            Text("Driveに接続")
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 認可できたらアカウントを確認し、フォルダ構成を用意して、
 * inboxに未取り込みのファイルがあるかまで見る。
 * 落ちるより理由を画面に出すほうを優先する。
 */
private suspend fun setUpDrive(context: Context, accessToken: String): String {
    val api = DriveApi(accessToken)
    return runCatching {
        val about = api.about()
        val outcome = DriveFolderSetup.ensure(api)
        val folderNote = if (outcome.created.isEmpty()) {
            "フォルダは作成済み"
        } else {
            "作成: ${outcome.created.joinToString(", ")}"
        }

        val dao = AppDatabase.get(context).ingestedFileDao()
        val scan = InboxScanner.scan(api, outcome.folders, dao)

        val inboxNote = buildString {
            append("inbox: 未取り込み ${scan.pending.size}件")
            if (scan.alreadyIngested.isNotEmpty()) {
                append(" / 取り込み済みが残留 ${scan.alreadyIngested.size}件")
            }
            for (file in scan.pending) {
                append("\n")
                append(describe(CsvIngest.read(api, file)))
            }
        }

        "接続OK: ${about.email}\n$folderNote\n$inboxNote"
    }.getOrElse { e -> "失敗: ${e.message}" }
}

/** 取り込み結果を1行にまとめる。E03でちゃんとした画面にする。 */
private fun describe(outcome: CsvIngest.Outcome): String = when (outcome) {
    is CsvIngest.Outcome.Parsed -> buildString {
        val r = outcome.result
        append("・${outcome.file.name}: ${r.adapterId}")
        // 推測で読んだことは隠さない。数字を鵜呑みにされると困る。
        if (outcome.viaFallback) append("(推測)")

        when (val data = r.data) {
            is ParsedData.Transactions -> {
                append(" ${data.rows.size}行")
                if (r.skipped.isNotEmpty()) append(" (読めず${r.skipped.size}行)")
                data.dateRange?.let { append("\n　 ${it.start} 〜 ${it.endInclusive}") }
                data.latestBalance?.let { append(" 残高 ${yen(it)}") }
            }

            is ParsedData.Metrics -> {
                append(" ${data.points.size}点")
                if (r.skipped.isNotEmpty()) append(" (読めず${r.skipped.size}行)")
                data.dateRange?.let { append("\n　 ${it.start} 〜 ${it.endInclusive}") }
                data.keys.forEach { key ->
                    data.latest(key)?.let { append("\n　 $key ${yen(it)}") }
                }
            }
        }
    }

    is CsvIngest.Outcome.UnknownFormat ->
        "・${outcome.file.name}: 未知のフォーマット (${outcome.header.size}列)"

    is CsvIngest.Outcome.Failed ->
        "・${outcome.file.name}: ${outcome.reason}"
}

private fun yen(value: Long): String = "%,d円".format(value)

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    AssetDashboardTheme {
        Placeholder()
    }
}
