package com.yswy.assetdashboard

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
 * 今はDrive認証(E01-01)の疎通確認だけができる。
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
                is DriveAuth.Result.Authorized -> verify(result.accessToken)
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
                        is DriveAuth.Result.Authorized -> status = verify(result.accessToken)
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

/** トークンが実際にDrive APIで通るかまで確かめる。 */
private suspend fun verify(accessToken: String): String =
    when (val about = DriveApi(accessToken).about()) {
        is DriveApi.AboutResult.Success -> "接続OK: ${about.email}"
        is DriveApi.AboutResult.Failed -> "トークンは取れたがAPIで失敗: ${about.message}"
    }

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    AssetDashboardTheme {
        Placeholder()
    }
}
