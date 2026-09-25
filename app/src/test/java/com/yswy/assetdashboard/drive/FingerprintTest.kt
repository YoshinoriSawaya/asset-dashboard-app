package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class FingerprintTest {

    private val day = LocalDate.of(2026, 9, 1)
    private val metrics = BackupReader.Backup("m", "m.csv", ParsedData.Metrics(listOf(MetricPoint("合計", day, 100), MetricPoint("年金", day, 10))))
    private val txs = BackupReader.Backup("t", "t.csv", ParsedData.Transactions(listOf(BankTransaction(day, "給与", null, 300, 500))))
    private val goal = Item.Goal("g", "車", 1_000, "合計").toEntity()

    private fun fp(backups: List<BackupReader.Backup>, corrections: List<Corrections.Entry> = emptyList(), settings: List<com.yswy.assetdashboard.data.ItemEntity> = listOf(goal)) =
        CacheSync.build(backups, corrections, settings).fingerprint

    @Test
    fun `同じ中身なら、読む順が違っても同じ指紋`() {
        assertEquals(fp(listOf(metrics, txs)), fp(listOf(txs, metrics)))
        assertEquals(8, fp(listOf(metrics)).length)
    }

    @Test
    fun `値・補正・項目が1つでも違えば指紋が変わる`() {
        val base = fp(listOf(metrics, txs))
        val changedValue = BackupReader.Backup("m", "m.csv", ParsedData.Metrics(listOf(MetricPoint("合計", day, 101), MetricPoint("年金", day, 10))))
        assertNotEquals(base, fp(listOf(changedValue, txs)))
        assertNotEquals(base, fp(listOf(metrics, txs), corrections = listOf(Corrections.Entry("合計", day, 100, Corrections.Kind.OVERRIDE, correctedAt = 1))))
        assertNotEquals(base, fp(listOf(metrics, txs), settings = listOf(goal.copy(name = "車の購入"))))
    }
}
