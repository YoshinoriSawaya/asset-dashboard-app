package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.data.FundLocalStore
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.NavBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ファンドごとの比べる基準(E05-14)。値はすべて作り物。 */
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
}
