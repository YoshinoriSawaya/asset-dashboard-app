package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.data.FundLocalStore
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.data.NavBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** ファンドごとの比べる基準(E05-14)と、決めた最高値・知らせるか(E05-15)。値はすべて作り物。 */
class FundBaseTest {

    private val a = FundSource("区分", "A", "JP90C000H1T1", "0331418A")

    @Test
    fun `比べる基準はfunds_jsonを往復する`() {
        val peak = a.copy(base = NavBase.PEAK)
        assertEquals(listOf(peak, a.copy(name = "B")), FundSourceStore.parse(FundSourceStore.render(listOf(peak, a.copy(name = "B")))))
    }

    @Test
    fun `基準の無い前の形と知らない値は平均取得単価`() {
        val old = """{"formatVersion":1,"funds":[{"section":"区分","name":"A","isin":"JP90C000H1T1","code":"0331418A"}]}"""
        assertEquals(listOf(a), FundSourceStore.parse(old))
        val unknown = """[{"section":"区分","name":"A","isin":"JP90C000H1T1","code":"0331418A","base":"何か"}]"""
        assertEquals(NavBase.COST, FundLocalStore.decodeSources(unknown).single().base)
    }

    @Test
    fun `取り先を直しても決めた基準は残る`() {
        val changed = FundSourceStore.upsert(listOf(a.copy(base = NavBase.PEAK)), a.copy(code = "0331418B"))
        assertEquals(listOf(a.copy(code = "0331418B", base = NavBase.PEAK)), changed)
    }

    @Test
    fun `基準だけを変え、取り先が無ければ変えない`() {
        val b = a.copy(name = "B")
        assertEquals(listOf(a, b.copy(base = NavBase.PEAK)), FundSourceStore.setBase(listOf(a, b), b.fundKey, NavBase.PEAK))
        assertNull(FundSourceStore.setBase(listOf(a), "区分|C", NavBase.PEAK))
    }

    private val fixed = Nav(LocalDate.of(2026, 9, 28), 12_000)

    @Test
    fun `決めた最高値と知らせるかはfunds_jsonを往復し、無い前の形は決めていない・知らせる`() {
        val s = a.copy(base = NavBase.PEAK, peakBase = fixed, notify = false)
        assertEquals(listOf(s), FundSourceStore.parse(FundSourceStore.render(listOf(s))))
        val old = """{"formatVersion":1,"funds":[{"section":"区分","name":"A","isin":"JP90C000H1T1","code":"0331418A","base":"peak"}]}"""
        val read = FundSourceStore.parse(old).single()
        assertNull(read.peakBase)
        assertTrue(read.notify)
        // 決めた最高値が壊れていても、行は捨てない(決めていないのと同じ)
        val broken = """[{"section":"区分","name":"A","isin":"JP90C000H1T1","code":"0331418A","base":"peak","peakBase":{"date":"x"}}]"""
        assertNull(FundLocalStore.decodeSources(broken).single().peakBase)
    }

    @Test
    fun `最高値を選ぶと決めた値を持ち、平均取得単価に戻すと消える`() {
        val peak = FundSourceStore.setBase(listOf(a), a.fundKey, NavBase.PEAK, fixed)!!.single()
        assertEquals(fixed, peak.peakBase)
        assertNull(FundSourceStore.setBase(listOf(peak), a.fundKey, NavBase.COST, fixed)!!.single().peakBase)
    }

    @Test
    fun `知らせるかだけを変え、取り先を直しても決めたものは残る`() {
        val off = FundSourceStore.setNotify(listOf(a), a.fundKey, false)!!.single()
        assertFalse(off.notify)
        assertNull(FundSourceStore.setNotify(listOf(a), "区分|C", false))
        val kept = FundSourceStore.upsert(listOf(off.copy(base = NavBase.PEAK, peakBase = fixed)), a.copy(code = "0331418B")).single()
        assertEquals(a.copy(code = "0331418B", base = NavBase.PEAK, peakBase = fixed, notify = false), kept)
    }
}
