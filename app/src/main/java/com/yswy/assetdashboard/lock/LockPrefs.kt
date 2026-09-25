package com.yswy.assetdashboard.lock

import android.content.Context

/** アプリのロックを使うか(E06-01)。端末の中だけの設定。既定は使う。 */
class LockPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY, true)
        set(value) {
            prefs.edit().putBoolean(KEY, value).apply()
        }

    private companion object {
        const val KEY = "enabled"
    }
}
