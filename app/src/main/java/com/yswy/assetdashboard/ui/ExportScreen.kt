package com.yswy.assetdashboard.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * AI相談用の書き出し(E03-07)。コピーする前に、中身をそのまま見せる。
 * 何を貼ることになるのかを本人が確かめてから渡せるように。
 */
@Composable
fun ExportScreen(text: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("AI相談用に書き出す", style = MaterialTheme.typography.headlineSmall)
        Text(
            "金額・系列名・推移だけを書き出します。明細の摘要(振込相手の名前など)や" +
                "口座の情報は入れません。どこにも送信しないので、コピーして貼る先を選んでください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (text == null) {
            Text("作成中...")
            return@Column
        }
        Button(onClick = {
            copySensitive(context, text)
            message = "コピーしました(${text.length}文字)"
        }) { Text("コピー") }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
}

/**
 * クリップボードに入れる。資産額なので「機密」の印を付け、Android 13以降で
 * コピー直後に出るプレビューに中身を表示させない。
 */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("資産の状況", text)
    val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
    clip.description.extras = PersistableBundle().apply { putBoolean(sensitiveKey, true) }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
