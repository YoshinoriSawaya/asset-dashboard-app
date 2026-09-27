package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.CardStatementAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 前の月の振り返り(E03-10)。値はすべて作り物。 */
class MonthlyReviewTest {

    private val today = LocalDate.of(2026, 9, 27)
    private var n = 0
    private fun card(date: String, desc: String, yen: Long, category: String? = null, kind: CategoryKind? = null) =
        BankTransactionEntity("k${n++}", LocalDate.parse(date), desc, yen, null, null, null, CardStatementAdapter.LABEL, "f",
            category = category, categoryKind = kind)
    private fun bankIn(date: String, yen: Long) =
        BankTransactionEntity("k${n++}", LocalDate.parse(date), "給与", null, yen, null, null, null, "f")

    /** 3〜8月: 給与30万、動画サブスク990(8月に1,290)、音楽サブスク980(8月は無し)、外食1万(8月は5万)。9月に1件。 */
    private val rows = buildList {
        listOf("03", "04", "05", "06", "07", "08").forEach { m ->
            val aug = m == "08"
            add(bankIn("2026-$m-25", 300_000))
            add(card("2026-$m-05", "動画サブスク", if (aug) 1_290 else 990, "遊び代", CategoryKind.DISCRETIONARY))
            if (!aug) add(card("2026-$m-06", "音楽サブスク", 980, "遊び代", CategoryKind.DISCRETIONARY))
            // 外食は月に3回(よく行く店なので固定費には入らない)
            val each = if (aug) 50_000L / 2 else 10_000L / 2
            add(card("2026-$m-10", "ラーメン", each, "外食", CategoryKind.DISCRETIONARY))
            add(card("2026-$m-11", "ラーメン", each / 2, "外食", CategoryKind.DISCRETIONARY))
            add(card("2026-$m-12", "ラーメン", each / 2, "外食", CategoryKind.DISCRETIONARY))
        }
        add(card("2026-09-02", "コンビニ", 500, "外食", CategoryKind.DISCRETIONARY))
    }

    @Test
    fun `前の月のお金の流れ・いつもより多いカテゴリ・予算・固定費の変化をまとめる`() {
        val r = MonthlyReview.of(rows, today, budgets = mapOf("外食" to 30_000L, "遊び代" to 10_000L))!!
        assertEquals(YearMonth.of(2026, 8), r.month)
        assertEquals(300_000L, r.incomeYen)
        assertEquals(51_290L, r.consumptionYen)
        assertEquals(300_000L - 51_290L, r.surplusYen)
        assertEquals(listOf("外食"), r.high.map { it.category })
        // 予算は使った割合の多い順。外食は5万/3万で超え、遊び代は1,290/1万
        assertEquals(listOf("外食" to true, "遊び代" to false), r.budgets.map { it.category to it.over })
        assertEquals(listOf("動画サブスク"), r.increased.map { it.description })
        assertEquals(listOf("音楽サブスク"), r.stopped.map { it.description })
        assertFalse(r.maybeIncomplete)
        assertNull(r.netWorthChangeYen)
    }

    @Test
    fun `今月の明細がまだ無ければ、出そろっていないかもしれないと分かる`() {
        val r = MonthlyReview.of(rows.filter { it.date < LocalDate.of(2026, 9, 1) }, today)!!
        assertEquals(YearMonth.of(2026, 8), r.month)
        assertTrue(r.maybeIncomplete)
    }

    @Test
    fun `純資産の増減はその月の最後と前の月の最後の差`() {
        val series = listOf(
            MetricPointEntity(NetWorth.KEY, LocalDate.of(2026, 7, 31), 1_000_000, MetricOrigin.CSV),
            MetricPointEntity(NetWorth.KEY, LocalDate.of(2026, 8, 31), 1_200_000, MetricOrigin.CSV),
        )
        assertEquals(200_000L, MonthlyReview.of(rows, today, netWorthSeries = series)!!.netWorthChangeYen)
    }

    @Test
    fun `今月より前に明細が無ければ振り返らない`() {
        assertNull(MonthlyReview.of(listOf(card("2026-09-02", "コンビニ", 500)), today))
    }
}
