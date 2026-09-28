package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.FundLocalStore
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.data.NavBase
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

    private fun source(name: String, base: NavBase = NavBase.COST, peakBase: Long? = null, notify: Boolean = true) =
        FundSource("区分", name, "JP90C000H1T1", "0331418A", base, peakBase?.let { Nav(day.minusDays(30), it) }, notify)

    @Test
    fun `最高値と比べるファンドは、決めた最高値より10%以上上がったら知らせ、平均取得単価では見ない`() {
        // 平均取得単価からは+50%だが、決めた最高値からは+5%: 最高値と比べるなら知らせない
        val h = holdings("A" to 10_000)
        val navs = mapOf("区分|A" to nav(15_000))
        assertTrue(NavAlert.evaluate(h, navs, emptyMap<String, LocalDate>(), listOf(source("A", NavBase.PEAK, 14_286))).notices.isEmpty())
        // 同じ値でも平均取得単価と比べるなら知らせる
        assertEquals(1, NavAlert.evaluate(h, navs, emptyMap<String, LocalDate>(), listOf(source("A"))).notices.size)
        // 決めた最高値から+10%
        val up = NavAlert.evaluate(h, mapOf("区分|A" to nav(15_400)), emptyMap<String, LocalDate>(), listOf(source("A", NavBase.PEAK, 14_000)))
        assertEquals(listOf("navpeak:区分|A:up"), up.notices.map { it.key })
        assertEquals("Aが決めた最高値より10%以上上がりました", up.notices.single().title)
        assertFalse((up.notices.single().title + up.notices.single().text).contains("円"))
    }

    @Test
    fun `下がったときは知らせない`() {
        val h = holdings("A" to 10_000)
        // 決めた最高値から-47.5%、平均取得単価からも-47.5%
        val navs = mapOf("区分|A" to nav(5_250))
        assertTrue(NavAlert.evaluate(h, navs, emptyMap<String, LocalDate>(), listOf(source("A", NavBase.PEAK, 10_000))).notices.isEmpty())
        assertTrue(NavAlert.evaluate(h, navs, emptyMap<String, LocalDate>(), listOf(source("A"))).notices.isEmpty())
    }

    @Test
    fun `最高値をまだ決めていないファンドは知らせない`() {
        val h = holdings("A" to 10_000)
        assertTrue(NavAlert.evaluate(h, mapOf("区分|A" to nav(20_000)), emptyMap<String, LocalDate>(), listOf(source("A", NavBase.PEAK))).notices.isEmpty())
    }

    @Test
    fun `知らせないにしたファンドは出さず、記録も消す`() {
        val h = holdings("A" to 10_000)
        val r = NavAlert.evaluate(h, mapOf("区分|A" to nav(12_000)), mapOf("navup:区分|A:up" to day), listOf(source("A", notify = false)))
        assertTrue(r.notices.isEmpty())
        assertEquals(listOf("navup:区分|A:up"), r.forget)
    }

    @Test
    fun `基準を変えたら、使っていないほうの記録を消す`() {
        val h = holdings("A" to 10_000)
        val r = NavAlert.evaluate(h, mapOf("区分|A" to nav(12_000)), mapOf("navup:区分|A:up" to day), listOf(source("A", NavBase.PEAK, 11_500)))
        assertEquals(listOf("navup:区分|A:up"), r.forget)
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
