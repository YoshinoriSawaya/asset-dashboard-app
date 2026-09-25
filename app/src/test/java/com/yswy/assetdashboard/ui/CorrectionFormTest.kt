package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.drive.Corrections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CorrectionFormTest {

    private fun ok(key: String, date: String, value: String, note: String = "") =
        CorrectionForm.parse(key, date, value, note) as CorrectionForm.Result.Ok

    @Test
    fun `金額はカンマ・円・円記号・マイナスを受ける`() {
        assertEquals(1_234_567L, ok("現金", "2026-09-25", "1,234,567").valueYen)
        assertEquals(1_234_567L, ok("現金", "2026-09-25", "¥1,234,567").valueYen)
        assertEquals(50_000L, ok("現金", "2026-09-25", "50000円").valueYen)
        assertEquals(-500L, ok("現金", "2026-09-25", "-500").valueYen)
    }

    @Test
    fun `前後の空白を落とし、空のメモはnull`() {
        val input = ok(" 現金 ", " 2026-09-25 ", " 1000 ", "  ")
        assertEquals("現金", input.metricKey)
        assertEquals(LocalDate.of(2026, 9, 25), input.date)
        assertEquals(null, input.note)
    }

    @Test
    fun `打ち間違いは通さない`() {
        for ((key, date, value) in listOf(
            Triple("", "2026-09-25", "1000"),
            Triple("現金", "2026/09/25", "1000"),
            Triple("現金", "2026-02-30", "1000"),
            Triple("現金", "2026-09-25", ""),
            Triple("現金", "2026-09-25", "1O00"),
            Triple("現金", "2026-09-25", "12.5"),
            Triple("現金", "2026-09-25", "123456789012345"),
        )) {
            assertTrue("$key $date $value", CorrectionForm.parse(key, date, value, "") is CorrectionForm.Result.Invalid)
        }
    }

    @Test
    fun `CSVの点を直すならOVERRIDE、無い点を足すならMANUAL`() {
        assertEquals(Corrections.Kind.OVERRIDE, CorrectionForm.kindFor(MetricOrigin.CSV))
        assertEquals(Corrections.Kind.OVERRIDE, CorrectionForm.kindFor(MetricOrigin.OVERRIDE))
        assertEquals(Corrections.Kind.MANUAL, CorrectionForm.kindFor(MetricOrigin.MANUAL))
        assertEquals(Corrections.Kind.MANUAL, CorrectionForm.kindFor(null))
    }

    @Test
    fun `同じ項目と日付の補正は置き換え、取り消しはその日だけ消す`() {
        val day = LocalDate.of(2026, 9, 1)
        fun entry(key: String, date: LocalDate, value: Long) =
            Corrections.Entry(key, date, value, Corrections.Kind.MANUAL, correctedAt = value)

        val start = listOf(entry("現金", day, 1), entry("現金", day.plusDays(1), 2), entry("合計", day, 3))
        val replaced = Corrections.upsert(start, entry("現金", day, 9))
        assertEquals(3, replaced.size)
        assertEquals(9L, replaced.single { it.metricKey == "現金" && it.date == day }.valueYen)

        val removed = Corrections.remove(replaced, "現金", day)
        assertEquals(listOf(2L, 3L), removed.map { it.valueYen }.sorted())
    }
}
