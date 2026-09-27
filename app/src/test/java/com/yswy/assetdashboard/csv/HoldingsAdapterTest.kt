package com.yswy.assetdashboard.csv

import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.MetricPointEntity
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
        val points = (result.data as ParsedData.Metrics).points.filterNot { it.metricKey.startsWith("#") }
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
    fun `ファンドごとに評価額・取得額(評価額−損益)・取得単価・現在値を持つ`() {
        val points = (HoldingsAdapter.parseRows(portfolio, fileDate).data as ParsedData.Metrics).points
        val funds = FundHoldings.of(points.map { MetricPointEntity.from(it) })
        assertEquals(listOf("見本ファンドA", "見本ファンドB", "見本ファンドC"), funds.funds.map { it.name })
        val a = funds.funds.first()
        assertEquals("投資信託（金額/NISA預り（つみたて投資枠））", a.section)
        // 評価額1,200・損益200 → 取得額1,000。取得単価10,000・現在値12,000
        assertEquals(FundHoldings.Snapshot(LocalDate.of(2026, 9, 25), 1_200, 1_000, 10_000, 12_000), a.latest)
        assertEquals(200L, a.latest.gainYen)
        assertEquals(0.2, a.latest.gainRatio!!, 1e-9)
        // 損失のファンド: 評価額270・損益-30 → 取得額300
        assertEquals(-30L, funds.funds.last().latest.gainYen)
    }

    @Test
    fun `損益に「+」と小数が付いていても取得額が合う`() {
        val signed = rows(
            "投資信託（金額/特定預り）,",
            "ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,",
            "見本ファンドA,----/--/--,1000,10000,12000,+10,+0.08,+200.40,+20.04,1200.40,",
        )
        val fund = FundHoldings.of((HoldingsAdapter.parseRows(signed, fileDate).data as ParsedData.Metrics).points.map { MetricPointEntity.from(it) }).funds.single()
        // 評価額1,200(1200.40を丸め) − 損益200(+200.40を丸め) = 1,000
        assertEquals(1_000L, fund.latest.costYen)
    }

    @Test
    fun `損益の列が無ければ取得額は無く、評価額だけ`() {
        val noProfit = rows(
            "投資信託（金額/特定預り）,",
            "ファンド名,数量,評価額,",
            "見本ファンドA,1000,1200,",
        )
        val fund = FundHoldings.of((HoldingsAdapter.parseRows(noProfit, fileDate).data as ParsedData.Metrics).points.map { MetricPointEntity.from(it) }).funds.single()
        assertEquals(1_200L, fund.latest.valueYen)
        assertNull(fund.latest.costYen)
        assertNull(fund.latest.gainYen)
    }

    @Test
    fun `取り込むたびに推移が1点ずつ増え、新しい日が先頭`() {
        val day1 = (HoldingsAdapter.parseRows(portfolio, fileDate).data as ParsedData.Metrics).points
        val later = portfolio.map { r -> r.map { it.replace("2026/09/25", "2026/10/25").replace("1200", "1300") } }
        val day2 = (HoldingsAdapter.parseRows(later, fileDate).data as ParsedData.Metrics).points
        val a = FundHoldings.of((day1 + day2).map { MetricPointEntity.from(it) }).funds.first { it.name == "見本ファンドA" }
        assertEquals(listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 9, 25)), a.history.map { it.date })
        assertEquals(1_300L, a.latest.valueYen)
    }

    @Test
    fun `ファンド名に区切りの文字が入っていても分けられる`() {
        assertEquals(Triple("区分", "A|B", "評価額"), FundHoldings.parseKey(FundHoldings.key("区分", "A|B", "評価額")))
        assertNull(FundHoldings.parseKey("投資信託"))
    }

    @Test
    fun `日付が無ければファイルの日付、区分の見出しが無ければ既定の名前`() {
        val bare = rows(
            "ファンド名,買付日,数量,取得単価,現在値,前日比,前日比（％）,損益,損益（％）,評価額,",
            "見本ファンドA,----/--/--,1000,10000,12000,10,0.08,200,20.00,1200,",
        )
        val points = (HoldingsAdapter.parseRows(bare, fileDate).data as ParsedData.Metrics).points.filterNot { it.metricKey.startsWith("#") }
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
        assertEquals(550L, (result.data as ParsedData.Metrics).points.filterNot { it.metricKey.startsWith("#") }.single().valueYen)
    }
}
