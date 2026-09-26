package com.yswy.assetdashboard.data

/**
 * 目標を満たしているかの判定と、足りないときの月々の額(E07-19)。
 *
 * 目標に「足りないとき何か月で埋めるか」([Item.Goal.refillMonths])を決めておくと、
 * 月々の額を1つに決めて出す。生活防衛資金なら「取り崩したら半年で戻す」のように決めておき、
 * 足りなくなったら毎月いくら積み立てればよいかが1つの答えになる(本人の案)。
 *
 * 決めていない目標には出さない(今までどおり3・6・12か月の候補を出す。E07-07)。
 */
sealed interface Refill {

    /** 満たしている。積み立ては要らない。 */
    data class Full(
        /** 生活費の何か月分あるか。生活費から出す目標でなければnull。 */
        val monthsCovered: Double?,
    ) : Refill

    /** 足りない。[months]か月で埋めるなら月々[monthlyYen]。 */
    data class Short(
        val shortfallYen: Long,
        val months: Int,
        val monthlyYen: Long,
        /** 下限(E07-12)も割っている。 */
        val belowFloor: Boolean,
    ) : Refill

    companion object {
        /** 埋める期間を決めていない、目標額か今の値が分からなければnull。 */
        fun of(overview: ItemOverview.Goal): Refill? {
            val months = overview.item.refillMonths?.takeIf { it > 0 } ?: return null
            val target = overview.targetYen ?: return null
            val current = overview.currentYen ?: return null
            if (current >= target) return Full(FundOutlook.of(overview)?.monthsCovered)

            // 月額の切り上げ方は回復プラン(E07-07)とそろえる
            val plan = RecoveryPlan.of(target, current, listOf(months)) ?: return null
            val floor = overview.floorYen
            return Short(
                shortfallYen = plan.shortfallYen,
                months = months,
                monthlyYen = plan.options.single().monthlyYen,
                belowFloor = floor != null && current < floor,
            )
        }
    }
}
