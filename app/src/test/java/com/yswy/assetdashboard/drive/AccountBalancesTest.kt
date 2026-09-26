package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 銀行明細の残高から口座ごとの系列(E02-07)。値はすべて作り物。 */
class AccountBalancesTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun row(date: String, balance: Long?) =
        BankTransaction(date = d(date), description = "x", withdrawal = 1, deposit = null, balance = balance)
    private fun backup(id: String, adapter: String, vararg rows: BankTransaction) =
        BackupReader.Backup(id, "$id.csv", ParsedData.Transactions(rows.toList()), adapter)

    @Test
    fun `形式ごとに日ごとの最後の残高を系列にする`() {
        val a = backup("f1", "形式A", row("2026-09-01", 1000), row("2026-09-01", 900), row("2026-09-02", 800))
        val b = backup("f2", "形式B", row("2026-09-01", 5000))
        assertEquals(
            listOf(
                MetricPoint("残高(形式A)", d("2026-09-01"), 900),
                MetricPoint("残高(形式A)", d("2026-09-02"), 800),
                MetricPoint("残高(形式B)", d("2026-09-01"), 5000),
            ),
            AccountBalances.points(listOf(a, b)),
        )
    }

    @Test
    fun `新しい順のファイルは古い順にそろえてから読む`() {
        // 同じ日の2件は、ファイルの中で後ろ(=古い順にすると先)が先の取引
        val newestFirst = backup("f1", "形式A", row("2026-09-02", 800), row("2026-09-01", 900), row("2026-09-01", 1000))
        assertEquals(
            listOf(MetricPoint("残高(形式A)", d("2026-09-01"), 900), MetricPoint("残高(形式A)", d("2026-09-02"), 800)),
            AccountBalances.points(listOf(newestFirst)),
        )
    }

    @Test
    fun `同じ日が2つのファイルにあれば後のファイル。残高の無い明細と形式の分からないbackupは使わない`() {
        val old = backup("f1", "形式A", row("2026-09-01", 900))
        val new = backup("f2", "形式A", row("2026-09-01", 950))
        val card = backup("f3", "カード", row("2026-09-01", null))
        val unknown = backup("f4", "", row("2026-09-01", 1))
        assertEquals(
            listOf(MetricPoint("残高(形式A)", d("2026-09-01"), 950)),
            AccountBalances.points(listOf(old, new, card, unknown)),
        )
    }
}
