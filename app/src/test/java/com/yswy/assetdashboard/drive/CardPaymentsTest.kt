package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.csv.ParsedData
import com.yswy.assetdashboard.data.Summary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CardPaymentsTest {

    private fun day(d: String) = LocalDate.parse(d)
    private fun bank(date: String, withdrawal: Long, description: String = "カード引落") =
        BankTransaction(day(date), description, withdrawal, null, 100_000)
    private fun card(date: String, amount: Long, store: String) =
        CardStatementAdapter.cardTransaction(day(date), store, amount)

    private val bankFile = BackupReader.Backup(
        "b", "meisai.csv",
        ParsedData.Transactions(
            listOf(
                bank("2026-08-26", 1_500), // 前の月の引き落とし(カードの明細は無い)
                bank("2026-09-10", 2_000, "家賃"),
                bank("2026-10-26", 2_000), // 今回の請求の引き落とし
            ),
        ),
    )
    private val cardFile = BackupReader.Backup(
        "c", "202610.csv",
        ParsedData.Transactions(listOf(card("2026-09-01", 1_200, "店A"), card("2026-09-15", 800, "店B")), statementTotal = 2_000),
    )

    @Test
    fun `請求の合計と同じ額の、最後の利用より後の出金だけを引き落としと見なす`() {
        val snapshot = CacheSync.build(listOf(bankFile, cardFile), emptyList(), emptyList())
        val excluded = snapshot.transactions.filter { it.excludedFromSpending }
        // 同じ額でも、最後の利用より前の家賃は違う
        assertEquals(listOf(day("2026-10-26")), excluded.map { it.date })
    }

    @Test
    fun `生活費はカードの明細を使った月に数え、引き落としは数えない。入出金は銀行だけ`() {
        val snapshot = CacheSync.build(listOf(bankFile, cardFile), emptyList(), emptyList())
        val months = Summary.monthlyCashflow(snapshot.transactions).associateBy { it.period.start.monthValue }
        // 明細の無い月の引き落としはそのまま数える
        assertEquals(1_500L, months.getValue(8).livingSpendingYen)
        assertEquals(2_000L + 1_200 + 800, months.getValue(9).livingSpendingYen)
        assertEquals(0L, months.getValue(10).livingSpendingYen)
        // 出金は口座から出た額。カードの明細は入らず、引き落としが入る
        assertEquals(2_000L, months.getValue(9).spendingYen)
        assertEquals(2_000L, months.getValue(10).spendingYen)
    }

    @Test
    fun `合計が無い(確定前の)明細や、窓の外の出金は突き合わせない`() {
        val pending = cardFile.copy(data = ParsedData.Transactions((cardFile.data as ParsedData.Transactions).rows))
        assertEquals(0, CacheSync.build(listOf(bankFile, pending), emptyList(), emptyList()).transactions.count { it.excludedFromSpending })

        val late = bankFile.copy(data = ParsedData.Transactions(listOf(bank("2027-01-10", 2_000))))
        assertEquals(0, CacheSync.build(listOf(late, cardFile), emptyList(), emptyList()).transactions.count { it.excludedFromSpending })
    }

    @Test
    fun `返品は生活費から引く`() {
        val refund = cardFile.copy(data = ParsedData.Transactions(listOf(card("2026-09-01", 1_200, "店A"), card("2026-09-02", -200, "店A"))))
        val snapshot = CacheSync.build(listOf(refund), emptyList(), emptyList())
        assertEquals(1_000L, Summary.monthlyCashflow(snapshot.transactions).single().livingSpendingYen)
    }
}
