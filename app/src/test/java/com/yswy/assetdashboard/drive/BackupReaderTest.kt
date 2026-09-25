package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BackupReaderTest {

    private val day = LocalDate.of(2026, 9, 1)

    @Test
    fun `取引は書いたとおりに読み戻せる`() {
        val rows = listOf(
            BankTransaction(day, "給与", null, 300_000, 500_000, memo = "9月分"),
            // 0とnullの区別、残高列の無い形式
            BankTransaction(day.plusDays(1), "手数料", 0, null, null, label = "銀行"),
        )
        val json = BackupWriter.render("meisai.csv", "file-1", "形式A", ParsedData.Transactions(rows))

        val backup = BackupReader.parse(json)!!
        assertEquals("file-1", backup.sourceFileId)
        assertEquals("meisai.csv", backup.sourceFileName)
        assertEquals(ParsedData.Transactions(rows), backup.data)
    }

    @Test
    fun `Metricは書いたとおりに読み戻せる`() {
        val points = listOf(MetricPoint("合計", day, 1_500_000), MetricPoint("年金", day, -1))
        val json = BackupWriter.render("推移.csv", "file-2", "資産推移形式", ParsedData.Metrics(points))

        assertEquals(ParsedData.Metrics(points), BackupReader.parse(json)!!.data)
    }

    @Test
    fun `知らない版や種類はnullにして呼ぶ側で飛ばす`() {
        assertNull(BackupReader.parse("""{"formatVersion":2,"sourceFileId":"x","kind":"metrics"}"""))
        assertNull(BackupReader.parse("""{"formatVersion":1,"sourceFileId":"x","kind":"???"}"""))
        assertNull(BackupReader.parse("""{"formatVersion":1,"kind":"metrics"}"""))
    }

    @Test
    fun `壊れた行だけ落として残りは読む`() {
        val json = """
            {"formatVersion":1,"sourceFileId":"x","kind":"metrics","metrics":[
              {"metricKey":"合計","date":"2026-09-01","valueYen":100},
              {"metricKey":"合計","date":"9月1日","valueYen":100},
              {"metricKey":"","date":"2026-09-01","valueYen":100},
              {"metricKey":"合計","date":"2026-09-02"}
            ]}
        """.trimIndent()

        val data = BackupReader.parse(json)!!.data as ParsedData.Metrics
        assertEquals(listOf(MetricPoint("合計", day, 100)), data.points)
    }
}
