package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.SkippedRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LastSyncTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val at = Instant.parse("2026-09-25T03:15:00Z") // 日本時間 12:15

    private fun result(inbox: InboxSync.Report, cache: CacheSync.Outcome = CacheSync.Outcome.Kept("x")) =
        FullSync.Result(emptyList(), inbox, cache)

    private val rebuilt = CacheSync.Outcome.Rebuilt(CacheSync.Snapshot(emptyList(), emptyList(), emptyList(), 0), 3, emptyList())

    @Test
    fun `問題なければ件数だけの1行`() {
        val inbox = InboxSync.Report(
            listOf(InboxSync.Entry("a.csv", InboxSync.Status.INGESTED, "形式A")),
        )
        val last = LastSync.of(result(inbox, rebuilt), at, zone)

        assertEquals("取り込み1件・失敗0件", last.summary)
        assertFalse(last.hasProblem)
        assertEquals("前回の同期 9/25 12:15  取り込み1件・失敗0件", last.line(zone))
    }

    @Test
    fun `失敗・移動できず・読めない行・キャッシュ未更新を数える`() {
        val inbox = InboxSync.Report(
            listOf(
                InboxSync.Entry("a.csv", InboxSync.Status.REINGESTED, "形式A"),
                InboxSync.Entry("b.xlsx", InboxSync.Status.FAILED, "CSVではない"),
                InboxSync.Entry("c.csv", InboxSync.Status.MOVE_FAILED, "形式B"),
            ),
            skipped = mapOf("a.csv" to listOf(SkippedRow(7, "日付を読めない", "raw"))),
        )
        val last = LastSync.of(result(inbox), at, zone)

        assertEquals("取り込み1件・失敗1件・移動できず1件・読めない行1件・キャッシュ未更新", last.summary)
        assertTrue(last.hasProblem)
        assertTrue(last.detail.contains("7行目: 日付を読めない"))
    }

    @Test
    fun `詳細に生の行は入らない`() {
        val raw = "合計,2026,09,25,ﾔﾏﾀﾞ ﾀﾛｳ,○○銀行,普通,1234567"
        val inbox = InboxSync.Report(
            listOf(InboxSync.Entry("a.csv", InboxSync.Status.INGESTED, "形式A / 読めない行 1")),
            skipped = mapOf("a.csv" to listOf(SkippedRow(12, "金額を読めない", raw))),
        )
        val detail = LastSync.of(result(inbox, rebuilt), at, zone).detail

        assertFalse(detail.contains("ﾔﾏﾀﾞ"))
        assertFalse(detail.contains("1234567"))
        assertTrue(detail.contains("12行目: 金額を読めない"))
    }

    @Test
    fun `端末に保存して読み戻せる`() {
        val last = LastSync(at, "取り込み1件・失敗0件", hasProblem = true, detail = "1行目\n2行目")
        assertEquals(last, LastSync.fromJson(last.toJson()))
        assertNull(LastSync.fromJson("壊れた"))
    }
}
