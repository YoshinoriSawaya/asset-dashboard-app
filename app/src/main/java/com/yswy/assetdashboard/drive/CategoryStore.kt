package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.data.Category
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.CategoryRule
import com.yswy.assetdashboard.data.CategorySettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * 明細のカテゴリ(E07-21)を、Driveの `settings/categories.json` に読み書きする。
 * 人が決めたもので作り直せないので、settingsに置く(docs/architecture.md)。
 *
 * ## 前の「生活費から除く言葉」を引き継ぐ
 * categories.json がまだ無ければ、`spending_rules.json`(E07-06)の言葉を「生活費以外」の
 * カテゴリとして読む。一度保存すれば categories.json が正になり、spending_rules.json は読まなくなる
 * (消さずに残す。戻したくなったときのため)。
 *
 * 形式と扱いは[Settings]にそろえた: `formatVersion` 付きで丸ごと書き戻し、読めない行は落とす。
 */
object CategoryStore {

    private const val TAG = "CategoryStore"
    private const val FILE_NAME = "categories.json"
    const val FORMAT_VERSION = 1

    /** 読めなければnull(空と区別する。空で上書きすると全部消えたように見える)。 */
    suspend fun load(api: DriveApi, folders: AppFolders): CategorySettings? {
        val fileId = try {
            api.findFile(FILE_NAME, folders.settings)
        } catch (e: Exception) {
            Log.w(TAG, "categoriesを探せなかった", e)
            return null
        }
        if (fileId == null) {
            // まだ無い: 前の除く言葉から作る
            val legacy = SpendingRules.load(api, folders) ?: return null
            return CategorySettings.fromLegacy(legacy)
        }
        return try {
            parse(String(api.download(fileId), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "categoriesを読めなかった", e)
            null
        }
    }

    suspend fun save(api: DriveApi, folders: AppFolders, settings: CategorySettings): Boolean = try {
        api.putTextFile(FILE_NAME, folders.settings, render(settings), "application/json")
        true
    } catch (e: Exception) {
        Log.w(TAG, "categoriesを書けなかった", e)
        false
    }

    fun render(settings: CategorySettings): String = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("categories", JSONArray().apply {
            settings.categories.forEach { put(JSONObject().put("name", it.name).put("kind", it.kind.name)) }
        })
        .put("rules", JSONArray().apply {
            settings.rules.forEach { put(JSONObject().put("keyword", it.keyword).put("category", it.category)) }
        })
        .toString(2)

    /** 読めるものだけ返す。知らない種類のカテゴリ、名前の無いカテゴリの決まりは落とす。 */
    fun parse(json: String): CategorySettings {
        val root = JSONObject(json)
        val categories = root.optJSONArray("categories").objects().mapNotNull { o ->
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = runCatching { CategoryKind.valueOf(o.optString("kind")) }.getOrNull() ?: return@mapNotNull null
            Category(name, kind)
        }.distinctBy { it.name }
        val names = categories.map { it.name }.toSet()
        val rules = root.optJSONArray("rules").objects().mapNotNull { o ->
            val keyword = o.optString("keyword").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val category = o.optString("category").takeIf { it in names } ?: return@mapNotNull null
            CategoryRule(keyword, category)
        }
        return CategorySettings(categories, rules)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
