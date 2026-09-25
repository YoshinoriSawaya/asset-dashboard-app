package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import org.json.JSONObject
import java.time.LocalDate

/**
 * [BackupWriter]が書いたJSONを読み戻す。キャッシュを作り直すときの入り口。
 *
 * processedの元CSVではなくこちらから作り直す理由は[BackupWriter]に書いた
 * (パーサーを直しても過去の結果が変わらない)。
 */
object BackupReader {

    data class Backup(
        val sourceFileId: String,
        val sourceFileName: String,
        val data: ParsedData,
    )

    /**
     * 1ファイルぶんを読む。形が分からないものはnull(呼ぶ側でスキップ)。
     * 行単位で壊れているものは、その行だけ落として残りを返す。
     */
    fun parse(json: String): Backup? {
        val root = JSONObject(json)
        val version = root.optInt("formatVersion")
        if (version != BackupWriter.FORMAT_VERSION) return null

        val sourceFileId = root.optString("sourceFileId").takeIf { it.isNotBlank() } ?: return null
        val sourceFileName = root.optString("sourceFileName")

        val data = when (root.optString("kind")) {
            "transactions" -> ParsedData.Transactions(
                root.objects("transactions").mapNotNull { it.toTransaction() },
            )
            "metrics" -> ParsedData.Metrics(
                root.objects("metrics").mapNotNull { it.toMetricPoint() },
            )
            else -> return null
        }
        return Backup(sourceFileId, sourceFileName, data)
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.date(): LocalDate? =
        runCatching { LocalDate.parse(optString("date")) }.getOrNull()

    /** キーが無ければnull。0と「無い」を区別する(BackupWriterはnullをキーごと出さない)。 */
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key)) optLong(key) else null

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key)) optString(key) else null

    private fun JSONObject.toTransaction(): BankTransaction? = BankTransaction(
        date = date() ?: return null,
        description = optString("description"),
        withdrawal = longOrNull("withdrawal"),
        deposit = longOrNull("deposit"),
        balance = longOrNull("balance"),
        memo = stringOrNull("memo"),
        label = stringOrNull("label"),
    )

    private fun JSONObject.toMetricPoint(): MetricPoint? {
        val key = optString("metricKey").takeIf { it.isNotBlank() } ?: return null
        if (!has("valueYen")) return null
        return MetricPoint(key, date() ?: return null, optLong("valueYen"))
    }
}
