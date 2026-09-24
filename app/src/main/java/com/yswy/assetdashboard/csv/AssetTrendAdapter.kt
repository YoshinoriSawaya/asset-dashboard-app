package com.yswy.assetdashboard.csv

/**
 * `日付, 合計（円）, 預金・現金（円）, 投資信託（円）, 年金（円）` 形式。
 *
 * 銀行の明細と違い、**取引ではなく時点の残高**が並んでいる。
 * 1行が「この日付における各項目の金額」なので、[MetricPoint]に展開する。
 *
 * ## 列を決め打ちしない
 * `（円）`で終わる列を**全部**Metricとして拾う。資産推移のエクスポートに
 * 「暗号資産（円）」のような列が増えても、コードを変えずにMetricが
 * 1種類増える(CLAUDE.mdの「汎用スキーマで項目として追加していく」)。
 *
 * metricKeyには列名から`（円）`を落としたものをそのまま使う。
 * `投資信託`→NISAのような対応付けはここではやらない。
 * 表示名や目標との紐付けはE07(Goal管理)の仕事。
 */
object AssetTrendAdapter : CsvAdapter {

    override val id = "資産推移形式"

    private const val DATE = "日付"

    /** `合計（円）` `合計(円)` `合計 (円)` いずれも拾う。 */
    private val YEN_SUFFIX = Regex("""[（(]円[）)]$""")

    override fun matches(header: List<String>): Boolean {
        val normalized = header.map { it.normalizeHeader() }
        val hasDate = normalized.any { it == DATE }
        val yenColumns = normalized.count { YEN_SUFFIX.containsMatchIn(it) }
        return hasDate && yenColumns >= 1
    }

    override fun parse(header: List<String>, rows: List<List<String>>): ParseResult {
        val normalized = header.map { it.normalizeHeader() }
        val dateAt = normalized.indexOf(DATE)

        // 「（円）」付きの列 → metricKey
        val metricColumns = normalized.mapIndexedNotNull { i, name ->
            if (YEN_SUFFIX.containsMatchIn(name)) i to YEN_SUFFIX.replace(name, "") else null
        }

        val points = mutableListOf<MetricPoint>()
        val skipped = mutableListOf<SkippedRow>()

        rows.forEachIndexed { i, row ->
            val lineNumber = i + 2

            val date = FieldParsers.parseDate(row.getOrNull(dateAt).orEmpty())
            if (date == null) {
                skipped += SkippedRow(lineNumber, "日付を読めない", row.joinToString(","))
                return@forEachIndexed
            }

            // 1つの列が空でも行ごと捨てない。読めた項目だけ拾う。
            var readAny = false
            for ((at, key) in metricColumns) {
                val value = FieldParsers.parseAmount(row.getOrNull(at).orEmpty()) ?: continue
                points += MetricPoint(metricKey = key, date = date, valueYen = value)
                readAny = true
            }

            if (!readAny) {
                skipped += SkippedRow(lineNumber, "金額の列が全部空", row.joinToString(","))
            }
        }

        return ParseResult(id, ParsedData.Metrics(points), skipped)
    }
}
