package com.yswy.assetdashboard.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 各テーブルのCRUDと、重複時の先勝ち/後勝ちを本物のSQLiteで確かめる。 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun itemのCRUD() = runBlocking {
        val dao = db.itemDao()
        val goal = Item.Goal("g1", "車購入", targetYen = 1_000_000, metricKey = "預金・現金")
        val metric = Item.Metric(Item.metricId("合計"), "合計", "合計", sortOrder = 1)

        dao.upsertAll(listOf(goal.toEntity(), metric.toEntity()))
        assertEquals(listOf(goal, metric), dao.getAll().map { it.toItem() })

        val renamed = goal.copy(name = "車の頭金", targetYen = 500_000)
        dao.upsert(renamed.toEntity())
        assertEquals(renamed, dao.findById("g1")?.toItem())

        dao.deleteById("g1")
        assertNull(dao.findById("g1"))
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun metric_pointは同じ項目と日付なら後勝ち() = runBlocking {
        val dao = db.metricPointDao()
        val day = LocalDate.of(2026, 9, 1)

        dao.upsertAll(
            listOf(
                MetricPointEntity.from(MetricPoint("合計", day, 100)),
                MetricPointEntity.from(MetricPoint("合計", day.plusDays(1), 110)),
                MetricPointEntity.from(MetricPoint("年金", day, 50)),
            ),
        )
        dao.upsertAll(listOf(MetricPointEntity("合計", day, 105, MetricOrigin.OVERRIDE)))

        val series = dao.series("合計")
        assertEquals(listOf(105L, 110L), series.map { it.valueYen })
        assertEquals(MetricOrigin.OVERRIDE, series.first().origin)
        assertEquals(110L, dao.latest("合計")?.valueYen)
        assertEquals(listOf("合計", "年金"), dao.keys())

        dao.delete("合計", day)
        assertEquals(1, dao.series("合計").size)
    }

    @Test
    fun bank_transactionは同じ取引なら先勝ち() = runBlocking {
        val dao = db.bankTransactionDao()
        val tx = BankTransaction(LocalDate.of(2026, 9, 1), "給与", null, 300_000, 500_000)

        val first = dao.insertAll(listOf(BankTransactionEntity.from(tx, "file-a")))
        // 期間が重なる別のCSVから同じ取引が来た。残高の並びが違ってもキーは同じ。
        val second = dao.insertAll(
            listOf(BankTransactionEntity.from(tx.copy(balance = 999_999), "file-b")),
        )

        assertEquals(1, first.count { it != -1L })
        assertEquals(listOf(-1L), second)
        assertEquals(1, dao.count())

        val stored = dao.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).single()
        assertEquals("file-a", stored.sourceFileId)
        assertEquals(500_000L, stored.balance)
    }

    @Test
    fun bank_transactionの期間指定は両端を含む() = runBlocking {
        val dao = db.bankTransactionDao()
        val days = listOf(1, 15, 30).map { LocalDate.of(2026, 9, it) }
        dao.insertAll(days.map { BankTransactionEntity.from(BankTransaction(it, "x", 100, null, null), "f") })
        dao.insertAll(listOf(BankTransactionEntity.from(BankTransaction(LocalDate.of(2026, 10, 1), "x", 100, null, null), "f")))

        assertEquals(days, dao.between(days.first(), days.last()).map { it.date })
    }
}
