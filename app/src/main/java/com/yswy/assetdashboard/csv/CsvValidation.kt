package com.yswy.assetdashboard.csv

/**
 * 取り込む前の足切り。
 *
 * 「inboxに雑に放り込む」運用なので、CSVでないものが入ってくる前提で考える。
 * Excelのまま置いた、PDFの明細を置いた、というのは普通に起きる。
 * ここで**理由が分かる形で**弾いておくと、E01-08のログが役に立つものになる。
 */
object CsvValidation {

    /** これを超えるファイルはCSVとして扱わない。個人の明細で10MBは異常。 */
    const val MAX_BYTES = 10 * 1024 * 1024

    /** 試す区切り文字。上から順に優先(同点ならカンマ)。 */
    private val DELIMITERS = listOf(',', '\t', ';')

    /**
     * 取り込みを諦める理由。問題なければnull。
     *
     * 中身のバイト列だけで判断する。拡張子やファイル名は見ない
     * (「ファイル名も気にせず放り込む」のが前提なので当てにならない)。
     */
    fun rejectReason(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return "中身が空"
        if (bytes.size > MAX_BYTES) return "ファイルが大きすぎる (${bytes.size / 1024 / 1024}MB)"

        signatureOf(bytes)?.let { return it }

        // NULバイトが入っていればテキストではない。
        // 先頭8KBだけ見れば十分(バイナリなら大抵すぐ出てくる)。
        val head = bytes.take(8 * 1024)
        if (head.any { it == 0.toByte() }) return "テキストファイルではない"

        return null
    }

    /**
     * 見覚えのある形式のファイルなら、何であるかを言って弾く。
     * 「読めなかった」より「xlsxだからCSVで出し直して」のほうが親切。
     */
    private fun signatureOf(bytes: ByteArray): String? {
        fun startsWith(vararg prefix: Int): Boolean =
            bytes.size >= prefix.size && prefix.withIndex().all { (i, b) ->
                bytes[i] == b.toByte()
            }

        return when {
            // PK.. はzip。xlsxもzipなのでここに入る。
            startsWith(0x50, 0x4B, 0x03, 0x04) ->
                "Excel(xlsx)かzipのように見える。CSVで書き出して置き直す"
            startsWith(0x25, 0x50, 0x44, 0x46) ->
                "PDFのように見える。CSVで書き出して置き直す"
            // 古いExcel(xls)の複合ドキュメント形式
            startsWith(0xD0, 0xCF, 0x11, 0xE0) ->
                "古い形式のExcel(xls)のように見える。CSVで書き出して置き直す"
            else -> null
        }
    }

    /**
     * 区切り文字を推測する。
     *
     * 銀行によってはタブ区切りやセミコロン区切りで出てくる。
     * 各候補で実際に分解してみて、**列数が揃っている**ものを選ぶ。
     * 区切り文字が違えば列は1つにしかならないので、そこで差がつく。
     */
    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().take(30).joinToString("\n")

        return DELIMITERS
            .map { it to scoreOf(sample, it) }
            .filter { it.second.columns >= 2 }
            .maxWithOrNull(
                compareBy({ it.second.consistency }, { it.second.columns }),
            )
            ?.first
            ?: ','
    }

    private fun scoreOf(sample: String, delimiter: Char): Score {
        val rows = CsvText.splitRows(sample, delimiter)
        if (rows.isEmpty()) return Score(0.0, 0)

        val columns = rows.first().size
        if (columns < 2) return Score(0.0, columns)

        val consistent = rows.count { it.size == columns }
        return Score(consistent.toDouble() / rows.size, columns)
    }

    private data class Score(val consistency: Double, val columns: Int)

    /** 分解した結果を見て、CSVとして成立していない理由を返す。問題なければnull。 */
    fun rejectRowsReason(rows: List<List<String>>): String? = when {
        rows.isEmpty() -> "行が1つも無い"
        rows.first().size < 2 -> "列が1つしか無い(区切り文字を判定できなかった)"
        rows.size < 2 -> "ヘッダーしか無い"
        else -> null
    }
}
