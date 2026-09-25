package com.yswy.assetdashboard.drive

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * 生活費から除く出金の決まり(E07-06)。Driveの `settings/spending_rules.json` が正。
 *
 * 振替やカードの引き落としは出金だが、生活費ではない。摘要にこの言葉を
 * 含む出金を、生活費の計算から外す。人が画面で登録する。
 *
 * 形式と扱いは[Settings]・[Corrections]に揃えてある。
 */
object SpendingRules {

    private const val TAG = "SpendingRules"
    private const val FILE_NAME = "spending_rules.json"

    const val FORMAT_VERSION = 1

    /**
     * 摘要が[keywords]のどれかを含むか。
     *
     * 全角・半角、大文字・小文字、空白の違いは無視する。銀行のCSVは
     * `ﾌﾘｶｴ` のような半角カナで出てくることが多く、人は `フリカエ` と
     * 入れるので、そのまま比べると当たらない。
     */
    fun matches(description: String, keywords: List<String>): Boolean {
        val text = normalize(description)
        return keywords.map(::normalize).any { it.isNotEmpty() && text.contains(it) }
    }

    /** NFKCで半角カナ→全角、全角英数→半角にそろえ、空白を落として小文字にする。 */
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s"), "").lowercase()

    /** Driveから読む。無ければ空、読めなければnull(空と区別する。[Settings.load]と同じ)。 */
    suspend fun load(api: DriveApi, folders: AppFolders): List<String>? {
        val fileId = try {
            api.findFile(FILE_NAME, folders.settings) ?: return emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "spending_rulesを探せなかった", e)
            return null
        }
        return try {
            parse(String(api.download(fileId), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "spending_rulesを読めなかった", e)
            null
        }
    }

    suspend fun save(api: DriveApi, folders: AppFolders, keywords: List<String>): Boolean = try {
        api.putTextFile(FILE_NAME, folders.settings, render(keywords), "application/json")
        true
    } catch (e: Exception) {
        Log.w(TAG, "spending_rulesを書けなかった", e)
        false
    }

    fun render(keywords: List<String>): String = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("excludeKeywords", JSONArray().apply { keywords.forEach { put(it) } })
        .toString(2)

    /** 空の言葉は落とす(全部の出金に当たってしまう)。 */
    fun parse(json: String): List<String> {
        val array = JSONObject(json).optJSONArray("excludeKeywords") ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
