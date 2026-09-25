package com.yswy.assetdashboard.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * アプリのロック(E06-01)。開いたとき、しばらく裏にいたあとに認証を求める。
 *
 * ## 認証は端末のものを使う
 * 生体認証か、端末の画面ロック(PIN・パターン・パスワード)。アプリ専用のPINは
 * 作らない。覚えるものが増え、忘れたら自分のデータを見られなくなる。
 * 端末の画面ロックなら、忘れても端末側で戻せる。
 *
 * ## ロックの状態はプロセスの間だけ持つ
 * 画面の回転などでActivityが作り直されても、ロックし直さないように、
 * Activityではなくこのオブジェクトに持つ。アプリのプロセスが終われば、
 * 次に開いたときはロックから始まる。
 */
object AppLock {

    /** 裏にいてもロックし直さない時間。 */
    const val RELOCK_AFTER_MILLIS = 60_000L

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    var unlocked: Boolean = false
        private set
    private var backgroundAt: Long? = null

    /** 裏に回ったとき(onStop)。 */
    fun onBackground(now: Long = System.currentTimeMillis()) {
        backgroundAt = now
    }

    /** 表に戻ったとき(onStart)。長く裏にいたならロックし直す。 */
    fun onForeground(now: Long = System.currentTimeMillis()) {
        val since = backgroundAt ?: return
        if (shouldRelock(since, now)) unlocked = false
        backgroundAt = null
    }

    fun shouldRelock(backgroundAt: Long, now: Long): Boolean = now - backgroundAt >= RELOCK_AFTER_MILLIS

    /** この端末でロックをかけられるか。画面ロックが無ければかけられない。 */
    fun canLock(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** 認証を求める。成功したら[onUnlocked]。キャンセル・失敗なら何もしない(ロックのまま)。 */
    fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    onUnlocked()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("資産ダッシュボード")
                .setSubtitle("ロックを解除してください")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    /** ロックを使わない、または使えない端末では、最初から開いている扱いにする。 */
    fun markUnlocked() {
        unlocked = true
    }

    /** テスト用: 最初の状態に戻す。 */
    internal fun reset() {
        unlocked = false
        backgroundAt = null
    }
}
