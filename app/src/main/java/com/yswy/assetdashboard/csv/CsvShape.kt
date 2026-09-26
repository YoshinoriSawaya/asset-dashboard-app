package com.yswy.assetdashboard.csv

/**
 * 知らない形のCSVを、**中身を出さずに**形だけ書き出す(E01-14)。
 *
 * カード会社のCSVは1行目に氏名やカード番号(伏せ字)が入ることがあり、
 * 「1行目は列名だから出してよい」が成り立たない。新しい形に対応するときの
 * 手がかりとして、各セルを種類に置き換えて出す。
 *
 * | 記号 | セル |
 * |------|------|
 * | `-` | 空 |
 * | `D` | 日付として読める |
 * | `N` | 金額として読める |
 * | `M` | 伏せ字(`*`)を含む |
 * | `T5` | 文字(5文字) |
 * | `T5様` | 「様」で終わる文字(氏名の行の目印) |
 * | `[利用日]` | 列名によくある言葉だけのとき、そのまま |
 */
object CsvShape {

    /** 列名によくある言葉。これだけのセルは個人情報ではないのでそのまま出す。 */
    private val COLUMN_WORDS = setOf(
        "日付", "利用日", "ご利用日", "利用店名", "ご利用店名", "利用店名・商品名", "ご利用先", "利用者",
        "利用金額", "ご利用金額", "支払区分", "支払方法", "今回回数", "支払回数", "支払金額", "お支払い金額",
        "今回支払金額", "支払総額", "支払手数料", "備考", "合計", "摘要", "内容", "金額", "残高",
        "現地通貨額", "通貨略称", "換算レート", "取引日", "お取引日", "お引出し", "お預入れ", "入金", "出金",
        // 証券口座(E01-15)
        "ファンド名", "銘柄", "銘柄名", "銘柄（コード）", "買付日", "数量", "保有数量", "取得単価", "現在値",
        "前日比", "前日比（％）", "損益", "損益（％）", "評価額", "評価損益", "評価損益率",
    )

    private val NUMBER = Regex("""[-+]?¥?[\d,]+(\.\d+)?円?""")

    fun cell(value: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> "-"
            v in COLUMN_WORDS -> "[$v]"
            v.contains('*') || v.contains('＊') -> "M"
            FieldParsers.parseDate(v) != null -> "D"
            // 金額のパーサーは「１回払い」も1と読むほど緩いので、形を見るときは数字だけのものに絞る
            java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFKC).matches(NUMBER) -> "N"
            v.endsWith("様") -> "T${v.length}様"
            else -> "T${v.length}"
        }
    }

    fun line(row: List<String>): String = "(${row.size}) " + row.joinToString("|") { cell(it) }

    /**
     * 先頭と末尾の数行の形。途中は同じ形の行が続くことが多いので、
     * 形ごとの行数だけ数えて出す。
     */
    fun describe(rows: List<List<String>>, head: Int = 6, tail: Int = 4, smallFile: Int = 40): String = buildString {
        appendLine("${rows.size}行")
        // 小さなファイルは全部の行を出す。区分ごとの表が並ぶ形(E01-15)は、並び順が分からないと読めない
        if (rows.size <= smallFile) {
            rows.forEachIndexed { i, row -> appendLine("L${i + 1} ${line(row)}") }
            return@buildString
        }
        rows.take(head).forEachIndexed { i, row -> appendLine("L${i + 1} ${line(row)}") }
        if (rows.size > head + tail) {
            val middle = rows.subList(head, rows.size - tail).groupingBy { line(it) }.eachCount()
            middle.entries.sortedByDescending { it.value }.take(5).forEach { (shape, count) -> appendLine("  …×$count $shape") }
        }
        rows.drop(maxOf(head, rows.size - tail)).forEachIndexed { i, row ->
            appendLine("L${maxOf(head, rows.size - tail) + i + 1} ${line(row)}")
        }
    }.trimEnd()
}
