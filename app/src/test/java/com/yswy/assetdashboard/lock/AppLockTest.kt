package com.yswy.assetdashboard.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLockTest {

    @Before
    fun setUp() = AppLock.reset()

    @Test
    fun `最初はロックされている`() {
        assertFalse(AppLock.unlocked)
    }

    @Test
    fun `1分未満で戻ればロックし直さない(画面の回転など)`() {
        AppLock.markUnlocked()
        AppLock.onBackground(now = 0)
        AppLock.onForeground(now = 59_999)
        assertTrue(AppLock.unlocked)
    }

    @Test
    fun `1分以上裏にいたらロックし直す`() {
        AppLock.markUnlocked()
        AppLock.onBackground(now = 0)
        AppLock.onForeground(now = 60_000)
        assertFalse(AppLock.unlocked)
    }

    @Test
    fun `裏に回っていないのに表に戻っても変えない`() {
        AppLock.markUnlocked()
        AppLock.onForeground(now = 1_000_000)
        assertTrue(AppLock.unlocked)
    }
}
