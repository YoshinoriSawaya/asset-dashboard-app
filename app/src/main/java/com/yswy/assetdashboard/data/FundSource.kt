package com.yswy.assetdashboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * ファンドの基準価額の取り先(E05-09)。投資信託協会の投信総合検索ライブラリーのISINコードと協会コード。
 * 正はDriveの `settings/funds.json`。ファンドは保有商品一覧(E01-15)の区分とファンド名で見分ける([FundHoldings.Fund])。
 */
data class FundSource(val section: String, val name: String, val isin: String, val code: String) {
    val fundKey: String get() = keyOf(section, name)

    companion object {
        fun keyOf(section: String, name: String) = "$section|$name"

        /** ISINは英数字12文字、協会コードは英数字8文字(例: JP90C000H1T1 / 0331418A)。 */
        fun isValidIsin(value: String) = Regex("[A-Z]{2}[A-Z0-9]{10}").matches(value)
        fun isValidCode(value: String) = Regex("[A-Z0-9]{8}").matches(value)
    }
}

/** ある日の基準価額(E05-09)。1万口あたりの円。 */
data class Nav(val date: LocalDate, val yen: Long)

/**
 * 基準価額の取り先と、取れた最新の基準価額・設定来の最高値の端末の控え(E05-09・E05-13)。
 * 取り先はキャッシュを作り直すたびにDriveから書き写す(予算(E07-29)と同じ)。基準価額と最高値は毎朝取り直すので、失ってもよい。
 */
class FundLocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("funds", Context.MODE_PRIVATE)

    fun sources(): List<FundSource> = decodeSources(prefs.getString(SOURCES, null))
    fun saveSources(sources: List<FundSource>) = prefs.edit().putString(SOURCES, encodeSources(sources)).apply()

    fun navs(): Map<String, Nav> = decodeNavs(prefs.getString(NAVS, null))
    fun saveNavs(navs: Map<String, Nav>) = prefs.edit().putString(NAVS, encodeNavs(navs)).apply()

    /** 設定来の最高値(E05-13)。形は基準価額と同じ。 */
    fun peaks(): Map<String, Nav> = decodeNavs(prefs.getString(PEAKS, null))
    fun savePeaks(peaks: Map<String, Nav>) = prefs.edit().putString(PEAKS, encodeNavs(peaks)).apply()

    /** 取れた推移から、最新の点と最高値を控える(E05-13)。取れなかったファンドは前の控えのまま。 */
    fun saveFromHistories(histories: Map<String, List<Nav>>) {
        saveNavs(navs() + histories.mapValues { it.value.last() })
        savePeaks(peaks() + FundNow.peaks(histories))
    }

    companion object {
        private const val SOURCES = "sources"
        private const val NAVS = "navs"
        private const val PEAKS = "peaks"

        fun encodeSources(sources: List<FundSource>): String = JSONArray().apply {
            sources.forEach { put(JSONObject().put("section", it.section).put("name", it.name).put("isin", it.isin).put("code", it.code)) }
        }.toString()

        /** 読めない行は落とす(落ちるよりスキップ)。 */
        fun decodeSources(json: String?): List<FundSource> = runCatching {
            val a = JSONArray(json ?: return emptyList())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val s = FundSource(o.optString("section"), o.optString("name"), o.optString("isin"), o.optString("code"))
                s.takeIf { it.name.isNotBlank() && FundSource.isValidIsin(it.isin) && FundSource.isValidCode(it.code) }
            }
        }.getOrDefault(emptyList())

        fun encodeNavs(navs: Map<String, Nav>): String = JSONObject().apply {
            navs.forEach { (k, v) -> put(k, JSONObject().put("date", v.date.toString()).put("yen", v.yen)) }
        }.toString()

        fun decodeNavs(json: String?): Map<String, Nav> = runCatching {
            val o = JSONObject(json ?: return emptyMap())
            o.keys().asSequence().mapNotNull { k ->
                val n = o.optJSONObject(k) ?: return@mapNotNull null
                runCatching { k to Nav(LocalDate.parse(n.getString("date")), n.getLong("yen")) }.getOrNull()
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
