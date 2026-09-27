package com.yswy.assetdashboard.notify

import android.util.Log
import com.yswy.assetdashboard.data.FundSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * 基準価額の取り先(ISIN・協会コード)を、ファンド名で投信総合検索ライブラリーから探す(E05-10)。
 * 基準価額を取る先([NavFetcher])と同じサイトの、画面の「ファンド名で検索」が使っている検索を呼ぶ。
 *
 * サイトの検索は全角半角と空白の違いを気にせず、空白で区切った語をすべて含むものを返す。
 * 保有商品一覧のファンド名はサイトの正式名称と同じ書き方なので、名前をそのまま渡せば当たる。
 */
object FundSearch {

    private const val TAG = "FundSearch"
    private const val TIMEOUT_MS = 15_000
    private const val URL_SEARCH = "https://toushin-lib.fwg.ne.jp/FdsWeb/FDST999900/fundDataSearch"

    /** 候補1件。name は正式名称、company は運用会社。 */
    data class Candidate(val isin: String, val code: String, val name: String, val company: String, val shortName: String = "")

    /** 見つかった候補(先頭の1ページ、20件まで)。通信や形がおかしければnull(「0件」と区別する)。 */
    suspend fun search(keyword: String): List<Candidate>? = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        try {
            val conn = URL(URL_SEARCH).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "asset-dashboard")
            try {
                conn.outputStream.use { it.write(requestBody(keyword).toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode != 200) {
                    Log.w(TAG, "ファンドを探せなかった: HTTP ${conn.responseCode}")
                    return@withContext null
                }
                parse(String(conn.inputStream.readBytes(), Charsets.UTF_8))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "ファンドを探せなかった", e)
            null
        }
    }

    /** 検索区分1 = ファンド名で検索。ほかの条件は付けない。 */
    fun requestBody(keyword: String): String = JSONObject()
        .put("s_keyword", keyword.trim())
        .put("s_kensakuKbn", "1")
        .put("startNo", 0)
        .put("draw", 1)
        .put("searchBtnClickFlg", true)
        .toString()

    /** 応答から候補を読む。コードの形が合わない行は落とす。読めなければnull。 */
    fun parse(json: String): List<Candidate>? = runCatching {
        val info = JSONObject(json).getJSONObject("searchResultInfo")
        val rows = info.optJSONArray("resultInfoMapList") ?: return@runCatching emptyList()
        (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            Candidate(
                isin = o.optString("isinCd").trim(),
                code = o.optString("associFundCd").trim(),
                name = o.optString("fundNm").trim(),
                company = o.optString("entrustCmpNm").trim(),
                shortName = o.optString("fundStNm").trim(),
            ).takeIf { it.name.isNotEmpty() && FundSource.isValidIsin(it.isin) && FundSource.isValidCode(it.code) }
        }
    }.getOrNull()

    /** 名前の比べ方: 全角半角をそろえ(NFKC)、空白を除く。サイトの検索と同じくらいの寛容さ。 */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC).filterNot { it.isWhitespace() }

    /** 保有商品一覧の名前と書き方まで同じ候補がちょうど1本ならそれ。無い・複数なら決めない(人が選ぶ)。 */
    fun exactMatch(name: String, candidates: List<Candidate>): Candidate? {
        val n = normalize(name)
        val hits = candidates.filter { normalize(it.name) == n || (it.shortName.isNotEmpty() && normalize(it.shortName) == n) }
        return hits.distinctBy { it.isin }.singleOrNull()
    }
}
