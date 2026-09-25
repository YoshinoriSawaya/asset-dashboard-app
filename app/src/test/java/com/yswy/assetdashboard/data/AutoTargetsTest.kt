package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.ParsedData
import com.yswy.assetdashboard.drive.BackupReader
import com.yswy.assetdashboard.drive.CacheSync
import com.yswy.assetdashboard.drive.Settings
import com.yswy.assetdashboard.drive.SpendingRules
import com.yswy.assetdashboard.ui.GoalForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AutoTargetsTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun month(ym: String, living: Long, count: Int = 3): Cashflow {
        val m = YearMonth.parse(ym)
        return Cashflow(m.atDay(1)..m.atEndOfMonth(), 0, living + 1000, count, livingSpendingYen = living)
    }

    @Test
    fun `今月と明細の無い月を除き、新しいほうからN か月の生活費の平均 × M か月`() {
        val monthly = listOf(
            month("2026-05", 100_000),
            month("2026-06", 0, count = 0), // 明細なし
            month("2026-07", 200_000),
            month("2026-08", 300_000),
            month("2026-09", 10_000), // 今月(途中)
        )
        val result = AutoTargets.compute(AutoTarget(averageMonths = 2, coverMonths = 6), monthly, today)
        assertEquals(2, result.monthsUsed)
        assertEquals(250_000L, result.monthlyAverageYen)
        assertEquals(1_500_000L, result.targetYen)
    }

    @Test
    fun `月が足りなければ、ある月だけで平均する`() {
        val result = AutoTargets.compute(AutoTarget(6, 6), listOf(month("2026-08", 300_000)), today)
        assertEquals(1, result.monthsUsed)
        assertEquals(1_800_000L, result.targetYen)
    }

    @Test
    fun `使える月が無ければ目標額は不明`() {
        val result = AutoTargets.compute(AutoTarget(6, 6), listOf(month("2026-09", 10_000)), today)
        assertNull(result.targetYen)
        assertEquals(0, result.monthsUsed)
    }

    @Test
    fun `自動のGoalは計算した額で進捗を出す`() {
        val goal = Item.Goal("g", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(1, 6))
        val points = mapOf("預金・現金" to listOf(MetricPointEntity("預金・現金", today, 900_000, MetricOrigin.CSV)))
        val row = ItemOverview.of(goal, points, today, listOf(month("2026-08", 300_000))) as ItemOverview.Goal
        assertEquals(1_800_000L, row.targetYen)
        assertEquals(0.5, row.progress!!, 1e-9)
    }

    @Test
    fun `自動のGoalは行とsettingsを往復しても同じ`() {
        val goal = Item.Goal("g", "生活防衛資金", null, "預金・現金", autoTarget = AutoTarget(6, 6))
        assertEquals(goal, goal.toEntity().toItem())
        assertEquals(listOf(goal.toEntity()), Settings.parse(Settings.render(listOf(goal.toEntity()))))
        // 目標額の決め方がどちらも無いGoalは読めない
        assertNull(ItemEntity("x", ItemType.GOAL, "何も無い").toItem())
    }

    @Test
    fun `目標の入力で生活費から出すを選ぶと、目標額は見ない`() {
        val ok = GoalForm.parse(null, "生活防衛資金", "", "預金・現金", "", newId = { "id" }, auto = "6" to "6")
            as GoalForm.Result.Ok
        assertEquals(AutoTarget(6, 6), ok.goal.autoTarget)
        assertNull(ok.goal.targetYen)
        assertTrue(GoalForm.parse(null, "x", "", null, "", auto = "0" to "6") is GoalForm.Result.Invalid)
        assertTrue(GoalForm.parse(null, "x", "", null, "", auto = "6" to "abc") is GoalForm.Result.Invalid)
    }

    @Test
    fun `除く言葉は全角半角・大文字小文字・空白を気にしない`() {
        assertTrue(SpendingRules.matches("ﾌﾘｶｴ ﾃｲｷ", listOf("フリカエ")))
        assertTrue(SpendingRules.matches("ＶＩＳＡ　ｶｰﾄﾞ", listOf("visaカード")))
        assertFalse(SpendingRules.matches("電気代", listOf("フリカエ")))
        assertFalse(SpendingRules.matches("電気代", listOf("  ")))
    }

    @Test
    fun `除く言葉の保存形式、空と重複は落とす`() {
        assertEquals(listOf("振替", "カード"), SpendingRules.parse(SpendingRules.render(listOf("振替", "カード"))))
        assertEquals(listOf("振替"), SpendingRules.parse("""{"formatVersion":1,"excludeKeywords":["振替","  ","振替"]}"""))
    }

    @Test
    fun `キャッシュを作るときに除く印を付け、月の生活費から外れる`() {
        val rows = listOf(
            BankTransaction(LocalDate.of(2026, 8, 5), "ｶｰﾄﾞ ﾋｷｵﾄｼ", 50_000, null, null),
            BankTransaction(LocalDate.of(2026, 8, 10), "電気代", 8_000, null, null),
        )
        val snapshot = CacheSync.build(
            listOf(BackupReader.Backup("f", "f.csv", ParsedData.Transactions(rows))),
            corrections = emptyList(), settings = emptyList(), exclusions = listOf("カード"),
        )
        assertEquals(listOf(true, false), snapshot.transactions.map { it.excludedFromSpending })

        val aug = Summary.cashflow(snapshot.transactions, Summary.month(YearMonth.of(2026, 8)))
        assertEquals(58_000L, aug.spendingYen)
        assertEquals(8_000L, aug.livingSpendingYen)
    }
}
