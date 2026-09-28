package com.yswy.assetdashboard.drive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 落としたbackupの写し(E02-08)。中身は作り物。 */
class BackupCopiesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val content = """{"formatVersion":1}""".toByteArray()

    private fun driveFile(id: String, md5: String?) = DriveApi.DriveFile(id, "$id.json", "application/json", "2026-09-28T00:00:00Z", null, md5)

    @Test
    fun `中身のmd5がDriveと同じ写しだけを使う`() {
        val copies = BackupCopies(tmp.newFolder("copies"))
        val md5 = BackupCopies.md5(content)
        // まだ無い
        assertNull(copies.read(driveFile("a", md5)))
        copies.write("a", content)
        assertArrayEquals(content, copies.read(driveFile("a", md5)))
        // Drive側で中身が変わった(md5が違う)
        assertNull(copies.read(driveFile("a", BackupCopies.md5("別の中身".toByteArray()))))
        // Driveがmd5を返さなければ使わない(落とし直す)
        assertNull(copies.read(driveFile("a", null)))
        // 大文字で返っても同じとみなす
        assertArrayEquals(content, copies.read(driveFile("a", md5.uppercase())))
    }

    @Test
    fun `壊れた写しは使わない`() {
        val dir = tmp.newFolder("copies")
        val copies = BackupCopies(dir)
        copies.write("a", content)
        File(dir, "a.json").writeBytes(content.copyOf(content.size - 1))
        assertNull(copies.read(driveFile("a", BackupCopies.md5(content))))
    }

    @Test
    fun `一覧に無いものと書きかけは消す`() {
        val dir = tmp.newFolder("copies")
        val copies = BackupCopies(dir)
        copies.write("a", content)
        copies.write("b", content)
        File(dir, "c.tmp").writeBytes(content)
        copies.keepOnly(setOf("a"))
        assertEquals(listOf("a.json"), dir.listFiles()!!.map { it.name })
    }

    @Test
    fun `置き場所が無くても落ちない`() {
        val copies = BackupCopies(File(tmp.root, "無い/フォルダ"))
        assertNull(copies.read(driveFile("a", BackupCopies.md5(content))))
        copies.keepOnly(emptySet())
        copies.write("a", content)
        assertArrayEquals(content, copies.read(driveFile("a", BackupCopies.md5(content))))
    }

    @Test
    fun `前に作り直したときの組み合わせを覚え、写しを片付けても消えない`() {
        val dir = tmp.newFolder("copies")
        val copies = BackupCopies(dir)
        assertNull(copies.lastRebuild())
        copies.saveRebuild("key1", "fp1")
        copies.keepOnly(emptySet())
        assertEquals("key1" to "fp1", copies.lastRebuild())
    }

    private fun f(id: String, md5: String? = "m$id", modified: String = "2026-09-01T00:00:00Z", name: String = "$id.json") =
        DriveApi.DriveFile(id, name, "application/json", modified, null, md5)

    @Test
    fun `入力の組み合わせは中身・順番・版が変われば変わり、md5が無ければ作らない`() {
        val base = CacheSync.inputKey("2.33.2", listOf(f("a"), f("b")), listOf(f("c")), listOf(f("s1"), f("s2")))!!
        // 同じ入力なら同じ。corrections・settingsは一覧の順によらない
        assertEquals(base, CacheSync.inputKey("2.33.2", listOf(f("a"), f("b")), listOf(f("c")), listOf(f("s2"), f("s1"))))
        // backupの中身・順・増減、settingsの中身、版のどれが変わっても違う
        val changed = listOf(
            CacheSync.inputKey("2.33.2", listOf(f("a", "x"), f("b")), listOf(f("c")), listOf(f("s1"), f("s2"))),
            CacheSync.inputKey("2.33.2", listOf(f("b"), f("a")), listOf(f("c")), listOf(f("s1"), f("s2"))),
            CacheSync.inputKey("2.33.2", listOf(f("a")), listOf(f("c")), listOf(f("s1"), f("s2"))),
            CacheSync.inputKey("2.33.2", listOf(f("a"), f("b")), listOf(f("c")), listOf(f("s1", "y"), f("s2"))),
            CacheSync.inputKey("2.33.2", listOf(f("a"), f("b")), emptyList(), listOf(f("s1"), f("s2"))),
            CacheSync.inputKey("2.34.0", listOf(f("a"), f("b")), listOf(f("c")), listOf(f("s1"), f("s2"))),
        )
        changed.forEach { assertTrue(it != null && it != base) }
        // md5の無いファイルがあると、変わったかを確かめられないので作らない(毎回作り直す)
        assertNull(CacheSync.inputKey("2.33.2", listOf(f("a", null)), emptyList(), emptyList()))
    }

    @Test
    fun `md5は既知の値と一致する`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", BackupCopies.md5("abc".toByteArray()))
    }
}
