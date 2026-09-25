package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.toEntity
import com.yswy.assetdashboard.data.toItem
import com.yswy.assetdashboard.drive.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class YearlyAllowanceTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val key = "ふるさと納税"

    private fun point(date: String, yen: Long) = MetricPointEntity(key, LocalDate.parse(date), yen, MetricOrigin.MANUAL)

    private val goal = Item.Goal("f", "ふるさと納税", 100_000, key, resetsYearly = true)

    @Test
    fun `進捗は今年の累計だけ。去年の点は数えない`() {
        val points = mapOf(key to listOf(point("2025-12-20", 90_000), point("2026-03-01", 20_000), point("2026-08-10", 45_000)))
        val row = ItemOverview.of(goal, points, today) as ItemOverview.Goal
        assertEquals(45_000L, row.currentYen)
        assertEquals(0.45, row.progress!!, 1e-9)
    }

    @Test
    fun `今年まだ使っていなければ0(不明ではない)`() {
        val row = ItemOverview.of(goal, mapOf(key to listOf(point("2025-12-20", 90_000))), today) as ItemOverview.Goal
        assertEquals(0L, row.currentYen)
        assertEquals(0.0, row.progress!!, 1e-9)
    }

    @Test
    fun `使った分を足すと、今年の累計に足した値を今日の点にする`() {
        val points = listOf(point("2025-12-20", 90_000), point("2026-08-10", 45_000))
        assertEquals(UsageForm.Result.Ok(today, 55_000), UsageForm.parse(points, "10,000", today))
        // 年が明けて最初の記録は0から
        assertEquals(UsageForm.Result.Ok(LocalDate.of(2027, 1, 5), 10_000), UsageForm.parse(points, "10000", LocalDate.of(2027, 1, 5)))
        assertTrue(UsageForm.parse(points, "0", today) is UsageForm.Result.Invalid)
        assertTrue(UsageForm.parse(points, "abc", today) is UsageForm.Result.Invalid)
    }

    @Test
    fun `毎年の枠は行とsettingsを往復しても残る`() {
        assertEquals(goal, goal.toEntity().toItem())
        assertEquals(listOf(goal.toEntity()), Settings.parse(Settings.render(listOf(goal.toEntity()))))
        assertFalse(Settings.render(listOf(goal.copy(resetsYearly = false).toEntity())).contains("resetsYearly"))
    }

    @Test
    fun `毎年の枠で系列を選ばなければ、目標の名前の系列にする`() {
        val ok = GoalForm.parse(null, "ふるさと納税", "100000", null, "", newId = { "f" }, resetsYearly = true) as GoalForm.Result.Ok
        assertEquals("ふるさと納税", ok.goal.metricKey)
        val plain = GoalForm.parse(null, "車", "100000", null, "", newId = { "c" }) as GoalForm.Result.Ok
        assertEquals(null, plain.goal.metricKey)
    }

    @Test
    fun `AI用の書き出しでは枠として出し、不足の提案は出さない`() {
        val row = ItemOverview.of(goal, mapOf(key to listOf(point("2026-08-10", 45_000))), today) as ItemOverview.Goal
        val text = AiExport.build(today, null, emptyList(), listOf(row))
        assertTrue(text.contains("- ふるさと納税: 今年の枠 100,000円 / 今年使った額 45,000円(45%) / 残り 55,000円"))
        assertFalse(text.contains("不足分を埋めるには"))
    }
}
