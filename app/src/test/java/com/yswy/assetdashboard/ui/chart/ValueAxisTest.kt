package com.yswy.assetdashboard.ui.chart

import com.yswy.assetdashboard.ui.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueAxisTest {

    @Test
    fun `目盛りは切りのいい間隔で、データを包む`() {
        val axis = ValueAxis.of(listOf(2_130_000, 3_870_000))
        assertEquals(listOf(2_000_000L, 2_500_000L, 3_000_000L, 3_500_000L, 4_000_000L), axis.ticks)
        assertEquals(2_000_000L, axis.min)
        assertEquals(4_000_000L, axis.max)
    }

    @Test
    fun `増減の棒は0を必ず含む`() {
        val axis = ValueAxis.of(listOf(40_000, 520_000), includeZero = true)
        assertEquals(0L, axis.min)
        assertTrue(axis.max >= 520_000)

        val mixed = ValueAxis.of(listOf(-70_000, 260_000), includeZero = true)
        assertTrue(mixed.min <= -70_000 && 0L in mixed.ticks)
    }

    @Test
    fun `全部同じ値でも範囲がつぶれない`() {
        val axis = ValueAxis.of(listOf(500_000, 500_000))
        assertTrue(axis.min < 500_000 && axis.max > 500_000)
        assertEquals(0.5f, ValueAxis(10, 10, listOf(10)).fraction(10))
    }

    @Test
    fun `値を0から1に写す`() {
        val axis = ValueAxis(0, 200, listOf(0, 100, 200))
        assertEquals(0f, axis.fraction(0))
        assertEquals(0.5f, axis.fraction(100))
        assertEquals(1f, axis.fraction(200))
    }

    @Test
    fun `切りのいい間隔`() {
        assertEquals(1.0, ValueAxis.niceStep(0.3), 0.0)
        assertEquals(500_000.0, ValueAxis.niceStep(456_000.0), 0.0)
        assertEquals(200_000.0, ValueAxis.niceStep(150_000.0), 0.0)
        assertEquals(1_000_000.0, ValueAxis.niceStep(600_000.0), 0.0)
    }

    @Test
    fun `データが無くても落ちない`() {
        assertEquals(listOf(0L, 1L), ValueAxis.of(emptyList()).ticks)
    }

    @Test
    fun `目盛りの短い金額表記`() {
        assertEquals("552万", Formatters.yenCompact(5_520_000))
        assertEquals("250万", Formatters.yenCompact(2_500_000))
        assertEquals("1.5万", Formatters.yenCompact(15_000))
        assertEquals("-30万", Formatters.yenCompact(-300_000))
        assertEquals("1.2億", Formatters.yenCompact(120_000_000))
        assertEquals("800円", Formatters.yenCompact(800))
        assertEquals("0円", Formatters.yenCompact(0))
    }
}
