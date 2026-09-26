package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.drive.Settings
import com.yswy.assetdashboard.ui.GoalForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 満たしているかの判定と、足りないときの月々の額(E07-19)。値はすべて作り物。 */
class RefillTest {

    /** 生活費月20万 × 6か月 = 120万、下限3か月 = 60万、足りないときは6か月で埋める。 */
    private fun fund(current: Long, refill: Int? = 6) = ItemOverview.Goal(
        Item.Goal("f", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6, 3), refillMonths = refill),
        current,
        auto = AutoTargets.Result(targetYen = 1_200_000, monthsUsed = 6, monthlyAverageYen = 200_000),
    )

    @Test
    fun `満たしていれば、生活費の何か月分かを添えて積み立て不要`() {
        assertEquals(Refill.Full(6.5), Refill.of(fund(1_300_000)))
        assertEquals(Refill.Full(6.0), Refill.of(fund(1_200_000)))
    }

    @Test
    fun `足りなければ、決めた月数で埋める月々の額を1つ出す`() {
        // あと30万を6か月で → 5万(千円単位で切り上げ)
        assertEquals(Refill.Short(300_000, 6, 50_000, belowFloor = false), Refill.of(fund(900_000)))
        // あと70万を6か月で → 116,667 → 117,000。下限(60万)も割っている
        assertEquals(Refill.Short(700_000, 6, 117_000, belowFloor = true), Refill.of(fund(500_000)))
    }

    @Test
    fun `埋める期間を決めていない、値が分からなければ出さない`() {
        assertNull(Refill.of(fund(900_000, refill = null)))
        assertNull(Refill.of(ItemOverview.Goal(Item.Goal("g", "車", 1_000_000, refillMonths = 6), null)))
    }

    @Test
    fun `金額の目標でも使え、生活費の何か月分かは出さない`() {
        val goal = ItemOverview.Goal(Item.Goal("g", "予備費", 300_000, "預金", refillMonths = 3), 300_000)
        assertEquals(Refill.Full(null), Refill.of(goal))
    }

    @Test
    fun `入力、1〜120か月で、毎年の枠・大型出費の積立・積み増しとは一緒にできない`() {
        val ok = GoalForm.parse(null, "生活防衛資金", "", "預金・現金", "", auto = "6" to "6", refill = "6")
        assertEquals(6, (ok as GoalForm.Result.Ok).goal.refillMonths)
        assertNull((GoalForm.parse(null, "x", "1000", null, "", refill = "") as GoalForm.Result.Ok).goal.refillMonths)
        assertTrue(GoalForm.parse(null, "x", "1000", null, "", refill = "0") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "1000", null, "", resetsYearly = true, refill = "6") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "", null, "", sinking = "5" to "", refill = "6") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "1000", null, "20300101", rampUp = "12", refill = "6") is GoalForm.Result.Invalid)
    }

    @Test
    fun `settingsに書いて読み戻せ、古い設定では決めていない`() {
        val goal = Item.Goal("f", "生活防衛資金", null, autoTarget = AutoTarget(6, 6), refillMonths = 6).toEntity()
        assertEquals(listOf(goal), Settings.parse(Settings.render(listOf(goal))))
        assertNull(Settings.parse(Settings.render(listOf(goal.copy(refillMonths = null)))).single().refillMonths)
    }
}
