package com.yswy.assetdashboard.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yswy.assetdashboard.MainActivity
import com.yswy.assetdashboard.R
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 端末の中だけで通知を出す(E05)。1日1回アラームで起き、手元のDBを見て
 * [NotificationRules]が出すと決めたものを通知する。
 *
 * ## 同期はしない
 * 起きてやるのは、ローカルのDBを読むことと通知を出すことだけ。Driveには
 * 触らない(CLAUDE.mdの「全自動ではなく、開いたときに処理する」)。
 * CSVの催促に気づいてアプリを開けば、そこで同期が走る。
 *
 * ## 決まった時刻にこだわらない
 * 正確なアラームは権限が要り電池も使う。1日1回、朝9時ごろに起きれば足りるので、
 * 省電力中も起こせる「おおよそ」のアラームで予約し、起きるたびに次を予約する。
 */
object DailyCheck {

    private const val TAG = "DailyCheck"
    const val CHANNEL_ID = "reminders"
    private val CHECK_TIME: LocalTime = LocalTime.of(9, 0)

    /** 次の確認を予約する。同じものが既にあれば置き換わる。 */
    fun schedule(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = nextCheckAt(now)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pendingIntent(context))
        Log.i(TAG, "次の確認: $next")
    }

    /** 今日の9時がまだなら今日、過ぎていれば明日の9時。 */
    fun nextCheckAt(now: ZonedDateTime): ZonedDateTime {
        val today = now.with(CHECK_TIME).withSecond(0).withNano(0)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    /** 確認して、出すべき通知を出す。出した数を返す。 */
    suspend fun run(context: Context, today: LocalDate = LocalDate.now()): Int {
        val db = AppDatabase.get(context)
        val store = NotifyStore(context)
        val notices = NotificationRules.evaluate(
            today = today,
            sync = SyncStatus.load(db, today),
            overviews = ItemOverview.load(db, today),
            lastNotified = store.load(),
        )
        if (!canNotify(context)) {
            // 許可が無ければ出せない。記録もしない(許可されたら出せるように)
            Log.i(TAG, "通知の許可が無いので出さない(${notices.size}件)")
            return 0
        }
        ensureChannel(context)
        notices.forEach { post(context, it) }
        store.markNotified(notices.map { it.key }, today)
        Log.i(TAG, "通知 ${notices.size}件")
        return notices.size
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "催促・リマインダー", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "CSVの同期の催促、点検・更新の期日、目標や枠の知らせ"
            },
        )
    }

    private fun post(context: Context, notice: NotificationRules.Notice) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // 通知の種類ごとに1つ。同じ種類は上書きする(催促が何通も溜まらない)
            NotificationManagerCompat.from(context).notify(notice.key.substringBeforeLast(':').hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "通知を出せなかった", e)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, DailyCheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** アラームで起きたとき。確認して、次を予約する。 */
class DailyCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                DailyCheck.run(context)
            } finally {
                DailyCheck.schedule(context)
                pending.finish()
            }
        }
    }
}

/**
 * 再起動・アプリの更新のあと、アラームの予約は消えている。予約し直す。
 * (予約は端末の中の状態で、Driveから作り直す対象ではない)
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyCheck.schedule(context)
    }
}
