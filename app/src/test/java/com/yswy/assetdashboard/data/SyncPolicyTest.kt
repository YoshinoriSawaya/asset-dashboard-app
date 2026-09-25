package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SyncPolicyTest {

    private val latest = LocalDate.of(2026, 9, 25)
    private val now = Instant.parse("2026-11-01T09:00:00Z")

    @Test
    fun `データから30日目までは期限内、31日目から同期が必要`() {
        assertEquals(LocalDate.of(2026, 10, 25), SyncPolicy.dueDate(latest))
        assertFalse(SyncPolicy.isDue(latest, LocalDate.of(2026, 10, 25)))
        assertTrue(SyncPolicy.isDue(latest, LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `データが無ければ同期が必要`() {
        assertNull(SyncPolicy.dueDate(null))
        assertTrue(SyncPolicy.isDue(null, latest))
    }

    @Test
    fun `期限内なら自動同期しない`() {
        assertFalse(SyncPolicy.shouldAutoSync(latest, latest.plusDays(3), lastAutoSyncAt = null, now = now))
    }

    @Test
    fun `期限切れなら自動同期する`() {
        assertTrue(SyncPolicy.shouldAutoSync(latest, latest.plusDays(40), lastAutoSyncAt = null, now = now))
        assertTrue(SyncPolicy.shouldAutoSync(null, latest, lastAutoSyncAt = null, now = now))
    }

    @Test
    fun `期限切れでも1時間以内に試していれば見送る`() {
        val today = latest.plusDays(40)
        assertFalse(SyncPolicy.shouldAutoSync(latest, today, now.minusSeconds(59 * 60), now))
        assertTrue(SyncPolicy.shouldAutoSync(latest, today, now.minusSeconds(60 * 60), now))
    }

    @Test
    fun `Metricと明細の新しいほうで判定する`() {
        val older = LocalDate.of(2026, 8, 31)
        assertEquals(latest, SyncPolicy.latestOf(older, latest))
        assertEquals(older, SyncPolicy.latestOf(null, older))
        assertNull(SyncPolicy.latestOf(null, null))
    }
}
