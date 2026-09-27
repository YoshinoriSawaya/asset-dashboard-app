package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.CardStatementAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** カテゴリ別の支出(E07-25)。値はすべて作り物。 */
class CategorySpendingTest {

    private var n = 0
    private fun row(
        date: String, desc: String, out: Long? = null, inn: Long? = null,
        category: String? = null, kind: CategoryKind? = null, card: Boolean = false, cardPayment: Boolean = false,
    ) = BankTransactionEntity(
        dedupKey = "k${n++}", date = LocalDate.parse(date), description = desc,
        withdrawal = out, deposit = inn, balance = null, memo = null,
        label = if (card) CardStatementAdapter.LABEL else null, sourceFileId = "f",
        category = category, categoryKind = kind, cardPayment = cardPayment,
    )

    private val living = CategoryKind.LIVING
    private val play = CategoryKind.DISCRETIONARY

    private val rows = listOf(
        // 6月: 電気代だけ(食費は0の月)
        row("2026-06-10", "電気代", out = 10_000),
        // 7月
        row("2026-07-05", "スーパー", out = 20_000, category = "食費", kind = living, card = true),
        row("2026-07-10", "電気代", out = 10_000),
        // 8月
        row("2026-08-05", "スーパー", out = 40_000, category = "食費", kind = living, card = true),
        row("2026-08-06", "スーパー", inn = 2_000, category = "食費", kind = living, card = true), // 返品
        row("2026-08-12", "ラーメン", out = 20_000, category = "外食", kind = play, card = true),
        row("2026-08-15", "家具店", out = 30_000, category = "家具・家電", kind = CategoryKind.PLANNED, card = true),
        row("2026-08-10", "電気代", out = 10_000),
        row("2026-08-26", "振替 口座A", out = 100_000, category = "振替", kind = CategoryKind.TRANSFER),
        row("2026-08-27", "カード引落", out = 50_000, cardPayment = true),
        row("2026-08-25", "給与", inn = 300_000),
    )

    @Test
    fun `カテゴリごとに使った額。返品は引き、振替・カードの引き落とし・入金は数えない`() {
        val aug = CategorySpending.of(rows, YearMonth.of(2026, 8))
        assertEquals(listOf("食費", "家具・家電", "外食", null), aug.rows.map { it.category })
        assertEquals(listOf(38_000L, 30_000L, 20_000L, 10_000L), aug.rows.map { it.yen })
        assertEquals(2, aug.rows.first().count)
        assertEquals(98_000L, aug.totalYen)
        // 消費は生活費・遊び代・カテゴリなし。大型出費は入れない
        assertEquals(68_000L, aug.consumptionYen)
    }

    @Test
    fun `平均は前の月の直近で、使わなかった月は0として数え、はっきり多ければ印を付ける`() {
        val aug = CategorySpending.of(rows, YearMonth.of(2026, 8)).rows.associateBy { it.category }
        // 6月・7月の2か月。食費は(0 + 20,000) / 2
        assertEquals(10_000L, aug["食費"]!!.averageYen)
        assertTrue(aug["食費"]!!.high)
        // 電気代は平均どおり
        assertEquals(10_000L, aug[null]!!.averageYen)
        assertFalse(aug[null]!!.high)
        // 前の月に無かったカテゴリは平均0
        assertEquals(0L, aug["外食"]!!.averageYen)
    }

    @Test
    fun `差が小さければ、倍率を超えても印を付けない`() {
        val small = listOf(
            row("2026-07-01", "自販機", out = 1_000, category = "飲み物", kind = play, card = true),
            row("2026-08-01", "自販機", out = 3_000, category = "飲み物", kind = play, card = true),
        )
        assertFalse(CategorySpending.of(small, YearMonth.of(2026, 8)).rows.single().high)
    }

    @Test
    fun `最初の月は平均と比べない`() {
        val jun = CategorySpending.of(rows, YearMonth.of(2026, 6))
        assertEquals(0, jun.averageMonths)
        assertNull(jun.rows.single().averageYen)
        assertFalse(jun.rows.single().high)
    }

    @Test
    fun `推移は古い月から、期間の合計の多いカテゴリに色を付け、残りはその他にまとめる`() {
        val trend = CategorySpending.trend(rows, YearMonth.of(2026, 8), top = 2)
        assertEquals(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)), trend.months)
        // 期間の合計: 食費58,000、カテゴリなし(電気代)30,000、家具・家電30,000、外食20,000。同じ額なら先に出てきたほう
        assertEquals(listOf("食費", null), trend.categories)
        assertEquals(
            listOf(
                listOf(0L, 10_000L, 0L),
                listOf(20_000L, 10_000L, 0L),
                listOf(38_000L, 10_000L, 50_000L), // その他 = 家具・家電 + 外食。振替は入らない
            ),
            trend.values,
        )
        assertTrue(trend.hasOthers)
    }

    @Test
    fun `推移の期間は指定した月まで、指定した月数だけ`() {
        val trend = CategorySpending.trend(rows, YearMonth.of(2026, 7), months = 1)
        assertEquals(listOf(YearMonth.of(2026, 7)), trend.months)
        assertEquals(listOf("食費", null), trend.categories)
        assertFalse(trend.hasOthers)
    }

    @Test
    fun `明細のある月は新しい順、カテゴリの明細はその月のそのカテゴリだけ`() {
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7), YearMonth.of(2026, 6)), CategorySpending.months(rows))
        val food = CategorySpending.transactions(rows, YearMonth.of(2026, 8), "食費")
        assertEquals(listOf(LocalDate.parse("2026-08-06"), LocalDate.parse("2026-08-05")), food.map { it.date })
        assertEquals(listOf("電気代"), CategorySpending.transactions(rows, YearMonth.of(2026, 8), null).map { it.description })
    }
}
