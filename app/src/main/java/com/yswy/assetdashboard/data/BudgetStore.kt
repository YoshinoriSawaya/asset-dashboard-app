package com.yswy.assetdashboard.data

import android.content.Context
import org.json.JSONObject

/**
 * カテゴリの月の予算(E07-29)の端末の控え。正はDriveの `settings/categories.json`。
 *
 * 毎朝の確認(E05)は同期しないのでDriveを読めない。同期・カテゴリの保存でキャッシュを作り直したときに、
 * ここへ書き写しておく(キャッシュと同じく、失っても次の同期で戻る)。
 */
class BudgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("budgets", Context.MODE_PRIVATE)

    fun load(): Map<String, Long> = decode(prefs.getString(KEY, null))

    fun save(budgets: Map<String, Long>) {
        prefs.edit().putString(KEY, encode(budgets)).apply()
    }

    companion object {
        private const val KEY = "budgets"

        fun encode(budgets: Map<String, Long>): String =
            JSONObject().apply { budgets.forEach { (name, yen) -> put(name, yen) } }.toString()

        /** 読めなければ空(予算なし)。落ちるよりスキップ。 */
        fun decode(json: String?): Map<String, Long> = runCatching {
            val o = JSONObject(json ?: return emptyMap())
            o.keys().asSequence().mapNotNull { k -> o.optLong(k).takeIf { it > 0 }?.let { k to it } }.toMap()
        }.getOrDefault(emptyMap())
    }
}
