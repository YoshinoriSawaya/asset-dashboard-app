package com.yswy.assetdashboard.data

/**
 * 目標の並び順を変える(E07-10)。並び順は同じ系列を分け合う目標の配分の順番でもある。
 */
object GoalOrder {

    /**
     * [goals](今の一覧の順)の中で[id]を1つ上・下へ動かし、全部に並びどおりの番号を振り直す。
     *
     * 番号は -N〜-1。Metric項目(0)より上に出る並びを保つ。
     * 作ったばかりの目標は -1 なので、動かすまでは一番下の目標と名前順で並ぶ。
     *
     * @return 番号を振り直した目標。見つからない・端で動かせなければnull
     */
    fun move(goals: List<Item.Goal>, id: String, up: Boolean): List<Item.Goal>? {
        val from = goals.indexOfFirst { it.id == id }
        if (from < 0) return null
        val to = if (up) from - 1 else from + 1
        if (to !in goals.indices) return null
        val reordered = goals.toMutableList().apply { add(to, removeAt(from)) }
        return reordered.mapIndexed { i, goal -> goal.copy(sortOrder = i - reordered.size) }
    }
}
