package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取り込み前の足切りと区切り文字判定。
 * 「inboxに雑に放り込む」のでCSVでないものが来る前提。
 */
class CsvValidationTest {

    @Test
    fun `空のファイルは弾く`() {
        assertEquals("中身が空", CsvValidation.rejectReason(ByteArray(0)))
    }

    @Test
    fun `xlsxは何であるかを言って弾く`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "あとは何でも".toByteArray()
        val reason = CsvValidation.rejectReason(zip)
        assertNotNull(reason)
        assertTrue(reason!!.contains("Excel"))
    }

    @Test
    fun `PDFは何であるかを言って弾く`() {
        val pdf = "%PDF-1.7\n...".toByteArray()
        val reason = CsvValidation.rejectReason(pdf)
        assertNotNull(reason)
        assertTrue(reason!!.contains("PDF"))
    }

    @Test
    fun `NULバイトを含むファイルは弾く`() {
        val binary = byteArrayOf(0x41, 0x42, 0x00, 0x43)
        assertEquals("テキストファイルではない", CsvValidation.rejectReason(binary))
    }

    @Test
    fun `大きすぎるファイルは弾く`() {
        val huge = ByteArray(CsvValidation.MAX_BYTES + 1) { 0x41 }
        val reason = CsvValidation.rejectReason(huge)
        assertNotNull(reason)
        assertTrue(reason!!.contains("大きすぎる"))
    }

    @Test
    fun `普通のCSVは通す`() {
        assertNull(CsvValidation.rejectReason("年月日,残高\n2026/9/1,100".toByteArray()))
    }

    @Test
    fun `カンマ区切りを判定する`() {
        val csv = "年月日,お引出し,残高\n2026/9/1,100,900\n2026/9/2,200,700"
        assertEquals(',', CsvValidation.detectDelimiter(csv))
    }

    @Test
    fun `タブ区切りを判定する`() {
        val tsv = "年月日\tお引出し\t残高\n2026/9/1\t100\t900\n2026/9/2\t200\t700"
        assertEquals('\t', CsvValidation.detectDelimiter(tsv))
    }

    @Test
    fun `セミコロン区切りを判定する`() {
        val csv = "年月日;お引出し;残高\n2026/9/1;100;900\n2026/9/2;200;700"
        assertEquals(';', CsvValidation.detectDelimiter(csv))
    }

    @Test
    fun `摘要にカンマが入るタブ区切りでもタブを選ぶ`() {
        // カンマで割ると列数がバラバラになるので、揃うタブが勝つ
        val tsv = "日付\t摘要\t金額\n2026/9/1\tﾀﾅｶ,ﾀﾛｳ\t100\n2026/9/2\tｽｽﾞｷ,ｲﾁﾛｳ,ﾎｹﾞ\t200"
        assertEquals('\t', CsvValidation.detectDelimiter(tsv))
    }

    @Test
    fun `区切り文字が無い1列のファイルは既定のカンマになる`() {
        assertEquals(',', CsvValidation.detectDelimiter("あいうえお\nかきくけこ"))
    }

    @Test
    fun `1列しか無い結果は理由をつけて弾く`() {
        val rows = listOf(listOf("あいうえお"), listOf("かきくけこ"))
        val reason = CsvValidation.rejectRowsReason(rows)
        assertNotNull(reason)
        assertTrue(reason!!.contains("区切り文字"))
    }

    @Test
    fun `ヘッダーだけの結果は弾く`() {
        assertEquals("ヘッダーしか無い", CsvValidation.rejectRowsReason(listOf(listOf("a", "b"))))
    }

    @Test
    fun `タブ区切りのCSVがアダプターに通る`() {
        val tsv = "年月日\tお引出し\tお預入れ\t残高\n2026/9/1\t\t300000\t500000"
        val delimiter = CsvValidation.detectDelimiter(tsv)
        val rows = CsvText.splitRows(tsv, delimiter)

        assertEquals(WithdrawalDepositAdapter, CsvAdapters.findFor(rows.first()))
        val data = WithdrawalDepositAdapter.parse(rows.first(), rows.drop(1))
            .data as ParsedData.Transactions
        assertEquals(300000L, data.rows.single().deposit)
    }

    @Test
    fun `ありえない金額は読まない`() {
        // 区切り文字を誤判定して数字が繋がった、のような値
        assertNull(FieldParsers.parseAmount("99999999999999999"))
        assertEquals(9_999_999_999_999L, FieldParsers.parseAmount("9999999999999"))
    }

    @Test
    fun `ありえない年の日付は読まない`() {
        assertNull(FieldParsers.parseDate("1234/9/1"))
        assertNull(FieldParsers.parseDate("9999/9/1"))
        assertNotNull(FieldParsers.parseDate("2026/9/1"))
    }

    @Test
    fun `取引名形式でもありえない年は弾く`() {
        val csv = """
            "レコード区分","取引名","取扱日付　年","取扱日付　月","取扱日付　日","金額","取引後残高"
            "明細","入金","9999","09","24","50000","250000"
        """.trimIndent()
        val rows = CsvText.splitRows(csv)
        val result = AnserAdapter.parse(rows.first(), rows.drop(1))

        assertTrue((result.data as ParsedData.Transactions).rows.isEmpty())
        assertEquals(1, result.skipped.size)
    }
}
