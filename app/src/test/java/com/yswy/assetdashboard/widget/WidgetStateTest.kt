package com.yswy.assetdashboard.widget

import com.yswy.assetdashboard.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetStateTest {

    private val latest = LocalDate.of(2026, 9, 25)

    @Test
    fun `期限内なら平常、期限切れなら赤`() {
        val ok = WidgetState.of(SyncStatus(latest, isDue = false))
        val due = WidgetState.of(SyncStatus(latest, isDue = true))
        assertFalse(ok.isDue)
        assertTrue(due.isDue)
        assertEquals("資産", ok.label)
        assertEquals("同期して", due.label)
    }

    @Test
    fun `データが無ければ赤`() {
        assertTrue(WidgetState.of(SyncStatus(null, isDue = true)).isDue)
    }

    @Test
    fun `数字を出さない`() {
        for (state in listOf(
            WidgetState.of(SyncStatus(latest, isDue = false)),
            WidgetState.of(SyncStatus(latest, isDue = true)),
        )) {
            assertFalse(state.label.any { it.isDigit() })
            assertFalse(state.description.any { it.isDigit() })
        }
    }
}
