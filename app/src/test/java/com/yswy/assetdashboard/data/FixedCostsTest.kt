package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.CardStatementAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 固定費・サブスクの一覧(E07-26)。値はすべて作り物。 */
class FixedCostsTest {

    private val today = LocalDate.of(2026, 9, 27)
    private var n = 0
    private fun row(
        date: String, desc: String, out: Long? = null, inn: Long? = null,
        kind: CategoryKind? = null, card: Boolean = false, cardPayment: Boolean = false,
    ) = BankTransactionEntity(
        dedupKey = "k${n++}", date = LocalDate.parse(date), description = desc,
        withdrawal = out, deposit = inn, balance = null, memo = null,
        label = if (card) CardStatementAdapter.LABEL else null, sourceFileId = "f",
        category = kind?.label, categoryKind = kind, cardPayment = cardPayment,
    )

    private val months = listOf("03", "04", "05", "06", "07", "08")
    private val rows = buildList {
        val electric = listOf(8_000L, 12_000L, 10_000L, 10_000L, 10_000L, 10_000L)
        months.forEachIndexed { i, m ->
            add(row("2026-$m-10", "電気代", out = electric[i]))
            add(row("2026-$m-15", "動画サブスク", out = 990, card = true))
            if (m != "08") add(row("2026-$m-20", "解約したサブスク", out = 500, card = true))
            repeat(3) { add(row("2026-$m-0${it + 1}", "自販機", out = 150, card = true)) } // よく買う店
            add(row("2026-$m-12", "NISA積立", out = 30_000, kind = CategoryKind.INVESTMENT, card = true))
            add(row("2026-$m-25", "振替 口座A", out = 50_000, kind = CategoryKind.TRANSFER))
            add(row("2026-$m-27", "カード引落", out = 40_000, cardPayment = true))
            add(row("2026-$m-25", "給与", inn = 300_000))
        }
        listOf("03", "04", "05").forEach { add(row("2026-$it-18", "たまの店", out = 3_000, card = true)) }
        add(row("2026-09-10", "今月だけの店", out = 5_000, card = true)) // 今月は見ない
    }

    @Test
    fun `毎月の支払いだけを拾い、よく買う店・たまの店・積立投資・振替・カードの引き落とし・入金は外す`() {
        val costs = FixedCosts.of(rows, today)!!
        assertEquals(6, costs.months.size)
        assertEquals(listOf("電気代", "動画サブスク", "解約したサブスク"), costs.items.map { it.description })
        assertEquals(listOf(10_000L, 990L, 500L), costs.items.map { it.monthlyYen })
        assertEquals(11_490L, costs.monthlyTotalYen)
        assertEquals(11_490L * 12, costs.yearlyTotalYen)
    }

    @Test
    fun `定額か変動か、先月止まったか`() {
        val items = FixedCosts.of(rows, today)!!.items.associateBy { it.description }
        assertFalse(items["電気代"]!!.fixedAmount)
        assertTrue(items["動画サブスク"]!!.fixedAmount)
        assertEquals(6, items["動画サブスク"]!!.hitMonths)
        assertEquals(5, items["解約したサブスク"]!!.hitMonths)
        assertTrue(items["解約したサブスク"]!!.missingLastMonth)
        assertFalse(items["動画サブスク"]!!.missingLastMonth)
    }

    @Test
    fun `消費に占める割合は、同じ期間の消費の月平均に対して`() {
        // 消費 = 電気代60,000 + 動画5,940 + 解約2,500 + 自販機2,700 + たまの店9,000 を6か月で割る
        val costs = FixedCosts.of(rows, today)!!
        assertEquals(80_140L / 6, costs.consumptionYen)
        assertEquals(11_490.0 / (80_140L / 6), costs.consumptionShare!!, 1e-9)
    }

    @Test
    fun `何か月出ていればよいかは見た月の3分の2で、少なくとも3か月。3か月に満たなければ出さない`() {
        assertEquals(listOf(3, 3, 4, 4), listOf(3, 4, 5, 6).map { FixedCosts.minMonths(it) })
        assertNull(FixedCosts.of(rows.filter { it.date >= LocalDate.of(2026, 7, 1) }, today))
    }
}
