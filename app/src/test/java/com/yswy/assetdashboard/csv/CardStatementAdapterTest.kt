package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 形は実物のカードのCSVに合わせ、値は全部作り物。 */
class CardStatementAdapterTest {

    private fun rows(text: String) = CsvText.splitRows(text)

    private val statement = rows(
        listOf(
            "見本　花子　様,1234-****-****-****,見本カード",
            "2026/09/01,ミホンストア,1200,１,１,1200,",
            "2026/09/03,ミホン書店,800,１,１,800,",
            "2026/09/05,ミホン返品,-300,１,１,-300,返品",
            "見本　太郎　様,1234-****-****-5678,見本カード（家族）",
            "2026/09/07,ミホン*オンライン,500,１,１,500,",
            ",,,,,2200,",
        ).joinToString("\n"),
    )

    private val pending = rows(
        listOf(
            "2026/10/01,ミホンストア,ご本人,１回払い,,26年12月,1200,1200,,,,,",
            "2026/10/04,ミホン書店,ご本人,１回払い,,26年12月,800,800,,,,,",
        ).joinToString("\n"),
    )

    @Test
    fun `確定した明細は1行目の氏名とカード番号で見分ける`() {
        assertEquals(CardStatementAdapter, CsvAdapters.findFor(statement.first()))
        assertEquals(CardPendingAdapter, CsvAdapters.findFor(pending.first()))
        // 銀行の見出しは今までどおり
        assertEquals(WithdrawalDepositAdapter, CsvAdapters.findFor(listOf("年月日", "お引出し", "お預入れ", "お取り扱い内容", "残高")))
        // 伏せ方が違っても当たる
        for (masked in listOf("************5678", "****-****-****-5678", "１２３４－＊＊＊＊－＊＊＊＊－＊＊＊＊")) {
            assertEquals(masked, CardStatementAdapter, CsvAdapters.findFor(listOf("見本　花子　様", masked, "見本カード")))
        }
        // 氏名っぽくても、カード番号が無ければ当たらない
        assertNull(CsvAdapters.findFor(listOf("見本　花子　様", "見本カード")))
    }

    @Test
    fun `確定した明細から利用を1行ずつと、請求の合計を取る`() {
        val result = CardStatementAdapter.parse(statement.first(), statement.drop(1))
        val data = result.data as ParsedData.Transactions
        assertEquals(listOf("ミホンストア", "ミホン書店", "ミホン返品", "ミホン*オンライン"), data.rows.map { it.description })
        assertEquals(listOf(1200L, 800L, null, 500L), data.rows.map { it.withdrawal })
        // 返品は入金として持つ
        assertEquals(300L, data.rows[2].deposit)
        assertEquals("返品", data.rows[2].memo)
        assertTrue(data.rows.all { it.label == CardStatementAdapter.LABEL && it.balance == null })
        assertEquals(LocalDate.of(2026, 9, 7), data.rows.last().date)
        assertEquals(2200L, data.statementTotal)
        // 家族カードの氏名の行と合計の行は、読めない行に数えない
        assertEquals(emptyList<SkippedRow>(), result.skipped)
    }

    @Test
    fun `まだ確定していない明細は1行目から読み、請求の合計は無い`() {
        val result = CardPendingAdapter.parse(pending.first(), pending.drop(1))
        val data = result.data as ParsedData.Transactions
        assertEquals(listOf(1200L, 800L), data.rows.map { it.withdrawal })
        assertEquals(listOf("ミホンストア", "ミホン書店"), data.rows.map { it.description })
        assertNull(data.statementTotal)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `同じ利用は確定前と確定後で同じ重複キーになる`() {
        val confirmed = (CardStatementAdapter.parse(statement.first(), statement.drop(1)).data as ParsedData.Transactions).rows.first()
        val early = CardPendingAdapter.parse(
            listOf("2026/09/01", "ミホンストア", "ご本人", "１回払い", "", "26年10月", "1200", "1200", "", "", "", "", ""),
            emptyList(),
        ).data as ParsedData.Transactions
        assertEquals(Deduplication.keyOf(confirmed), Deduplication.keyOf(early.rows.single()))
    }

    @Test
    fun `読めない行は理由付きで数える`() {
        val broken = CardStatementAdapter.parse(statement.first(), listOf(listOf("見出し?", "x", "y")))
        assertEquals(1, broken.skipped.size)
        assertFalse((broken.data as ParsedData.Transactions).rows.any())
    }
}
