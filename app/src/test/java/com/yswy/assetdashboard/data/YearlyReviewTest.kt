package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.CardStatementAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1年の振り返り(E03-12)。値はすべて作り物。 */
class YearlyReviewTest {

    private val today = LocalDate.of(2026, 9, 27)
    private var n = 0
    private fun card(date: LocalDate, desc: String, yen: Long, category: String, kind: CategoryKind) =
        BankTransactionEntity("k${n++}", date, desc, yen, null, null, null, CardStatementAdapter.LABEL, "f",
            category = category, categoryKind = kind)
    private fun bankIn(date: LocalDate, yen: Long) =
        BankTransactionEntity("k${n++}", date, "給与", null, yen, null, null, null, "f")

    /**
     * 2024-09〜2026-08の24か月。給与30万、積立3万。
     * 家賃8万(2026-03から9万)、古いサブスク1,000(2025-08まで)、新しいサブスク500(2026-01から)、
     * 外食は月3回で前の1年は月1万・この1年は月2万、2026-05に大型出費10万。9月にも1件。
     */
    private val rows = buildList {
        (0L until 24L).map { YearMonth.of(2024, 9).plusMonths(it) }.forEach { m ->
            val recent = m >= YearMonth.of(2025, 9)
            add(bankIn(m.atDay(25), 300_000))
            add(card(m.atDay(1), "積立", 30_000, "積立投資", CategoryKind.INVESTMENT))
            add(card(m.atDay(2), "家賃", if (m >= YearMonth.of(2026, 3)) 90_000 else 80_000, "住まい", CategoryKind.LIVING))
            if (m <= YearMonth.of(2025, 8)) add(card(m.atDay(3), "古いサブスク", 1_000, "遊び代", CategoryKind.DISCRETIONARY))
            if (m >= YearMonth.of(2026, 1)) add(card(m.atDay(4), "新しいサブスク", 500, "遊び代", CategoryKind.DISCRETIONARY))
            val eat = if (recent) 20_000L else 10_000L
            add(card(m.atDay(10), "ラーメン", eat / 2, "外食", CategoryKind.DISCRETIONARY))
            add(card(m.atDay(11), "ラーメン", eat / 4, "外食", CategoryKind.DISCRETIONARY))
            add(card(m.atDay(12), "ラーメン", eat / 4, "外食", CategoryKind.DISCRETIONARY))
        }
        add(card(LocalDate.of(2026, 5, 20), "冷蔵庫", 100_000, "家電", CategoryKind.PLANNED))
        add(card(LocalDate.of(2026, 9, 2), "ラーメン", 1_000, "外食", CategoryKind.DISCRETIONARY))
    }

    private val netWorth = listOf(
        MetricPointEntity(NetWorth.KEY, LocalDate.of(2025, 8, 31), 10_000_000, MetricOrigin.CSV),
        MetricPointEntity(NetWorth.KEY, LocalDate.of(2026, 8, 31), 12_500_000, MetricOrigin.CSV),
    )

    @Test
    fun `期間は直近12か月(先月まで)と、明細のある暦年の新しい順。今年は先月までの途中の年`() {
        val periods = YearlyReview.periods(rows, today)
        assertEquals(listOf("直近12か月", "2026年(8月まで)", "2025年", "2024年"), periods.map { it.label })
        assertEquals(YearMonth.of(2025, 9), periods[0].months.first())
        assertEquals(YearMonth.of(2026, 8), periods[0].months.last())
        assertEquals(8, periods[1].months.size)
    }

    @Test
    fun `お金の流れと月ごとの残り、純資産の増減を貯めた分と残りに分ける`() {
        val r = YearlyReview.of(rows, YearlyReview.periods(rows, today)[0], netWorth)
        assertEquals(3_600_000L, r.incomeYen)
        // 家賃 8万×6 + 9万×6、外食 2万×12、新しいサブスク 500×8
        assertEquals(1_264_000L, r.consumptionYen)
        assertEquals(100_000L, r.plannedYen)
        assertEquals(360_000L, r.investmentYen)
        assertEquals(12, r.monthsWithData)
        assertEquals(YearMonth.of(2026, 5) to 189_500L, r.monthly[8])
        assertEquals(2_500_000L, r.netWorthChangeYen)
        assertEquals(3_600_000L - 1_264_000L - 100_000L, r.savedYen)
        assertEquals(2_500_000L - 2_236_000L, r.otherChangeYen)
    }

    @Test
    fun `カテゴリ別は今の期間の多い順で、前の12か月との差を持つ`() {
        val r = YearlyReview.of(rows, YearlyReview.periods(rows, today)[0])
        assertEquals(12, r.previousMonthsWithData)
        val eat = r.categories.first { it.category == "外食" }
        assertEquals(240_000L, eat.yen)
        assertEquals(120_000L, eat.previousYen)
        assertEquals(120_000L, eat.changeYen)
        assertEquals("住まい", r.categories.first().category)
        // 遊び代は、この1年は新しいサブスクだけ(500×8)、前の1年は古いサブスクだけ(1,000×12)
        val play = r.categories.first { it.category == "遊び代" }
        assertEquals(4_000L to 12_000L, play.yen to play.previousYen)
        assertNull(r.netWorthChangeYen)
    }

    @Test
    fun `固定費は期間の前の6か月と終わりの6か月を比べ、増えた・止まった・金額の変わったものを出す`() {
        val f = YearlyReview.of(rows, YearlyReview.periods(rows, today)[0]).fixed!!
        assertEquals(listOf("新しいサブスク"), f.added.map { it.description })
        assertEquals(listOf("古いサブスク"), f.stopped.map { it.description })
        assertEquals(listOf("家賃"), f.changed.map { it.second.description })
        assertEquals(80_000L to 90_000L, f.changed.single().let { it.first.monthlyYen to it.second.monthlyYen })
        assertEquals(90_500L, f.monthlyTotalYen)
        assertEquals(81_000L, f.previousMonthlyTotalYen)
    }

    @Test
    fun `明細が途中からの年は、明細のある月だけ数え、前の期間の月数も分かる`() {
        val periods = YearlyReview.periods(rows, today)
        val y2024 = YearlyReview.of(rows, periods[3])
        assertEquals(4, y2024.monthsWithData)
        assertEquals(0, y2024.previousMonthsWithData)
        assertNull(y2024.monthly.first().second)
        val y2025 = YearlyReview.of(rows, periods[2])
        assertEquals(4, y2025.previousMonthsWithData)
    }
}
