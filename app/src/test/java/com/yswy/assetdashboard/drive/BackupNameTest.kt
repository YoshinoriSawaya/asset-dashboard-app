package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** backupの名前がぶつかる問題(E01-16)。値はすべて作り物。 */
class BackupNameTest {

    private fun backup(fileId: String, name: String, yen: Long) =
        BackupReader.Backup(fileId, name, ParsedData.Metrics(listOf(MetricPoint("預金", LocalDate.of(2026, 9, 1), yen))))

    private fun file(id: String, name: String) = DriveApi.DriveFile(id, name, "text/csv", "2026-09-26T00:00:00Z", 10, null)

    @Test
    fun `名前が同じでも元ファイルが違えばbackupの名前は違う`() {
        assertNotEquals(BackupWriter.nameFor("明細 (1).csv", "idA"), BackupWriter.nameFor("明細 (1).csv", "idB"))
        assertEquals("明細 (1).csv.idA.json", BackupWriter.nameFor("明細 (1).csv", "idA"))
    }

    @Test
    fun `同じ元ファイルのbackupが2つあれば、後のほうだけ使う`() {
        val old = backup("idA", "明細.csv", 100) // 古い名前で残ったもの
        val other = backup("idB", "明細.csv", 50)
        val new = backup("idA", "明細.csv", 120) // 新しい名前で書き直したもの
        assertEquals(listOf(other, new), CacheSync.latestPerSource(listOf(old, other, new)))
    }

    @Test
    fun `processedにあってbackupの元になっていないファイルを挙げる`() {
        val processed = listOf(file("idA", "a.csv"), file("idB", "明細 (1).csv"), file("idC", "明細 (1).csv"))
        val backups = listOf(backup("idA", "a.csv", 1), backup("idC", "明細 (1).csv", 1))
        assertEquals(listOf("明細 (1).csv"), CacheSync.missingBackups(processed, backups))
    }

    @Test
    fun `backupの抜けは同期の結果に出して、問題ありにする`() {
        val inbox = InboxSync.Report(listOf(InboxSync.Entry("a.csv", InboxSync.Status.INGESTED, "形式A")))
        val rebuilt = CacheSync.Outcome.Rebuilt(
            CacheSync.Snapshot(emptyList(), emptyList(), emptyList(), 0), 3, emptyList(), missingBackups = listOf("明細 (1).csv"),
        )
        val last = LastSync.of(FullSync.Result(emptyList(), inbox, rebuilt), java.time.Instant.parse("2026-09-26T00:00:00Z"))
        assertEquals("取り込み1件・失敗0件・backup無し1件", last.summary)
        assertTrue(last.hasProblem)
        assertTrue(last.detail.contains("backupの無い取り込み済み 1件: 明細 (1).csv"))
    }
}
