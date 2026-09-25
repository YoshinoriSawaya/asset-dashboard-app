package com.yswy.assetdashboard.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yswy.assetdashboard.MainActivity
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ホーム画面のウィジェット(E04)。**同期が必要かどうかだけ**を色で示す。
 *
 * 資産額などの数値は出さない(CLAUDE.mdの「ウィジェットは通知専用」)。
 * 人前でホーム画面を開いても金額が見えない。詳細はタップしてアプリで見る。
 *
 * 色だけでは伝わらない人・場面があるので、短い言葉を添える(平常「資産」、要同期「同期して」)。
 *
 * 要同期の色は `error`(濃い赤)。最初は `errorContainer` にしたが、淡いピンクで
 * ホーム画面の壁紙に紛れて目に留まらなかった。気づかせるのが役目なので濃くした。
 */
class SyncStatusWidget : GlanceAppWidget() {

    /**
     * 描く前に、判定をウィジェットの状態に書き込んでおく。
     *
     * ## 判定は状態に置き、描くときにそこから読む
     * 最初は判定を `provideGlance` の中でローカル変数に持っていたが、
     * **同期して期限内に戻っても赤のままだった**。Glanceは描画のセッションを
     * しばらく生かしておき、その間の `update` は再合成だけで `provideGlance` を
     * 呼び直さない。ローカル変数は古いまま残る。
     * 状態(`updateAppWidgetState`)に書き、合成の中で `currentState` から読めば、
     * どちらの経路でも新しい判定が出る。
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 6時間ごとの描き直し(アプリを開いていないとき)はここを通る
        writeState(context, id)
        provideContent {
            GlanceTheme { Content(WidgetState.of(currentState<Preferences>()[IS_DUE] ?: true)) }
        }
    }

    @Composable
    private fun Content(state: WidgetState) {
        val colors = GlanceTheme.colors
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(if (state.isDue) colors.error else colors.secondaryContainer)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { contentDescription = state.description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.label,
                style = TextStyle(
                    color = if (state.isDue) colors.onError else colors.onSecondaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }

    companion object {
        /**
         * 置かれている全ウィジェットを描き直す。同期やキャッシュの更新のあとに呼ぶ。
         * ウィジェットが1つも無ければ何もしない。
         */
        suspend fun refresh(context: Context) {
            runCatching {
                val widget = SyncStatusWidget()
                GlanceAppWidgetManager(context).getGlanceIds(SyncStatusWidget::class.java).forEach { id ->
                    writeState(context, id)
                    widget.update(context, id)
                }
            }
        }

        private val IS_DUE = booleanPreferencesKey("is_due")

        /** 判定はアプリと同じ(E02-04)。「データの新しさ」なので、開いただけでは赤は消えない。 */
        private suspend fun writeState(context: Context, id: GlanceId) {
            val isDue = SyncStatus.load(AppDatabase.get(context)).isDue
            updateAppWidgetState(context, id) { it[IS_DUE] = isDue }
        }

        private fun component(context: Context) = ComponentName(context, SyncStatusWidgetReceiver::class.java)

        /** ホーム画面にもう置いてあるか。 */
        fun isPlaced(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

        /**
         * ホーム画面に置くよう頼む(E04-01)。ランチャーが確認の画面を出す。
         *
         * ウィジェット一覧から探す手間を省くため。アプリ名が日本語で、
         * 一覧の検索で英字では見つからない。
         * @return ランチャーが対応していなければfalse(一覧から置いてもらう)
         */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(component(context), null, null)
        }
    }
}

/** ウィジェットに出すもの。数値は含めない。 */
data class WidgetState(val isDue: Boolean, val label: String, val description: String) {
    companion object {
        fun of(status: SyncStatus): WidgetState = of(status.isDue)

        fun of(isDue: Boolean): WidgetState =
            if (isDue) {
                WidgetState(true, "同期して", "資産ダッシュボード: 同期が必要です。タップして開く")
            } else {
                WidgetState(false, "資産", "資産ダッシュボード: 同期は不要です。タップして開く")
            }
    }
}

class SyncStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SyncStatusWidget()

    /**
     * 6時間ごとの描き直し(E04-02)。アプリを開いていなくても、データが古くなれば赤にする。
     *
     * 標準の処理(`update`)に任せると、描画のセッションが生きている間は判定が
     * 書き直されず、古い色のままになる(エミュレータで確認)。
     * 判定を書き直してから描き直す[SyncStatusWidget.refresh]を通す。
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SyncStatusWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
