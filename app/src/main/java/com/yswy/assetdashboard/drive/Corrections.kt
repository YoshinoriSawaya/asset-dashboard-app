package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.csv.MetricPoint
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 手動補正。アプリ内で直した数値を、Driveのcorrectionsに残す。
 *
 * ## なぜローカルDBを直接書き換えないか
 * 「Driveが正のデータソース」という原則が崩れる。DBを直接直すと、
 * アプリを入れ直した瞬間に補正が消える。
 *
 * corrections に**補正内容そのもの**を置いておけば、
 * キャッシュを作り直すときに「元データ + corrections」で組み立て直せる。
 *
 * ## 元データを書き換えない
 * processedのCSVもbackupのJSONも触らない。補正は**別レイヤー**として
 * 重ねる。こうしておくと、
 * - 元データは銀行が出したそのままで残る(後から検証できる)
 * - 補正を取り消したければcorrectionsから消すだけ
 * - パーサーを直して再取り込みしても、補正は生き残る
 *
 * ## 種類は2つだけ
 * | 種類 | 用途 |
 * |------|------|
 * | `OVERRIDE` | パースを間違えた値を正しい値に直す |
 * | `MANUAL` | そもそもCSVに載らない値を足す(現金、タンス預金など) |
 *
 * どちらも「ある日付の、ある項目の、正しい値はこれ」という同じ形なので、
 * 実体は同じ。区別は由来を残すためだけのもの。
 */
object Corrections {

    private const val TAG = "Corrections"
    private const val FILE_NAME = "corrections.json"

    const val FORMAT_VERSION = 1

    enum class Kind { OVERRIDE, MANUAL }

    /**
     * 1件の補正。
     *
     * キーの形は[com.yswy.assetdashboard.csv.Deduplication.keyOf]と同じ
     * 「項目 + 日付」。同じ日・同じ項目の補正は1つだけ有効。
     */
    data class Entry(
        val metricKey: String,
        val date: LocalDate,
        val valueYen: Long,
        val kind: Kind,
        val note: String? = null,
        /** 補正した時刻(epoch millis)。後勝ちの判定に使う。 */
        val correctedAt: Long,
    )

    /** Driveからcorrectionsを読む。無ければ空。 */
    suspend fun load(api: DriveApi, folders: AppFolders): List<Entry> {
        val fileId = try {
            api.findFile(FILE_NAME, folders.corrections) ?: return emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "correctionsを探せなかった", e)
            return emptyList()
        }

        return try {
            parse(String(api.download(fileId), Charsets.UTF_8))
        } catch (e: Exception) {
            // 読めなくても致命傷ではない。補正が効かないだけ。
            Log.w(TAG, "correctionsを読めなかった", e)
            emptyList()
        }
    }

    /**
     * corrections全体を書き戻す。
     *
     * 差分ではなく丸ごと置き換える。件数が多くなる類のデータではないし、
     * 追記方式だと途中で失敗したときに壊れる。
     */
    suspend fun save(api: DriveApi, folders: AppFolders, entries: List<Entry>): Boolean = try {
        api.putTextFile(
            name = FILE_NAME,
            parentId = folders.corrections,
            content = render(entries),
            mimeType = "application/json",
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "correctionsを書けなかった", e)
        false
    }

    /**
     * 元データに補正を重ねる。
     *
     * 同じ「項目 + 日付」があれば補正で置き換え、無ければ足す。
     * これが「元データ + corrections で最終結果を組み立てる」の実体。
     */
    fun apply(points: List<MetricPoint>, corrections: List<Entry>): List<MetricPoint> {
        if (corrections.isEmpty()) return points

        // 同じキーの補正が複数あれば、後に補正したほうを採る
        val byKey = corrections
            .groupBy { "${it.metricKey}|${it.date}" }
            .mapValues { (_, list) -> list.maxBy { it.correctedAt } }

        val result = LinkedHashMap<String, MetricPoint>()
        for (point in points) {
            result["${point.metricKey}|${point.date}"] = point
        }
        for ((key, correction) in byKey) {
            result[key] = MetricPoint(correction.metricKey, correction.date, correction.valueYen)
        }
        return result.values.toList()
    }

    /** JSONの組み立て。テストから直接確かめられるよう分けてある。 */
    fun render(entries: List<Entry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("metricKey", entry.metricKey)
                    .put("date", entry.date.toString())
                    .put("valueYen", entry.valueYen)
                    .put("kind", entry.kind.name)
                    .put("correctedAt", entry.correctedAt)
                    .also { json -> entry.note?.let { json.put("note", it) } },
            )
        }

        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("corrections", array)
            .toString(2)
    }

    /** 壊れた項目は落として、読める分だけ返す。 */
    fun parse(json: String): List<Entry> {
        val array = JSONObject(json).optJSONArray("corrections") ?: return emptyList()
        val entries = mutableListOf<Entry>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val metricKey = item.optString("metricKey").takeIf { it.isNotBlank() } ?: continue
            val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: continue
            if (!item.has("valueYen")) continue

            entries += Entry(
                metricKey = metricKey,
                date = date,
                valueYen = item.optLong("valueYen"),
                kind = runCatching { Kind.valueOf(item.optString("kind")) }.getOrDefault(Kind.MANUAL),
                note = item.optString("note").takeIf { it.isNotBlank() },
                correctedAt = item.optLong("correctedAt"),
            )
        }
        return entries
    }
}
