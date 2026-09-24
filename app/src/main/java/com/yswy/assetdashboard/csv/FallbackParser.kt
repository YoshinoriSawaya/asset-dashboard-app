package com.yswy.assetdashboard.csv

import java.time.LocalDate

/**
 * どのアダプターにも当たらないCSVから、金額だけでも拾う。
 *
 * ヘッダーを見ても形式が分からないので、**列の中身を見て**
 * 日付の列と金額の列を推測する。取引の意味(入金か出金か)までは
 * 分からないので、結果は[ParsedData.Metrics]として返す。
 *
 * ## 割り切り
 * 推測なので取りこぼしも誤検出もある。ここでの方針は
 * **「余分に拾う」ほうに倒す**こと。未知の形式に対して
 * 「本命の列を落とす」より「関係ない数値列も拾う」ほうがマシで、
 * 要らない系列は後から人間が見て捨てられる(E03-04の手動補正)。
 *
 * 具体的には、連番や口座番号のような「金額ではない数値列」も
 * Metricとして出てくることがある。これは仕様。
 */
object FallbackParser {

    const val ADAPTER_ID = "汎用フォールバック"

    /** 列が「その種類」だと判断する最低割合。 */
    private const val THRESHOLD = 0.6

    /**
     * 金額らしい見た目か。
     *
     * [FieldParsers.parseAmount]は既知の金額列向けにわざと緩くしてあり、
     * `ABC123`でも123を返す。列の推測にそれを使うと何でも金額列に
     * 見えてしまうので、ここでは厳しめに判定する。
     */
    private val NUMERIC = Regex("""^[-−△▲+]?[¥￥]?[\d,]+\s*円?$""")

    private fun looksNumeric(value: String): Boolean {
        val text = value.trim()
        return text.isNotEmpty() && text.any { it.isDigit() } && NUMERIC.matches(text)
    }

    /**
     * @param fallbackDate 日付の列が見つからなかったときに使う日付。
     *   ファイルのmodifiedTimeを渡す想定(「このファイルの時点の値」とみなす)。
     */
    fun parse(
        header: List<String>,
        rows: List<List<String>>,
        fallbackDate: LocalDate,
    ): ParseResult {
        val columnCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
        if (columnCount == 0 || rows.isEmpty()) {
            return ParseResult(ADAPTER_ID, ParsedData.Metrics(emptyList()), emptyList())
        }

        fun columnValues(at: Int): List<String> =
            rows.mapNotNull { it.getOrNull(at)?.trim()?.takeIf(String::isNotEmpty) }

        // 日付の列: 日付として読める割合がいちばん高い列
        val dateAt = (0 until columnCount)
            .map { at -> at to ratio(columnValues(at)) { FieldParsers.parseDate(it) != null } }
            .filter { it.second >= THRESHOLD }
            .maxByOrNull { it.second }
            ?.first

        // 金額の列: 数値らしい値が多い列。日付の列は除く。
        val amountColumns = (0 until columnCount)
            .filter { it != dateAt }
            .filter { at -> ratio(columnValues(at)) { looksNumeric(it) } >= THRESHOLD }

        if (amountColumns.isEmpty()) {
            return ParseResult(ADAPTER_ID, ParsedData.Metrics(emptyList()), emptyList())
        }

        val points = mutableListOf<MetricPoint>()
        val skipped = mutableListOf<SkippedRow>()

        rows.forEachIndexed { i, row ->
            val lineNumber = i + 2
            val date = dateAt
                ?.let { FieldParsers.parseDate(row.getOrNull(it).orEmpty()) }
                ?: fallbackDate

            var readAny = false
            for (at in amountColumns) {
                val cell = row.getOrNull(at).orEmpty()
                if (!looksNumeric(cell)) continue
                val value = FieldParsers.parseAmount(cell) ?: continue
                points += MetricPoint(metricKey = keyFor(header, at), date = date, valueYen = value)
                readAny = true
            }

            if (!readAny) {
                skipped += SkippedRow(lineNumber, "金額として読める列が無い", row.joinToString(","))
            }
        }

        return ParseResult(ADAPTER_ID, ParsedData.Metrics(points), skipped)
    }

    /** 列名が使えるならそれを、無ければ位置で呼ぶ。 */
    private fun keyFor(header: List<String>, at: Int): String {
        val name = header.getOrNull(at)?.normalizeHeader().orEmpty()
        return if (name.isNotEmpty()) name else "列${at + 1}"
    }

    private fun ratio(values: List<String>, predicate: (String) -> Boolean): Double {
        if (values.isEmpty()) return 0.0
        return values.count(predicate).toDouble() / values.size
    }
}
