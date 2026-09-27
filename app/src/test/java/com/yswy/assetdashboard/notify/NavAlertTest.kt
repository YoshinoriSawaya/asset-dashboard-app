package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.FundLocalStore
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.drive.FundSourceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 基準価額の取り込みと、平均取得単価より10%以上上がったときの通知(E05-09)。値はすべて作り物。 */
class NavAlertTest {

    private val day = LocalDate.of(2026, 9, 25)

    /** 形は投信総合検索ライブラリーのCSVに合わせた(値は作り物)。 */
    private val csv = """
        年月日,基準価額(円),純資産総額（百万円）,分配金,決算期
        2026年09月24日,11900,1000,,
        2026年09月25日,12100,1001,,
    """.trimIndent()

    @Test
    fun `基準価額のCSVからいちばん新しい日の値を読む`() {
        assertEquals(Nav(day, 12_100), NavFetcher.parseLatest(csv))
        assertNull(NavFetcher.parseLatest("見出しだけ"))
        assertNull(NavFetcher.parseLatest(""))
    }

    private fun holdings(vararg funds: Pair<String, Long>): FundHoldings = FundHoldings.of(
        funds.flatMap { (name, unitCost) ->
            listOf(
                MetricPointEntity(FundHoldings.key("区分", name, FundHoldings.VALUE), day, 100_000, MetricOrigin.CSV),
                MetricPointEntity(FundHoldings.key("区分", name, FundHoldings.UNIT_COST), day, unitCost, MetricOrigin.CSV),
            )
        },
    )

    private fun nav(yen: Long) = Nav(day, yen)

    @Test
    fun `平均取得単価より10%以上上がった朝に1回、下回ったら記録を消してまた見る`() {
        val h = holdings("A" to 10_000, "B" to 10_000)
        val navs = mapOf("区分|A" to nav(11_000), "区分|B" to nav(10_999))
        val first = NavAlert.evaluate(h, navs, emptyMap<String, LocalDate>())
        assertEquals(listOf("navup:区分|A:up"), first.notices.map { it.key })
        assertEquals("Aが平均取得単価より10%以上上がりました", first.notices.single().title)
        assertEquals(NotificationRules.SCREEN_FUNDS, first.notices.single().screen)
        assertFalse((first.notices.single().title + first.notices.single().text).contains("円"))
        // 次の朝も上なら出さない
        val again = NavAlert.evaluate(h, navs, mapOf("navup:区分|A:up" to day))
        assertTrue(again.notices.isEmpty())
        assertTrue(again.forget.isEmpty())
        // 下回ったら記録を消す
        val below = NavAlert.evaluate(h, mapOf("区分|A" to nav(10_500)), mapOf("navup:区分|A:up" to day))
        assertEquals(listOf("navup:区分|A:up"), below.forget)
    }

    @Test
    fun `ファンドごとに通知の番号が分かれる(あとの通知が前の通知を上書きしない)`() {
        val h = holdings("A" to 10_000, "B" to 10_000)
        val notices = NavAlert.evaluate(h, mapOf("区分|A" to nav(12_000), "区分|B" to nav(12_000)), emptyMap<String, LocalDate>()).notices
        // DailyCheck.post は鍵の最後の「:」より前から通知の番号を作る
        assertEquals(2, notices.map { it.key.substringBeforeLast(':') }.toSet().size)
    }

    @Test
    fun `基準価額が無い・単価が無い・桁が合わないファンドは知らせない`() {
        val h = holdings("A" to 10_000)
        assertTrue(NavAlert.evaluate(h, emptyMap(), emptyMap<String, LocalDate>()).notices.isEmpty())
        // 1口あたりの単価と取り違えたような桁(100倍)
        assertNull(NavAlert.gain(10_000, nav(1_200_000)))
        assertNull(NavAlert.gain(null, nav(12_000)))
        assertEquals(0.2, NavAlert.gain(10_000, nav(12_000))!!, 1e-9)
    }

    @Test
    fun `取り先はfundsjsonと端末の控えに書いて読み戻せ、形の合わないコードは落とす`() {
        val s = FundSource("区分", "A", "JP90C000H1T1", "0331418A")
        assertEquals(listOf(s), FundSourceStore.parse(FundSourceStore.render(listOf(s))))
        assertEquals(listOf(s), FundLocalStore.decodeSources(FundLocalStore.encodeSources(listOf(s, s.copy(name = "B", isin = "abc")))))
        // 同じファンドは置き換え、両方空ならやめる
        val changed = s.copy(code = "0331418B")
        assertEquals(listOf(changed), FundSourceStore.upsert(listOf(s), changed))
        assertEquals(emptyList<FundSource>(), FundSourceStore.upsert(listOf(s), s, remove = true))
        val navs = mapOf("区分|A" to nav(12_100))
        assertEquals(navs, FundLocalStore.decodeNavs(FundLocalStore.encodeNavs(navs)))
        assertEquals(emptyMap<String, Nav>(), FundLocalStore.decodeNavs("壊れた"))
    }
}
