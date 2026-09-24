package com.yswy.assetdashboard.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 未知フォーマットからの金額抽出。
 *
 * 推測なので「必ず正しい」は保証できない。ここで確かめたいのは
 * **エラーで止まらず、拾えるものは拾えること**(E01-05の完了条件)。
 */
class FallbackParserTest {

    private val anyDate = LocalDate.of(2026, 9, 25)

    private fun parse(csv: String, fallbackDate: LocalDate = anyDate): ParsedData.Metrics {
        val rows = CsvText.splitRows(csv.trimIndent())
        val result = FallbackParser.parse(rows.first(), rows.drop(1), fallbackDate)
        return result.data as ParsedData.Metrics
    }

    @Test
    fun `未知の列名でも日付と金額を拾う`() {
        val data = parse(
            """
            取引日,なんとか区分,きんがく,備考
            2026/9/1,A,"12,345",メモ
            2026/9/2,B,"6,789",メモ
            """,
        )

        assertEquals(listOf("きんがく"), data.keys)
        assertEquals(6789L, data.latest("きんがく"))
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2026, 9, 2), data.dateRange)
    }

    @Test
    fun `日付の列が無ければファイルの更新日を使う`() {
        val fileDate = LocalDate.of(2026, 3, 3)
        val data = parse(
            """
            項目,金額
            預金,"1,000,000"
            投信,"2,000,000"
            """,
            fallbackDate = fileDate,
        )

        assertEquals(2, data.points.size)
        assertTrue(data.points.all { it.date == fileDate })
        // 「項目」列がキーになるとは推測できないので、両方とも「金額」系列になる。
        // 同じ日付で衝突するため、最新値は後の行が勝つ(縦持ちCSVの既知の限界)。
        assertEquals(listOf("金額"), data.keys)
        assertEquals(2000000L, data.latest("金額"))
    }

    @Test
    fun `列名が空なら位置で呼ぶ`() {
        val data = parse(
            """
            日付,,
            2026/9/1,100,200
            2026/9/2,300,400
            """,
        )

        assertEquals(listOf("列2", "列3"), data.keys)
        assertEquals(300L, data.latest("列2"))
    }

    @Test
    fun `金額らしくない文字列は拾わない`() {
        // parseAmountは緩いので ABC123 でも123を返すが、
        // 列の推測ではこれを金額列と見なしてはいけない。
        val data = parse(
            """
            日付,コード,金額
            2026/9/1,ABC123,500
            2026/9/2,DEF456,600
            """,
        )

        assertEquals(listOf("金額"), data.keys)
    }

    @Test
    fun `金額の列が1つも無ければ何も返さない`() {
        val data = parse(
            """
            名前,メモ
            たなか,ほげ
            すずき,ふが
            """,
        )

        assertTrue(data.points.isEmpty())
    }

    @Test
    fun `空のCSVでも落ちない`() {
        val result = FallbackParser.parse(emptyList(), emptyList(), anyDate)
        assertTrue((result.data as ParsedData.Metrics).points.isEmpty())
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `列が欠けた行があっても落ちない`() {
        val rows = listOf(
            listOf("日付", "金額"),
            listOf("2026/9/1", "100"),
            listOf("2026/9/2"), // 金額の列が無い
        )
        val result = FallbackParser.parse(rows.first(), rows.drop(1), anyDate)
        val data = result.data as ParsedData.Metrics

        assertEquals(1, data.points.size)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun `一部の行だけ日付が読めなくても列としては日付とみなす`() {
        val data = parse(
            """
            日付,金額
            2026/9/1,100
            2026/9/2,200
            合計,300
            """,
        )

        // 「合計」の行はfallbackDateになるが、拾えること自体は止めない
        assertEquals(3, data.points.size)
        assertTrue(data.points.any { it.date == anyDate })
    }

    @Test
    fun `マイナスや通貨記号つきでも拾う`() {
        val data = parse(
            """
            日付,金額
            2026/9/1,-500
            2026/9/2,￥1200
            """,
        )

        assertEquals(listOf(-500L, 1200L), data.points.map { it.valueYen })
    }

    @Test
    fun `既知の形式はフォールバックに回らない`() {
        val header = listOf("日付", "合計（円）")
        assertEquals(AssetTrendAdapter, CsvAdapters.findFor(header))
    }
}
