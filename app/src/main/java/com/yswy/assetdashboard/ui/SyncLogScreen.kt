package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.drive.LastSync

/**
 * 前回の同期の詳細(E03-05)。ファイルごとの結果と、読めなかった行の
 * 行番号・理由(Driveのlogsと同じ中身)。
 */
@Composable
fun SyncLogScreen(lastSync: LastSync?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text("前回の同期", style = MaterialTheme.typography.headlineSmall)
        if (lastSync == null) {
            Text("まだ同期していません")
            return@Column
        }
        Text(lastSync.line(), style = MaterialTheme.typography.bodyMedium)
        if (lastSync.hasProblem) {
            Text(
                "問題のあったファイルはinboxに残っています。直して置き直すと次の同期で読み直します。" +
                    "同じ内容はDriveの logs にもあります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 行番号で揃えて読めるよう等幅にする
        Text(lastSync.detail, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
}
