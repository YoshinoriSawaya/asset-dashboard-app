package com.yswy.assetdashboard.csv

import android.util.Log
import com.yswy.assetdashboard.BuildConfig
import com.yswy.assetdashboard.drive.DriveApi

/**
 * inboxのファイル1つを読んでパースするところまで。
 *
 * まだDBには書かない(保存先のスキーマはE02で決める)。
 * processedへの移動はE01-07、失敗ログの書き出しはE01-08。
 */
object CsvIngest {

    private const val TAG = "CsvIngest"

    suspend fun read(api: DriveApi, file: DriveApi.DriveFile): Outcome {
        val bytes = try {
            api.download(file.id)
        } catch (e: Exception) {
            return Outcome.Failed(file, "ダウンロードできない: ${e.message}")
        }

        if (bytes.isEmpty()) return Outcome.Failed(file, "中身が空")

        val decoded = CsvText.decode(bytes)
        val rows = CsvText.splitRows(decoded.text)
        if (rows.isEmpty()) return Outcome.Failed(file, "行が1つも無い")

        val header = rows.first()
        val dataRows = rows.drop(1)
        if (dataRows.isEmpty()) return Outcome.Failed(file, "ヘッダーしか無い")

        val adapter = CsvAdapters.findFor(header)

        if (adapter == null) {
            // ヘッダーは列名だけなので個人情報は入らない。判定の手がかりとして出す。
            Log.i(TAG, "未知のヘッダー: ${file.name} header=${header.joinToString(",")}")

            // 日付が読めない場合に備えて、ファイルの更新時刻を日付の代わりに渡す。
            val fallbackDate = parseModifiedDate(file.modifiedTime)
            val fallback = FallbackParser.parse(header, dataRows, fallbackDate)
            val points = (fallback.data as ParsedData.Metrics).points

            if (points.isEmpty()) {
                Log.i(TAG, "  金額らしい列が見つからず、拾えるものが無かった")
                return Outcome.UnknownFormat(file, decoded.charsetName, header)
            }

            Log.i(
                TAG,
                "  フォールバックで ${points.size}点 " +
                    "(${(fallback.data as ParsedData.Metrics).keys.joinToString("/")}) を抽出",
            )
            return Outcome.Parsed(file, decoded.charsetName, fallback, viaFallback = true)
        }

        val result = adapter.parse(header, dataRows)

        val summary = when (val data = result.data) {
            is ParsedData.Transactions -> "取引${data.rows.size}行"
            is ParsedData.Metrics -> "Metric${data.points.size}点 (${data.keys.joinToString("/")})"
        }
        Log.i(
            TAG,
            "${file.name}: ${adapter.id} charset=${decoded.charsetName} " +
                "$summary 読めず=${result.skipped.size}",
        )

        // 入出金の判定に使っている取引名が想定どおりかを確かめる手がかり。
        // 列の区分値なので個人情報は入らない。
        (result.data as? ParsedData.Transactions)?.let { data ->
            val labels = data.rows.mapNotNull { it.label }.distinct()
            if (labels.isNotEmpty()) Log.i(TAG, "  取引名の種類: ${labels.joinToString(" / ")}")
        }

        result.skipped.take(5).forEach {
            // 行の中身には口座番号や氏名が入る。デバッグビルドでだけ出す。
            val detail = if (BuildConfig.DEBUG) " / ${it.raw.take(120)}" else ""
            Log.i(TAG, "  読めない行 ${it.lineNumber}: ${it.reason}$detail")
        }

        return Outcome.Parsed(file, decoded.charsetName, result, viaFallback = false)
    }

    /**
     * DriveのmodifiedTime(RFC3339)から日付だけ取る。
     * 読めなければ今日にしておく——ここで落ちる意味は無い。
     */
    private fun parseModifiedDate(modifiedTime: String): java.time.LocalDate =
        runCatching { java.time.OffsetDateTime.parse(modifiedTime).toLocalDate() }
            .getOrElse { java.time.LocalDate.now() }

    sealed interface Outcome {
        val file: DriveApi.DriveFile

        data class Parsed(
            override val file: DriveApi.DriveFile,
            val charsetName: String,
            val result: ParseResult,
            /** アダプターではなく推測で読んだ。画面でもそう分かるようにする。 */
            val viaFallback: Boolean,
        ) : Outcome

        /** アダプターにも当たらず、フォールバックでも何も拾えなかった。 */
        data class UnknownFormat(
            override val file: DriveApi.DriveFile,
            val charsetName: String,
            val header: List<String>,
        ) : Outcome

        data class Failed(
            override val file: DriveApi.DriveFile,
            val reason: String,
        ) : Outcome
    }
}
