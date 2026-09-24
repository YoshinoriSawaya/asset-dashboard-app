package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.SkippedRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SyncLogTest {

    private val now = ZonedDateTime.of(2026, 9, 25, 10, 30, 0, 0, ZoneOffset.ofHours(9))

    private fun entry(name: String, status: InboxSync.Status, detail: String = "") =
        InboxSync.Entry(name, status, detail)

    @Test
    fun `失敗した行の中身はログに書かない`() {
        val raw = "合計,2026,09,25,ﾔﾏﾀﾞ ﾀﾛｳ,○○銀行,○○支店,普通,1234567,,残高,,,,250000,,"
        val report = InboxSync.Report(
            listOf(entry("meisai.csv", InboxSync.Status.INGESTED, "取引5行")),
        )
        val details = mapOf("meisai.csv" to listOf(SkippedRow(7, "金額が空", raw)))

        val text = SyncLog.render(report, details, now)

        // 行番号と理由は出る
        assertTrue(text.contains("7行目"))
        assertTrue(text.contains("金額が空"))
        // 中身(口座番号・氏名)は出ない
        assertFalse(text.contains("1234567"))
        assertFalse(text.contains("ﾔﾏﾀﾞ"))
        assertFalse(text.contains("○○銀行"))
    }

    @Test
    fun `失敗したファイルは理由つきで出る`() {
        val report = InboxSync.Report(
            listOf(entry("meisai.xlsx", InboxSync.Status.FAILED, "Excel(xlsx)のように見える")),
        )

        val text = SyncLog.render(report, emptyMap(), now)

        assertTrue(text.contains("meisai.xlsx"))
        assertTrue(text.contains("Excel"))
        assertTrue(text.contains("2026-09-25 10:30:00"))
    }

    @Test
    fun `成功しただけのファイルはログに並べない`() {
        val report = InboxSync.Report(
            listOf(entry("ok.csv", InboxSync.Status.INGESTED, "資産推移形式")),
        )

        val text = SyncLog.render(report, emptyMap(), now)

        assertFalse(text.contains("ok.csv"))
    }

    @Test
    fun `読めない行が多くても打ち切る`() {
        val skipped = (1..80).map { SkippedRow(it, "日付を読めない", "raw$it") }
        val report = InboxSync.Report(
            listOf(entry("many.csv", InboxSync.Status.INGESTED, "取引0行")),
        )

        val text = SyncLog.render(report, mapOf("many.csv" to skipped), now)

        assertTrue(text.contains("読めなかった行 80件"))
        assertTrue(text.contains("ほか30件"))
    }

    @Test
    fun `問題が無ければ書く必要が無い`() {
        val report = InboxSync.Report(
            listOf(entry("ok.csv", InboxSync.Status.INGESTED, "資産推移形式")),
        )
        assertFalse(report.hasProblem)
    }

    @Test
    fun `移動できなかったファイルは問題として扱う`() {
        val report = InboxSync.Report(
            listOf(entry("ok.csv", InboxSync.Status.MOVE_FAILED, "移動できない")),
        )
        assertTrue(report.hasProblem)
    }

    @Test
    fun `結果の要約は状態ごとに件数を出す`() {
        val report = InboxSync.Report(
            listOf(
                entry("a.csv", InboxSync.Status.INGESTED),
                entry("b.csv", InboxSync.Status.INGESTED),
                entry("c.csv", InboxSync.Status.FAILED),
            ),
        )
        assertTrue(report.summary().contains("取り込み2件"))
        assertTrue(report.summary().contains("失敗1件"))
    }
}
