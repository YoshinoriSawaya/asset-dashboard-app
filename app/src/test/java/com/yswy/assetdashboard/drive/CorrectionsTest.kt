package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.MetricPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CorrectionsTest {

    private fun entry(
        key: String,
        day: Int,
        value: Long,
        kind: Corrections.Kind = Corrections.Kind.OVERRIDE,
        at: Long = 1000,
        note: String? = null,
    ) = Corrections.Entry(key, LocalDate.of(2026, 9, day), value, kind, note, at)

    private fun point(key: String, day: Int, value: Long) =
        MetricPoint(key, LocalDate.of(2026, 9, day), value)

    @Test
    fun `書いて読み直すと同じになる`() {
        val entries = listOf(
            entry("投資信託", 1, 1500000, note = "パースが間違っていた"),
            entry("現金", 1, 50000, Corrections.Kind.MANUAL),
        )

        val restored = Corrections.parse(Corrections.render(entries))

        assertEquals(entries.size, restored.size)
        assertEquals("投資信託", restored[0].metricKey)
        assertEquals(1500000L, restored[0].valueYen)
        assertEquals("パースが間違っていた", restored[0].note)
        assertEquals(Corrections.Kind.MANUAL, restored[1].kind)
    }

    @Test
    fun `補正は同じ日同じ項目の値を置き換える`() {
        val points = listOf(point("投資信託", 1, 100), point("預金・現金", 1, 200))
        val corrections = listOf(entry("投資信託", 1, 999))

        val applied = Corrections.apply(points, corrections)

        assertEquals(2, applied.size)
        assertEquals(999L, applied.first { it.metricKey == "投資信託" }.valueYen)
        // 補正していない項目はそのまま
        assertEquals(200L, applied.first { it.metricKey == "預金・現金" }.valueYen)
    }

    @Test
    fun `元データに無い項目は足される`() {
        // 現金のようにCSVに載らない値
        val points = listOf(point("投資信託", 1, 100))
        val corrections = listOf(entry("現金", 1, 50000, Corrections.Kind.MANUAL))

        val applied = Corrections.apply(points, corrections)

        assertEquals(2, applied.size)
        assertEquals(50000L, applied.first { it.metricKey == "現金" }.valueYen)
    }

    @Test
    fun `同じキーの補正が複数あれば後に補正したほうを採る`() {
        val points = listOf(point("投資信託", 1, 100))
        val corrections = listOf(
            entry("投資信託", 1, 111, at = 1000),
            entry("投資信託", 1, 222, at = 2000),
        )

        val applied = Corrections.apply(points, corrections)

        assertEquals(1, applied.size)
        assertEquals(222L, applied.single().valueYen)
    }

    @Test
    fun `補正が無ければ元データがそのまま出る`() {
        val points = listOf(point("投資信託", 1, 100))
        assertEquals(points, Corrections.apply(points, emptyList()))
    }

    @Test
    fun `日付が違えば別の補正`() {
        val points = listOf(point("投資信託", 1, 100), point("投資信託", 2, 200))
        val applied = Corrections.apply(points, listOf(entry("投資信託", 1, 999)))

        assertEquals(999L, applied.first { it.date.dayOfMonth == 1 }.valueYen)
        assertEquals(200L, applied.first { it.date.dayOfMonth == 2 }.valueYen)
    }

    @Test
    fun `壊れた項目は落として読める分だけ返す`() {
        val json = """
            {
              "formatVersion": 1,
              "corrections": [
                {"metricKey": "投資信託", "date": "2026-09-01", "valueYen": 100, "kind": "OVERRIDE", "correctedAt": 1},
                {"metricKey": "", "date": "2026-09-01", "valueYen": 200},
                {"metricKey": "現金", "date": "こわれてる", "valueYen": 300},
                {"metricKey": "年金", "date": "2026-09-01"}
              ]
            }
        """.trimIndent()

        val entries = Corrections.parse(json)

        assertEquals(1, entries.size)
        assertEquals("投資信託", entries.single().metricKey)
    }

    @Test
    fun `空のJSONでも落ちない`() {
        assertTrue(Corrections.parse("""{"formatVersion":1}""").isEmpty())
        assertTrue(Corrections.parse(Corrections.render(emptyList())).isEmpty())
    }

    @Test
    fun `noteが無ければキーごと出さない`() {
        val json = org.json.JSONObject(Corrections.render(listOf(entry("投資信託", 1, 100))))
        val first = json.getJSONArray("corrections").getJSONObject(0)
        assertTrue(!first.has("note"))
        assertNull(Corrections.parse(Corrections.render(listOf(entry("投資信託", 1, 100))))[0].note)
    }
}
