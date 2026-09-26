package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 形は実物の保有商品一覧に合わせ、値と名前は全部作り物。 */
class HoldingsAdapterTest {

    private val fileDate = LocalDate.of(2026, 9, 26)

    private fun rows(vararg lines: String) = CsvText.splitRows(lines.joinToString("\n"))

    private val portfolio = rows(
        "ポートフォリオ一覧,",
        "見本口座,",
        "2026/09/25 15:00現在,",
        "投資信託（金額/NISA預り（つみたて投資枠））,",
        "ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,",
        "見本ファンドA,----/--/--,1000,10000,12000,10,0.08,200,20.00,1200,",
        "見本ファンドB,----/--/--,500,10000,11000,-5,-0.05,50,10.00,550.40,",
        "投資信託（金額/NISA預り（つみたて投資枠））合計,",
        "評価額,損益,損益（％）",
        "1750,250,16.67",
        "投資信託（金額/特定預り）,",
        "ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,",
        "見本ファンドC,----/--/--,300,10000,9000,1,0.01,-30,-10.00,270,",
    )

    @Test
    fun `見出しが途中にあっても、ファイル全体から見分ける`() {
        assertEquals(HoldingsAdapter, CsvAdapters.findForRows(portfolio))
        // 1行目だけでは当たらない
        assertNull(CsvAdapters.findFor(portfolio.first()))
        // 他の形は今までどおり
        assertEquals(
            WithdrawalDepositAdapter,
            CsvAdapters.findForRows(rows("年月日,お引出し,お預入れ,お取り扱い内容,残高", "2026/09/01,100,,x,1000")),
        )
    }

    @Test
    fun `区分ごとに評価額を足し、系列名は区分の見出しそのまま`() {
        val result = HoldingsAdapter.parseRows(portfolio, fileDate)
        val points = (result.data as ParsedData.Metrics).points
        assertEquals(
            listOf(
                MetricPoint("投資信託（金額/NISA預り（つみたて投資枠））", LocalDate.of(2026, 9, 25), 1_750),
                MetricPoint("投資信託（金額/特定預り）", LocalDate.of(2026, 9, 25), 270),
            ),
            points,
        )
        // 区分の合計の小さな表は読まず、読めない行にも数えない
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `日付が無ければファイルの日付、区分の見出しが無ければ既定の名前`() {
        val bare = rows(
            "ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,",
            "見本ファンドA,----/--/--,1000,10000,12000,10,0.08,200,20.00,1200,",
        )
        val points = (HoldingsAdapter.parseRows(bare, fileDate).data as ParsedData.Metrics).points
        assertEquals(listOf(MetricPoint(HoldingsAdapter.DEFAULT_SECTION, fileDate, 1_200)), points)
    }

    @Test
    fun `評価額を読めない行は理由付きで数え、残りは読む`() {
        val broken = rows(
            "投資信託（金額/特定預り）,",
            "ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,",
            "見本ファンドA,----/--/--,1000,10000,12000,10,0.08,200,20.00,--,",
            "見本ファンドB,----/--/--,500,10000,11000,-5,-0.05,50,10.00,550,",
        )
        val result = HoldingsAdapter.parseRows(broken, fileDate)
        assertEquals(1, result.skipped.size)
        assertEquals(550L, (result.data as ParsedData.Metrics).points.single().valueYen)
    }
}
