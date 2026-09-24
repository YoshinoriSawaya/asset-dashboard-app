package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeduplicationTest {

    private fun tx(
        day: Int,
        description: String,
        withdrawal: Long? = null,
        deposit: Long? = null,
        balance: Long? = null,
    ) = BankTransaction(
        date = LocalDate.of(2026, 9, day),
        description = description,
        withdrawal = withdrawal,
        deposit = deposit,
        balance = balance,
    )

    @Test
    fun `同じCSVを2回取り込んでも二重にならない`() {
        val once = listOf(tx(1, "給与", deposit = 300000), tx(2, "家賃", withdrawal = 80000))
        val twice = once + once

        val result = Deduplication.dedupeTransactions(twice)

        assertEquals(2, result.kept.size)
        assertEquals(2, result.dropped)
        assertTrue(result.hasDuplicates)
    }

    @Test
    fun `期間が重なるCSVでは重なった分だけ落ちる`() {
        val first = listOf(tx(1, "給与", deposit = 300000), tx(2, "家賃", withdrawal = 80000))
        val second = listOf(tx(2, "家賃", withdrawal = 80000), tx(3, "電気代", withdrawal = 5000))

        val existing = first.map { Deduplication.keyOf(it) }.toSet()
        val result = Deduplication.dedupeTransactions(second, existing)

        assertEquals(1, result.kept.size)
        assertEquals("電気代", result.kept.single().description)
        assertEquals(1, result.dropped)
    }

    @Test
    fun `残高が違っても同じ取引とみなす`() {
        // 取り込むCSVによって残高の並びが違うことがある
        val a = tx(1, "給与", deposit = 300000, balance = 500000)
        val b = tx(1, "給与", deposit = 300000, balance = 999999)

        assertEquals(Deduplication.keyOf(a), Deduplication.keyOf(b))
    }

    @Test
    fun `摘要のスペースの違いは同じ扱い`() {
        val a = tx(1, "振込 ﾀﾅｶ ﾀﾛｳ", deposit = 1000)
        val b = tx(1, "振込　ﾀﾅｶ　ﾀﾛｳ", deposit = 1000)

        assertEquals(Deduplication.keyOf(a), Deduplication.keyOf(b))
    }

    @Test
    fun `金額が違えば別の取引`() {
        val a = tx(1, "電気代", withdrawal = 5000)
        val b = tx(1, "電気代", withdrawal = 6000)

        val result = Deduplication.dedupeTransactions(listOf(a, b))
        assertEquals(2, result.kept.size)
    }

    @Test
    fun `出金と入金は取り違えない`() {
        val out = tx(1, "振替", withdrawal = 1000)
        val income = tx(1, "振替", deposit = 1000)

        val result = Deduplication.dedupeTransactions(listOf(out, income))
        assertEquals(2, result.kept.size)
    }

    @Test
    fun `重複が無ければ何も落とさない`() {
        val rows = listOf(tx(1, "給与", deposit = 300000), tx(2, "家賃", withdrawal = 80000))
        val result = Deduplication.dedupeTransactions(rows)

        assertEquals(2, result.kept.size)
        assertFalse(result.hasDuplicates)
    }

    @Test
    fun `Metricは同じ日同じ項目なら後から来たほうを採る`() {
        val points = listOf(
            MetricPoint("投資信託", LocalDate.of(2026, 9, 1), 100),
            MetricPoint("投資信託", LocalDate.of(2026, 9, 1), 200),
        )

        val result = Deduplication.dedupeMetrics(points)

        assertEquals(1, result.kept.size)
        // 時点の残高なので、後から取り込んだファイルのほうが正しい
        assertEquals(200L, result.kept.single().valueYen)
        assertEquals(1, result.dropped)
    }

    @Test
    fun `Metricは日か項目が違えば別物`() {
        val points = listOf(
            MetricPoint("投資信託", LocalDate.of(2026, 9, 1), 100),
            MetricPoint("預金・現金", LocalDate.of(2026, 9, 1), 200),
            MetricPoint("投資信託", LocalDate.of(2026, 9, 2), 300),
        )

        assertEquals(3, Deduplication.dedupeMetrics(points).kept.size)
    }

    @Test
    fun `同じ日に同じ額の取引が本当に2件あると1件に潰れる`() {
        // 既知の限界。二重計上より取りこぼしのほうがマシという割り切り。
        val rows = listOf(tx(1, "交通費", withdrawal = 220), tx(1, "交通費", withdrawal = 220))

        val result = Deduplication.dedupeTransactions(rows)
        assertEquals(1, result.kept.size)
    }
}
