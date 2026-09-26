package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.math.pow

class FutureValueTest {

    @Test
    fun `利回り0なら今の額に積立額を足すだけ`() {
        val fv = FutureValue.of(1_000_000, 0, 30_000)!!
        assertEquals(listOf(10, 20, 30), fv.rows.map { it.years })
        assertEquals(listOf(4_600_000L, 8_200_000L, 11_800_000L), fv.rows.map { it.valueYen })
        assertEquals(fv.rows.map { it.valueYen }, fv.rows.map { it.principalYen })
    }

    @Test
    fun `積立が無ければ年利回りで毎年増える(月の複利でも1年の増え方は年利回りと同じ)`() {
        val fv = FutureValue.of(1_000_000, 300, null)!!
        // 1.03^10 = 1.3439, ^20 = 1.8061, ^30 = 2.4273 → 1万円単位で四捨五入
        assertEquals(listOf(1_340_000L, 1_810_000L, 2_430_000L), fv.rows.map { it.valueYen })
        assertEquals(listOf(1_000_000L, 1_000_000L, 1_000_000L), fv.rows.map { it.principalYen })
        assertNull(fv.monthlyYen)
    }

    @Test
    fun `積立は月末に入れ、月ごとに複利で増える`() {
        val fv = FutureValue.of(500_000, 500, 50_000, years = listOf(10))!!
        // 1か月ずつ別に積み上げた値と比べる
        val m = 1.05.pow(1.0 / 12) - 1
        var v = 500_000.0
        repeat(120) { v = v * (1 + m) + 50_000 }
        assertEquals(Math.round(v / 10_000) * 10_000, fv.rows.single().valueYen)
        assertEquals(500_000L + 50_000L * 120, fv.rows.single().principalYen)
    }

    @Test
    fun `利回りを決めていない・今の額が分からないなら出さない`() {
        assertNull(FutureValue.of(1_000_000, null, 30_000))
        assertNull(FutureValue.of(null, 300, 30_000))
    }

    @Test
    fun `詳細には想定利回りを決めた系列だけ出す`() {
        val today = LocalDate.of(2026, 9, 25)
        val series = listOf(MetricPointEntity("投資信託", today, 1_000_000, MetricOrigin.CSV))
        fun detail(bp: Int?) = ItemDetail.of(
            ItemOverview.of(Item.Metric("m", "NISA", "投資信託", expectedReturnBp = bp), mapOf("投資信託" to series), today),
            series, today = today, investMonthlyYen = 30_000,
        ) as ItemDetail.Metric

        assertNull(detail(null).future)
        assertEquals(FutureValue.of(1_000_000, 0, 30_000), detail(0).future)
    }
}
