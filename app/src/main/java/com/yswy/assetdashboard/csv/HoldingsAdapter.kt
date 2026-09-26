package com.yswy.assetdashboard.csv

import java.time.LocalDate

/**
 * 証券口座の保有商品一覧(ポートフォリオ)(E01-15)。
 *
 * 1枚のCSVに区分(NISAつみたて投資枠・特定預りなど)ごとの表が並ぶ。値は作り物。
 * ```
 * ポートフォリオ一覧
 * ...
 * 投資信託（金額/NISA預り（つみたて投資枠））      ← 区分の見出し(文字だけの行)
 * ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,
 * 見本ファンドA,----/--/--,1000,10000,12000,10,0.08,200,20.00,1200,
 * ...
 * 投資信託（金額/NISA預り（つみたて投資枠））合計  ← 区分の合計(読まない)
 * 評価額,損益,損益（％）
 * 1200,200,20.00
 * 投資信託（金額/特定預り）
 * ファンド名,買付日,...
 * ```
 *
 * ## 区分ごとの評価額の合計を1つのMetricにする(本人が選んだ)
 * 系列名は区分の見出しそのまま(`投資信託→NISA` のような対応付けはしない。CLAUDE.md)。
 * 区分の合計の行があっても使わず、明細の評価額を足す(合計の行の形が区分ごとに揃っているとは限らない)。
 *
 * ## 見分け方
 * 見出しが1行目に無いので、ファイル全体から「名前の列と評価額の列がある行」を探す。
 *
 * ## 日付
 * 表に日付の列が無い(値は開いた時点のもの)。文字の行に日付があればそれを、無ければ
 * ファイルの日付(Driveの更新日)を使う。
 */
object HoldingsAdapter : CsvAdapter {

    override val id = "保有商品一覧(区分ごとの表)"

    /** 名前の列の見出し。 */
    private val NAME_COLUMNS = listOf("ファンド名", "銘柄", "銘柄名", "銘柄(コード)")
    private const val VALUE_COLUMN = "評価額"

    /** 文字の行から日付を探す。`2026/09/25 15:00現在` `2026年9月25日` など。 */
    private val DATE_IN_TEXT = Regex("""(\d{4})[/\-年](\d{1,2})[/\-月](\d{1,2})""")

    /** 見出しの列の並び: 名前の列と評価額の列の位置。 */
    private data class Columns(val name: Int, val value: Int, val size: Int)

    private fun columnsOf(row: List<String>): Columns? {
        val normalized = row.map { it.normalizeHeader() }
        val name = normalized.indexOfFirst { it in NAME_COLUMNS || it.startsWith("銘柄") }
        val value = normalized.indexOf(VALUE_COLUMN)
        // 区分の合計の「評価額,損益,損益（％）」は名前の列が無いので当たらない
        return if (name >= 0 && value >= 0) Columns(name, value, row.size) else null
    }

    /** 見出しは1行目ではないので、1行目だけでは判定しない。 */
    override fun matches(header: List<String>): Boolean = false

    override fun matchesRows(rows: List<List<String>>): Boolean = rows.any { columnsOf(it) != null }

    override fun parse(header: List<String>, rows: List<List<String>>): ParseResult =
        parseRows(listOf(header) + rows, LocalDate.now())

    override fun parseRows(rows: List<List<String>>, fileDate: LocalDate): ParseResult {
        val date = dateInText(rows) ?: fileDate
        val totals = LinkedHashMap<String, Long>()
        val skipped = mutableListOf<SkippedRow>()

        var section: String? = null
        var columns: Columns? = null

        rows.forEachIndexed { i, row ->
            val lineNumber = i + 1
            val cells = row.map { it.trim() }
            val filled = cells.filter { it.isNotEmpty() }

            columnsOf(row)?.let {
                columns = it
                return@forEachIndexed
            }

            // 文字だけの1セルの行は区分の見出し。「…合計」は区分の終わりなので、表を閉じる
            if (filled.size == 1 && FieldParsers.parseAmount(filled.single()) == null) {
                columns = null
                if (!filled.single().endsWith("合計")) section = filled.single()
                return@forEachIndexed
            }

            val current = columns ?: return@forEachIndexed
            // 表の途中で列の数が変わったら、表は終わっている(合計の小さな表など)
            if (row.size != current.size) {
                columns = null
                return@forEachIndexed
            }
            if (filled.isEmpty()) return@forEachIndexed

            val value = FieldParsers.parseAmount(cells.getOrNull(current.value).orEmpty())
            if (value == null || cells.getOrNull(current.name).isNullOrEmpty()) {
                skipped += SkippedRow(lineNumber, "評価額を読めない", row.joinToString(","))
                return@forEachIndexed
            }
            val key = section ?: DEFAULT_SECTION
            totals[key] = (totals[key] ?: 0L) + value
        }

        val points = totals.map { (key, yen) -> MetricPoint(key, date, yen) }
        return ParseResult(id, ParsedData.Metrics(points), skipped)
    }

    /** 区分の見出しが見つからなかったときの系列名。 */
    const val DEFAULT_SECTION = "保有商品"

    /** 表より前の文字の行にある日付。 */
    private fun dateInText(rows: List<List<String>>): LocalDate? {
        val firstTable = rows.indexOfFirst { columnsOf(it) != null }.takeIf { it >= 0 } ?: rows.size
        for (row in rows.take(firstTable)) {
            for (cell in row) {
                val m = DATE_IN_TEXT.find(cell) ?: continue
                FieldParsers.parseDate("${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}")?.let { return it }
            }
        }
        return null
    }
}
