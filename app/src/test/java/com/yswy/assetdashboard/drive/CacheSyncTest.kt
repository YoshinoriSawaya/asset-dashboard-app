package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemType
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CacheSyncTest {

    private val day = LocalDate.of(2026, 9, 1)

    private fun metrics(fileId: String, vararg points: MetricPoint) =
        BackupReader.Backup(fileId, "$fileId.csv", ParsedData.Metrics(points.toList()))

    private fun transactions(fileId: String, vararg rows: BankTransaction) =
        BackupReader.Backup(fileId, "$fileId.csv", ParsedData.Transactions(rows.toList()))

    private fun correction(key: String, value: Long, kind: Corrections.Kind, at: Long) =
        Corrections.Entry(key, day, value, kind, correctedAt = at)

    @Test
    fun `期間の重なる明細は先に取り込んだほうを残す`() {
        val salary = BankTransaction(day, "給与", null, 300_000, 500_000)
        val rent = BankTransaction(day.plusDays(2), "家賃", 80_000, null, 420_000)

        val snapshot = CacheSync.build(
            listOf(
                transactions("old", salary),
                // 同じ取引が別のCSVにも載っている。残高の並びは違ってもよい。
                transactions("new", salary.copy(balance = 999_999), rent),
            ),
            corrections = emptyList(),
            settings = emptyList(),
        )

        assertEquals(listOf("old", "new"), snapshot.transactions.map { it.sourceFileId })
        assertEquals(500_000L, snapshot.transactions.first().balance)
        assertEquals(1, snapshot.droppedDuplicates)
    }

    @Test
    fun `同じ日のMetricは後から取り込んだほうを採る`() {
        val snapshot = CacheSync.build(
            listOf(
                metrics("old", MetricPoint("合計", day, 100), MetricPoint("年金", day, 10)),
                metrics("new", MetricPoint("合計", day, 120)),
            ),
            corrections = emptyList(),
            settings = emptyList(),
        )

        assertEquals(mapOf("合計" to 120L, "年金" to 10L), snapshot.points.associate { it.metricKey to it.valueYen })
        assertEquals(1, snapshot.droppedDuplicates)
    }

    @Test
    fun `補正を重ね、由来を残す`() {
        val snapshot = CacheSync.build(
            listOf(metrics("f", MetricPoint("合計", day, 100))),
            corrections = listOf(
                correction("合計", 90, Corrections.Kind.OVERRIDE, at = 1),
                correction("合計", 95, Corrections.Kind.OVERRIDE, at = 2),
                // CSVに載らない値を足す
                correction("現金", 30, Corrections.Kind.MANUAL, at = 1),
            ),
            settings = emptyList(),
        )

        val byKey = snapshot.points.associateBy { it.metricKey }
        assertEquals(95L, byKey.getValue("合計").valueYen)
        assertEquals(MetricOrigin.OVERRIDE, byKey.getValue("合計").origin)
        assertEquals(MetricOrigin.MANUAL, byKey.getValue("現金").origin)
    }

    @Test
    fun `settingsに無い系列だけMetric項目を自動で足す`() {
        val renamed = Item.Metric(Item.metricId("投資信託"), "NISA", "投資信託").toEntity()
        val goal = Item.Goal("g1", "車購入", 1_000_000, "預金・現金").toEntity()

        val snapshot = CacheSync.build(
            listOf(
                metrics(
                    "f",
                    MetricPoint("投資信託", day, 1),
                    MetricPoint("預金・現金", day, 2),
                    MetricPoint("合計", day, 3),
                ),
            ),
            corrections = listOf(correction("現金", 30, Corrections.Kind.MANUAL, at = 1)),
            settings = listOf(renamed, goal),
        )

        // 人が変えたものはそのまま、残りは列名をそのまま名前にして足す
        assertEquals(
            listOf("metric:投資信託" to "NISA", "g1" to "車購入") +
                listOf("合計", "現金", "預金・現金").map { "metric:$it" to it },
            snapshot.items.map { it.id to it.name },
        )
        assertEquals(ItemType.METRIC, snapshot.items.last().type)
    }

    @Test
    fun `何も無ければ空`() {
        val snapshot = CacheSync.build(emptyList(), emptyList(), emptyList())
        assertEquals(0, snapshot.items.size + snapshot.points.size + snapshot.transactions.size)
    }
}
