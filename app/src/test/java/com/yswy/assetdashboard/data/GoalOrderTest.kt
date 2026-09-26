package com.yswy.assetdashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalOrderTest {

    private val goals = listOf("a", "b", "c").map { Item.Goal(it, it, 100, sortOrder = -1) }

    @Test
    fun `1つ動かして、全部に並びどおりの番号を振り直す`() {
        val moved = GoalOrder.move(goals, "c", up = true)!!
        assertEquals(listOf("a", "c", "b"), moved.map { it.id })
        assertEquals(listOf(-3, -2, -1), moved.map { it.sortOrder })
        assertEquals(listOf("b", "a", "c"), GoalOrder.move(goals, "a", up = false)!!.map { it.id })
    }

    @Test
    fun `端や見つからない目標は動かさない`() {
        assertNull(GoalOrder.move(goals, "a", up = true))
        assertNull(GoalOrder.move(goals, "c", up = false))
        assertNull(GoalOrder.move(goals, "x", up = true))
    }
}
