package com.yswy.assetdashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatTest {

    private val real = MoneyFormat(PrivacyMode.REAL)
    private val percent = MoneyFormat(PrivacyMode.PERCENT)
    private val mask = MoneyFormat(PrivacyMode.MASK)

    @Test
    fun `実額はこれまでどおり`() {
        assertEquals("1,234,567円", real.amount(1_234_567))
        assertEquals("+12,345円", real.change(12_345, 1_000_000))
        assertEquals("42%", real.percent(0.42))
        assertEquals("552万", real.lineAxis(5_520_000, 5_000_000))
        assertEquals("50万", real.barAxis(500_000))
    }

    @Test
    fun `%は絶対額を隠し、増減は前の値に対する率にする`() {
        assertEquals("※", percent.amount(1_234_567))
        assertEquals("+1.2%", percent.change(12_345, 1_000_000))
        assertEquals("-5.0%", percent.change(-50_000, 1_000_000))
        assertEquals("±0%", percent.change(0, 1_000_000))
        // 前の値が分からない・0なら率を出せないので隠す
        assertEquals("※", percent.change(100, null))
        assertEquals("※", percent.change(100, 0))
        assertEquals("42%", percent.percent(0.42))
    }

    @Test
    fun `%の推移グラフの目盛りは最初の点に対する増減率、増減の棒は出さない`() {
        assertEquals("+10.4%", percent.lineAxis(5_520_000, 5_000_000))
        assertEquals("-20.0%", percent.lineAxis(4_000_000, 5_000_000))
        assertNull(percent.barAxis(500_000))
    }

    @Test
    fun `マスクは数字を全部隠し、グラフの目盛りも出さない`() {
        assertEquals("※※※", mask.amount(1_234_567))
        assertEquals("※※※", mask.change(12_345, 1_000_000))
        assertEquals("※※※", mask.percent(0.42))
        assertNull(mask.lineAxis(5_520_000, 5_000_000))
        assertNull(mask.barAxis(500_000))
        for (text in listOf(mask.amount(1), mask.change(1, 1), mask.percent(0.5))) {
            assertFalse(text.any { it.isDigit() })
        }
    }

    @Test
    fun `減った割合がマイナスの値を基準にしても符号が崩れない`() {
        // 基準が負(マイナス残高など)でも、増えたらプラス
        assertEquals("+10.0%", percent.change(10, -100))
    }

    @Test
    fun `積み上げの目盛りは、%のとき最新の合計に対する割合`() {
        assertEquals("50万", real.shareAxis(500_000, 1_000_000))
        assertEquals("0%", percent.shareAxis(0, 1_000_000))
        assertEquals("50%", percent.shareAxis(500_000, 1_000_000))
        assertNull(percent.shareAxis(500_000, 0))
        assertNull(mask.shareAxis(500_000, 1_000_000))
    }

    @Test
    fun `配分の割合は小数1桁に四捨五入し、マスクでは隠す`() {
        // 進捗の percent は切り捨て(99%)。配分では合計が100%に近く見えるよう四捨五入
        assertEquals("99%", real.percent(0.9996))
        assertEquals("100.0%", real.share(0.9996))
        assertEquals("33.3%", percent.share(1.0 / 3))
        assertEquals("66.7%", real.share(2.0 / 3))
        assertEquals(MoneyFormat.HIDDEN, mask.share(0.5))
    }
}
