package com.yswy.assetdashboard.csv

import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/**
 * バイト列をCSVのテキストとして読むまでの処理。
 *
 * 銀行から落としたCSVはShift_JIS(CP932)のことが多く、UTF-8のものもある。
 * 本格的な判定はE01-06でやるが、パースするには最低限ここで決める必要がある。
 */
object CsvText {

    /** Windows拡張を含むShift_JIS。素のShift_JISより受けが広い。 */
    private val CP932 = charset("windows-31j")

    /**
     * バイト列を文字列にする。
     *
     * UTF-8として**厳密に**デコードしてみて、壊れたバイトが1つでもあれば
     * CP932とみなす。UTF-8のマルチバイト列はかなり厳しい規則なので、
     * Shift_JISのテキストが偶然UTF-8として通ることはほぼ無い。
     * 逆(UTF-8をCP932で読む)は必ず通ってしまうので、この順序が重要。
     */
    fun decode(bytes: ByteArray): Decoded {
        stripBom(bytes)?.let { return Decoded(String(it, Charsets.UTF_8), "UTF-8 (BOM)") }

        val utf8: CharsetDecoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            Decoded(utf8.decode(ByteBuffer.wrap(bytes)).toString(), "UTF-8")
        } catch (_: Exception) {
            Decoded(String(bytes, CP932), "Shift_JIS (CP932)")
        }
    }

    private fun stripBom(bytes: ByteArray): ByteArray? {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        if (bytes.size < 3) return null
        for (i in bom.indices) if (bytes[i] != bom[i]) return null
        return bytes.copyOfRange(3, bytes.size)
    }

    data class Decoded(val text: String, val charsetName: String)

    /**
     * CSVを行×列に分解する。
     *
     * ダブルクォート括りの中のカンマ・改行・`""`によるエスケープを扱う。
     * 摘要欄に「振込 ﾀﾅｶ,ﾀﾛｳ」のようにカンマが入ることがあるので、
     * 単純なsplit(",")では壊れる。
     *
     * 区切り文字は指定できる(タブ区切りのファイルもE01-06で扱う)。
     */
    fun splitRows(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString().trim())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            // 完全な空行は捨てる(末尾の改行で1行増えるのを防ぐ)
            if (row.any { it.isNotEmpty() }) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    // CRLFを2行と数えない
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows
    }
}
