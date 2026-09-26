package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DrawdownTest {

    private val today = LocalDate.of(2026, 9, 26)

    /** 生活費が月30万円、6か月分が目標、下限は3か月分(90万円)。 */
    private fun fund(current: Long?, floorMonths: Int? = 3, sortOrder: Int = -1, id: String = "f") = ItemOverview.Goal(
        Item.Goal(id, "生活防衛資金", null, "預金・現金", sortOrder = sortOrder, autoTarget = AutoTarget(6, 6, floorMonths)),
        current,
        AutoTargets.Result(1_800_000, 6, 300_000),
    )

    private fun car(current: Long, due: LocalDate = LocalDate.of(2027, 3, 1)) = ItemOverview.Goal(
        Item.Goal("c", "車", 1_000_000, "車の資金", dueDate = due, rampUpMonths = 12),
        current,
    )

    @Test
    fun `下限は生活費の月平均 × 何か月分`() {
        assertEquals(900_000L, fund(0).floorYen)
        assertNull(fund(0, floorMonths = null).floorYen)
    }

    @Test
    fun `目標額以上・下限まで・下限割れ・下限なしを見分ける`() {
        assertEquals(Drawdown.FundState.FULL, Drawdown.stateOf(fund(1_800_000)))
        assertEquals(Drawdown.FundState.DRAWN, Drawdown.stateOf(fund(900_000)))
        assertEquals(Drawdown.FundState.BELOW_FLOOR, Drawdown.stateOf(fund(899_999)))
        assertEquals(Drawdown.FundState.SHORT, Drawdown.stateOf(fund(1_000_000, floorMonths = null)))
        assertNull(Drawdown.stateOf(fund(null)))
        assertNull(Drawdown.stateOf(car(0)))
    }

    @Test
    fun `期日の目標の足りない分を、下限まで取り崩して補えるか`() {
        // 生活防衛資金 150万 − 下限 90万 = 60万まで取り崩せる
        val fits = Drawdown.cover(car(600_000), listOf(fund(1_500_000)), today)!!
        assertEquals(Drawdown.Cover("生活防衛資金", 400_000, 600_000), fits)
        assertEquals(true, fits.fits)

        val short = Drawdown.cover(car(300_000), listOf(fund(1_500_000)), today)!!
        assertEquals(false, short.fits)

        // 下限を割っていれば取り崩せるのは0
        assertEquals(0L, Drawdown.cover(car(300_000), listOf(fund(800_000)), today)!!.availableYen)
        // 下限を決めていなければ分からない
        assertNull(Drawdown.cover(car(300_000), listOf(fund(1_500_000, floorMonths = null)), today)!!.fits)
    }

    @Test
    fun `期日を過ぎても出し、積み増し前・届いた・生活防衛資金が無いなら出さない`() {
        val past = LocalDate.of(2026, 9, 1)
        assertEquals(400_000L, Drawdown.cover(car(600_000, due = past), listOf(fund(1_500_000)), today)!!.shortfallYen)
        assertNull(Drawdown.cover(car(0, due = LocalDate.of(2030, 1, 1)), listOf(fund(1_500_000)), today))
        assertNull(Drawdown.cover(car(1_000_000), listOf(fund(1_500_000)), today))
        assertNull(Drawdown.cover(car(600_000), emptyList(), today))
    }

    @Test
    fun `生活防衛資金が複数なら一覧で上のもの`() {
        val upper = fund(1_500_000, sortOrder = -2, id = "upper")
        assertEquals("upper", Drawdown.primaryFund(listOf(fund(1_000_000), upper))!!.item.id)
    }
}
