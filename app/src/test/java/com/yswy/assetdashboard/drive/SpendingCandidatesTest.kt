package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.data.BankTransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 生活費から除く出金の手がかり(E07-20)。値はすべて作り物。 */
class SpendingCandidatesTest {

    private var n = 0
    private fun row(desc: String, date: String, out: Long?, card: Boolean = false, excluded: Boolean = false) =
        BankTransactionEntity(
            dedupKey = "k${n++}", date = LocalDate.parse(date), description = desc,
            withdrawal = out, deposit = if (out == null) 100 else null, balance = null, memo = null,
            label = if (card) CardStatementAdapter.LABEL else null, sourceFileId = "f",
            excludedFromSpending = excluded,
        )

    private val rows = listOf(
        row("スーパーA", "2026-09-01", 3_000, card = true),
        row("スーパーA", "2026-09-20", 5_000, card = true),
        row("家具店B", "2026-05-10", 80_000, card = true),
        row("カード引落", "2026-09-26", 50_000, excluded = true),
        row("給与", "2026-09-25", null), // 入金だけ
    )

    @Test
    fun `摘要ごとに件数・合計・最後の日・カードか・外れているかをまとめ、入金の摘要も出す`() {
        val c = SpendingRules.candidates(rows).associateBy { it.description }
        // 入金の摘要もカテゴリを付けるので出す(E07-21。振替・給与)
        assertEquals(setOf("スーパーA", "家具店B", "カード引落", "給与"), c.keys)
        assertEquals(100L, c["給与"]!!.depositYen)
        assertEquals(SpendingRules.Candidate("スーパーA", 2, 8_000, LocalDate.parse("2026-09-20"), fromCard = true, excludedNow = false), c["スーパーA"])
        assertEquals(true, c["カード引落"]!!.excludedNow)
        assertEquals(false, c["カード引落"]!!.fromCard)
    }

    @Test
    fun `件数・金額・最近の順に並べる`() {
        val c = SpendingRules.candidates(rows)
        assertEquals(listOf("スーパーA", "家具店B", "カード引落", "給与"), SpendingRules.sorted(c, SpendingRules.Order.COUNT).map { it.description })
        assertEquals(listOf("家具店B", "カード引落", "スーパーA", "給与"), SpendingRules.sorted(c, SpendingRules.Order.AMOUNT).map { it.description })
        assertEquals(listOf("カード引落", "給与", "スーパーA", "家具店B"), SpendingRules.sorted(c, SpendingRules.Order.RECENT).map { it.description })
    }

    @Test
    fun `カテゴリなしの出金だけに絞る。入金だけの摘要とカードの引き落としは出さない`() {
        val c = SpendingRules.candidates(rows) +
            SpendingRules.Candidate("カード会社", 1, 9_000, LocalDate.parse("2026-09-27"), fromCard = false, excludedNow = true, cardPaymentOnly = true)
        val shown = SpendingRules.uncategorizedSpending(c) { it == "スーパーA" }
        assertEquals(setOf("家具店B", "カード引落"), shown.map { it.description }.toSet())
    }

    @Test
    fun `タップで切り替え、当たっていなければ摘要を足し、当たっていれば当たる言葉を外す`() {
        assertEquals(listOf("振替", "家具店B"), SpendingRules.toggle(listOf("振替"), "家具店B"))
        assertEquals(listOf("振替"), SpendingRules.toggle(listOf("振替", "家具店B"), "家具店B"))
        // 部分の言葉で当たっていれば、その言葉を外す(全角半角は区別しない)
        assertEquals(listOf("振替"), SpendingRules.toggle(listOf("振替", "投信積立"), "ＳＢＩ証券投信積立サ－ビス"))
    }
}
