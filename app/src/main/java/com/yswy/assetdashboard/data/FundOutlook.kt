package com.yswy.assetdashboard.data

/**
 * 生活防衛資金の見通し(E09-02)。生活費から目標額を出す目標(E07-06)に出す。
 *
 * - **何か月分あるか**: 今の額 ÷ 生活費の月平均。収入が止まったら何か月暮らせるか
 * - **目標を割るまで**: 今は目標以上だが減っているとき、このペースで何か月後に
 *   目標を下回るか。下回る前に気づけるように(下回ってからはE07-07)
 */
data class FundOutlook(
    /** 生活費の何か月分あるか。生活費の平均が分からなければnull。 */
    val monthsCovered: Double?,
    /** このペースで目標を割るまでの月数。減っていない、もう下回っている、ペースが分からなければnull。 */
    val monthsUntilBelowTarget: Int?,
) {
    companion object {
        /** これ以内に目標を割りそうなら通知する(E05)。 */
        const val WARN_MONTHS = 3

        fun of(overview: ItemOverview.Goal): FundOutlook? {
            if (overview.item.autoTarget == null) return null
            val current = overview.currentYen ?: return null
            val average = overview.auto?.monthlyAverageYen
            val covered = average?.takeIf { it > 0 }?.let { current.toDouble() / it }

            val target = overview.targetYen
            val monthly = overview.monthlyPaceYen
            // 系列を分け合っていると割当額は目標額で止まるので、使える額で数える(E07-10)
            val available = overview.availableYen ?: current
            val until = if (target != null && monthly != null && monthly < 0 && available >= target) {
                ((available - target) / -monthly).toInt()
            } else {
                null
            }
            return FundOutlook(covered, until)
        }
    }
}
