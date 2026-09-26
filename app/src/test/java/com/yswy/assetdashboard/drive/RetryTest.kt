package com.yswy.assetdashboard.drive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** backupを落とせなかったときの読み直し(E02-06)。 */
class RetryTest {

    @Test
    fun `失敗しても待って読み直し、読めたらその値`() = runBlocking {
        val waited = mutableListOf<Long>()
        var calls = 0
        val value = CacheSync.withRetry(listOf(2_000L, 5_000L), wait = { waited += it }) {
            calls++
            if (calls < 3) throw IOException("まだ無い") else "中身"
        }
        assertEquals("中身", value)
        assertEquals(3, calls)
        assertEquals(listOf(2_000L, 5_000L), waited)
    }

    @Test
    fun `一度で読めれば待たない`() = runBlocking {
        val waited = mutableListOf<Long>()
        assertEquals(1, CacheSync.withRetry(wait = { waited += it }) { 1 })
        assertTrue(waited.isEmpty())
    }

    @Test
    fun `読み直してもだめなら最後の例外を投げる`() = runBlocking {
        val waited = mutableListOf<Long>()
        var calls = 0
        val error = runCatching {
            CacheSync.withRetry(listOf(1L, 1L), wait = { waited += it }) {
                calls++
                throw IOException("${calls}回目")
            }
        }.exceptionOrNull()
        assertEquals("3回目", error?.message)
        assertEquals(3, calls)
        assertEquals(listOf(1L, 1L), waited)
    }
}
