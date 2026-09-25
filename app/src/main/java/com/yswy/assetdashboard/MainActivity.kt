package com.yswy.assetdashboard

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.yswy.assetdashboard.lock.AppLock
import com.yswy.assetdashboard.lock.LockPrefs
import com.yswy.assetdashboard.ui.AppRoot
import com.yswy.assetdashboard.ui.theme.AssetDashboardTheme

/**
 * 唯一の画面の入れ物。アプリのロック(E06-01)もここで扱う。
 *
 * BiometricPromptがFragmentActivityを要るので、ComponentActivityではなくこちらを継ぐ。
 */
class MainActivity : FragmentActivity() {

    /** ロックで中身を隠しているか。 */
    private var locked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 最近使ったアプリの一覧に、画面の中身(金額)のサムネイルを出さない
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)

        setContent {
            AssetDashboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (locked) {
                        LockedScreen(onUnlock = ::requestUnlock, modifier = Modifier.padding(innerPadding))
                    } else {
                        AppRoot(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground()
        val prefs = LockPrefs(this)
        // ロックを使わない、または端末に画面ロックが無ければ、ロックしない
        if (!prefs.enabled || !AppLock.canLock(this)) AppLock.markUnlocked()
        locked = !AppLock.unlocked
        if (locked) requestUnlock()
    }

    override fun onStop() {
        super.onStop()
        // 画面の回転でも呼ばれるが、1分以内に戻ればロックし直さない
        AppLock.onBackground()
    }

    private fun requestUnlock() {
        AppLock.prompt(this) { locked = false }
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("資産ダッシュボード", style = MaterialTheme.typography.headlineSmall)
        Text("ロックされています", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onUnlock) { Text("ロックを解除") }
    }
}
