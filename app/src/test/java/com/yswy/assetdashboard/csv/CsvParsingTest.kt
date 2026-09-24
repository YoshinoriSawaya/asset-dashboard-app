package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 実データを置かなくても回せるように、実ファイルで起きた事象を
 * 最小の形に写したテストを置いておく。
 * 個人情報が入るので実物のCSVはリポジトリに入れない。
 */
class CsvParsingTest {

    @Test
    fun `摘要のカンマでは列がずれない`() {
        val rows = CsvText.splitRows("a,b\n\"振込 ﾀﾅｶ,ﾀﾛｳ\",100")
        assertEquals(listOf("振込 ﾀﾅｶ,ﾀﾛｳ", "100"), rows[1])
    }

    @Test
    fun `CRLFを2行と数えない`() {
        val rows = CsvText.splitRows("a,b\r\n1,2\r\n")
        assertEquals(2, rows.size)
    }

    @Test
    fun `金額は空とゼロを区別する`() {
        assertNull(FieldParsers.parseAmount(""))
        assertNull(FieldParsers.parseAmount("-"))
        assertEquals(0L, FieldParsers.parseAmount("0"))
        assertEquals(1234L, FieldParsers.parseAmount("1,234"))
        assertEquals(-500L, FieldParsers.parseAmount("-500"))
        assertEquals(-500L, FieldParsers.parseAmount("△500"))
    }

    @Test
    fun `日付は複数の書き方を受ける`() {
        val expected = java.time.LocalDate.of(2026, 9, 24)
        assertEquals(expected, FieldParsers.parseDate("2026/9/24"))
        assertEquals(expected, FieldParsers.parseDate("2026-09-24"))
        assertEquals(expected, FieldParsers.parseDate("2026年9月24日"))
        assertEquals(expected, FieldParsers.parseDate("20260924"))
        assertNull(FieldParsers.parseDate("合計"))
    }

    @Test
    fun `引出預入形式は列の順番が変わっても読める`() {
        val csv = """
            年月日,お取り扱い内容,お引出し,お預入れ,残高,メモ,ラベル
            2026/9/1,給与,,300000,500000,,
            2026/9/2,家賃,80000,,420000,,
        """.trimIndent()
        val rows = CsvText.splitRows(csv)
        val adapter = CsvAdapters.findFor(rows.first())
        assertEquals(WithdrawalDepositAdapter, adapter)

        val result = adapter!!.parse(rows.first(), rows.drop(1))
        assertEquals(2, result.transactions.size)
        assertEquals(300000L, result.transactions[0].deposit)
        assertNull(result.transactions[0].withdrawal)
        assertEquals(80000L, result.transactions[1].withdrawal)
        assertEquals(420000L, result.latestBalance)
    }

    @Test
    fun `引出預入形式は合計行を読み飛ばす`() {
        val csv = """
            年月日,お引出し,お預入れ,お取り扱い内容,残高,メモ,ラベル
            2026/9/1,,300000,給与,500000,,
            合計,80000,300000,,,,
        """.trimIndent()
        val rows = CsvText.splitRows(csv)
        val result = WithdrawalDepositAdapter.parse(rows.first(), rows.drop(1))

        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped.first().reason.contains("日付"))
    }

    @Test
    fun `取引名形式は日付3列と全角スペースを扱える`() {
        val csv = """
            "レコード区分","年","月","日","取引名","取扱日付　年","取扱日付　月","取扱日付　日","金額","取引後残高","摘要"
            "明細","2026","09","25","入金","2026","09","24","50000","250000","給与"
            "明細","2026","09","25","支払","2026","09","20","1200","200000","電気代"
        """.trimIndent()
        val rows = CsvText.splitRows(csv)
        val adapter = CsvAdapters.findFor(rows.first())
        assertEquals(AnserAdapter, adapter)

        val result = adapter!!.parse(rows.first(), rows.drop(1))
        assertEquals(2, result.transactions.size)

        val deposit = result.transactions[0]
        assertEquals(java.time.LocalDate.of(2026, 9, 24), deposit.date)
        assertEquals(50000L, deposit.deposit)
        assertNull(deposit.withdrawal)

        val withdrawal = result.transactions[1]
        assertEquals(1200L, withdrawal.withdrawal)
        assertNull(withdrawal.deposit)
    }

    @Test
    fun `取引名形式は金額が空の合計行を読み飛ばす`() {
        val csv = """
            "レコード区分","年","月","日","取引名","取扱日付　年","取扱日付　月","取扱日付　日","金額","取引後残高","摘要"
            "明細","2026","09","25","入金","2026","09","24","50000","250000","給与"
            "合計","2026","09","25","残高","","","","","250000",""
        """.trimIndent()
        val rows = CsvText.splitRows(csv)
        val result = AnserAdapter.parse(rows.first(), rows.drop(1))

        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun `Shift_JISとUTF-8を取り違えない`() {
        val text = "年月日,お引出し\n2026/9/1,1000"

        val sjis = CsvText.decode(text.toByteArray(charset("windows-31j")))
        assertEquals(text, sjis.text)
        assertTrue(sjis.charsetName.contains("Shift_JIS"))

        val utf8 = CsvText.decode(text.toByteArray(Charsets.UTF_8))
        assertEquals(text, utf8.text)
        assertTrue(utf8.charsetName.startsWith("UTF-8"))
    }

    @Test
    fun `未知のヘッダーはアダプターに当たらない`() {
        val header = listOf("日付", "合計（円）", "預金・現金（円）", "投資信託（円）", "年金（円）")
        assertNull(CsvAdapters.findFor(header))
    }

    @Test
    fun `列が後ろに増えても壊れない`() {
        val header = listOf("年月日", "お引出し", "お預入れ", "お取り扱い内容", "残高", "メモ", "ラベル", "新列")
        assertNotNull(CsvAdapters.findFor(header))
    }
}
