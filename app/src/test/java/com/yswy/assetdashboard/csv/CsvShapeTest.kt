package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CsvShapeTest {

    // 値は作り物
    private val rows = listOf(
        listOf("見本　花子　様", "1234-****-****-****", "見本カード"),
        listOf("2026/09/01", "ミホンストア", "1200", "１回払い", "", "1200", ""),
        listOf("2026/09/03", "ミホン", "800", "１回払い", "", "800", ""),
        listOf("", "", "", "", "", "2000", ""),
    )

    @Test
    fun `セルを種類に置き換え、文字は長さだけ`() {
        assertEquals("(3) T7様|M|T5", CsvShape.line(rows[0]))
        assertEquals("(7) D|T6|N|T4|-|N|-", CsvShape.line(rows[1]))
        assertEquals("(3) [利用日]|[利用店名]|[利用金額]", CsvShape.line(listOf("利用日", "利用店名", "利用金額")))
    }

    @Test
    fun `氏名・店名・カード番号は出ない`() {
        val text = CsvShape.describe(rows, head = 2, tail = 1, smallFile = 0)
        for (secret in listOf("見本", "花子", "1234", "ミホン", "1200")) assertFalse(text, text.contains(secret))
        assertEquals(
            "4行\nL1 (3) T7様|M|T5\nL2 (7) D|T6|N|T4|-|N|-\n  …×1 (7) D|T3|N|T4|-|N|-\nL4 (7) -|-|-|-|-|N|-",
            text,
        )
    }

    @Test
    fun `小さなファイルは全部の行を出し、証券の列名は言葉のまま`() {
        val text = CsvShape.describe(rows + listOf(listOf("ファンド名", "評価額", "")))
        assertEquals(
            "5行\nL1 (3) T7様|M|T5\nL2 (7) D|T6|N|T4|-|N|-\nL3 (7) D|T3|N|T4|-|N|-\nL4 (7) -|-|-|-|-|N|-\nL5 (3) [ファンド名]|[評価額]|-",
            text,
        )
    }
}
