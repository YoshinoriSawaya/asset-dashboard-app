package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.ui.AiExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryPlanTest {

    @Test
    fun `不足額を月数で割り、千円単位で切り上げる`() {
        val plan = RecoveryPlan.of(targetYen = 1_800_000, currentYen = 1_000_000)!!
        assertEquals(800_000L, plan.shortfallYen)
        assertEquals(
            listOf(RecoveryPlan.Option(3, 267_000), RecoveryPlan.Option(6, 134_000), RecoveryPlan.Option(12, 67_000)),
            plan.options,
        )
        // 切り上げなので、N か月積めば必ず届く
        plan.options.forEach { assertTrue(it.monthlyYen * it.months >= plan.shortfallYen) }
    }

    @Test
    fun `割り切れるときは切り上げない`() {
        assertEquals(100_000L, RecoveryPlan.of(600_000, 0, months = listOf(6))!!.options.single().monthlyYen)
    }

    @Test
    fun `届いていれば不足0で提案なし`() {
        val plan = RecoveryPlan.of(1_000_000, 1_200_000)!!
        assertEquals(0L, plan.shortfallYen)
        assertFalse(plan.isShort)
        assertTrue(plan.options.isEmpty())
    }

    @Test
    fun `目標額か現在の値が分からなければ計算しない`() {
        assertNull(RecoveryPlan.of(null, 100))
        assertNull(RecoveryPlan.of(100, null))
    }

    @Test
    fun `詳細とAI用の書き出しに出る`() {
        val overview = ItemOverview.Goal(Item.Goal("g", "生活防衛資金", 1_800_000, "預金・現金"), 1_000_000)
        assertEquals(800_000L, ItemDetail.Goal(overview).recovery?.shortfallYen)

        val text = AiExport.build(LocalDate.of(2026, 9, 25), null, emptyList(), listOf(overview))
        assertTrue(text.contains("不足分を埋めるには 3か月なら月々267,000円、6か月なら月々134,000円、12か月なら月々67,000円"))
    }
}
