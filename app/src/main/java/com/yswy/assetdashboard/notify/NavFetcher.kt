package com.yswy.assetdashboard.notify

import android.util.Log
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.time.LocalDate

/**
 * 基準価額を投資信託協会の投信総合検索ライブラリーから取る(E05-09)。本人が「端末が公開データから取る」を選んだ。
 *
 * ファンドごとのCSV(Shift_JIS)を取り、いちばん新しい行の基準価額を読む。形は
 * `年月日,基準価額(円),純資産総額（百万円）,分配金,決算期` / `2026年09月25日,38325,14014518,,`。
 *
 * 通信や形がおかしければ、そのファンドは飛ばす(落ちるよりスキップ。次の朝にまた取る)。
 */
object NavFetcher {

    private const val TAG = "NavFetcher"
    private const val TIMEOUT_MS = 15_000

    fun url(source: FundSource) =
        "https://toushin-lib.fwg.ne.jp/FdsWeb/FDST030000/csv-file-download?isinCd=${source.isin}&associFundCd=${source.code}"

    /** ファンドごとの設定来の推移を同時に取る(E05-12。朝の確認もこれ: E05-13)。取れて1点以上あるものだけ返す(鍵は [FundSource.fundKey])。 */
    suspend fun fetchAllHistory(sources: List<FundSource>): Map<String, List<Nav>> = coroutineScope {
        sources.map { s -> async { fetchHistory(s)?.takeIf { it.isNotEmpty() }?.let { s.fundKey to it } } }
            .awaitAll().filterNotNull().toMap()
    }

    /** 設定来の基準価額の推移(E05-11)。見るときに取る(本人が選んだ。端末には残さない)。取れなければnull。 */
    suspend fun fetchHistory(source: FundSource): List<Nav>? = download(source)?.let { parseAll(it) }

    /** ファンドのCSVを取る。通信や形がおかしければnull。 */
    private suspend fun download(source: FundSource): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url(source)).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "asset-dashboard")
            try {
                if (conn.responseCode != 200) {
                    Log.w(TAG, "基準価額を取れなかった: HTTP ${conn.responseCode}")
                    return@withContext null
                }
                String(conn.inputStream.readBytes(), Charset.forName("MS932"))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "基準価額を取れなかった", e)
            null
        }
    }

    private val DATE = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""")

    /** CSVのいちばん新しい日付の行の基準価額。読めなければnull。 */
    fun parseLatest(text: String): Nav? = parseAll(text).lastOrNull()

    /** CSVの読める行を日付順に。同じ日が2行あれば後の行。読めない行は飛ばす。 */
    fun parseAll(text: String): List<Nav> = text.lineSequence().mapNotNull { line ->
        val cells = line.split(",")
        val m = DATE.find(cells.firstOrNull().orEmpty()) ?: return@mapNotNull null
        val yen = cells.getOrNull(1)?.trim()?.toLongOrNull() ?: return@mapNotNull null
        val date = runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        date?.let { Nav(it, yen) }
    }.associateBy { it.date }.values.sortedBy { it.date }
}
