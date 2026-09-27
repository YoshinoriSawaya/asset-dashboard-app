package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.notify.NavFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 基準価額の推移(E05-11)。値はすべて作り物。 */
class NavHistoryTest {

    private fun nav(date: String, yen: Long) = Nav(LocalDate.parse(date), yen)

    @Test
    fun `CSVの読める行を日付順に読み、読めない行は飛ばす`() {
        // 形は投信総合検索ライブラリーのCSVに合わせた(新しい順に並んでいても日付順にする)
        val csv = """
            年月日,基準価額(円),純資産総額（百万円）,分配金,決算期
            2026年09月25日,12100,1001,,
            2026年09月24日,11900,1000,,
            2026年09月23日,-,1000,,
            2025年12月01日,10000,900,,
        """.trimIndent()
        val all = NavFetcher.parseAll(csv)
        assertEquals(listOf(nav("2025-12-01", 10_000), nav("2026-09-24", 11_900), nav("2026-09-25", 12_100)), all)
        assertEquals(nav("2026-09-25", 12_100), NavFetcher.parseLatest(csv))
    }

    @Test
    fun `期間は最新の点の日付から数える`() {
        val all = listOf(
            nav("2024-01-10", 9_000),
            nav("2025-09-24", 10_000),
            nav("2025-09-25", 10_100),
            nav("2026-06-24", 11_000),
            nav("2026-06-25", 11_100),
            nav("2026-09-25", 12_000),
        )
        assertEquals(listOf("2026-06-25", "2026-09-25"), NavHistory.window(all, NavPeriod.THREE_MONTHS).map { it.date.toString() })
        assertEquals(4, NavHistory.window(all, NavPeriod.ONE_YEAR).size)
        assertEquals(all, NavHistory.window(all.reversed(), NavPeriod.ALL))
        assertEquals(emptyList<Nav>(), NavHistory.window(emptyList(), NavPeriod.ONE_YEAR))
    }

    @Test
    fun `期間の増減は最初の点から最新の点まで。点が1つならnull`() {
        assertEquals(0.2, NavHistory.change(listOf(nav("2026-01-01", 10_000), nav("2026-09-25", 12_000)))!!, 1e-9)
        assertNull(NavHistory.change(listOf(nav("2026-09-25", 12_000))))
    }
}
