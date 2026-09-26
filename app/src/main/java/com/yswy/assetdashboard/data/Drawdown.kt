package com.yswy.assetdashboard.data

/**
 * 生活防衛資金の取り崩し(E07-12)。
 *
 * 期日に向けて積み増す目標(E07-11。車の頭金など)が届かなかったとき、
 * 足りない分を生活防衛資金から**下限まで**取り崩すのは想定内として扱う。
 *
 * ## 取り崩しを記録しない
 * 取り崩したかどうかは、生活防衛資金の系列(口座の残高)が目標額を割ったことで
 * 分かる。別に「取り崩しを実行した」記録を持つと、残高と記録が食い違う
 * (記録したのに下ろしていない、下ろしたのに記録していない)。
 * 状態は毎回、今の値と目標額・下限から出す([FundState])。
 */
object Drawdown {

    /** 生活防衛資金が今どこにいるか。 */
    enum class FundState {
        /** 目標額以上。 */
        FULL,

        /** 目標額を割ったが、下限以上。取り崩してよい範囲。 */
        DRAWN,

        /** 下限を割った。回復を急ぐ。 */
        BELOW_FLOOR,

        /** 目標額を割った。下限を決めていないので、取り崩してよいかは分からない。 */
        SHORT,
    }

    /** 生活費から目標額を出す目標(生活防衛資金)でなければ、または値が分からなければnull。 */
    fun stateOf(fund: ItemOverview.Goal): FundState? {
        if (fund.item.autoTarget == null) return null
        val target = fund.targetYen ?: return null
        val current = fund.currentYen ?: return null
        val floor = fund.floorYen
        return when {
            current >= target -> FundState.FULL
            floor == null -> FundState.SHORT
            current >= floor -> FundState.DRAWN
            else -> FundState.BELOW_FLOOR
        }
    }

    /**
     * 期日に向けた目標の足りない分を、生活防衛資金で補えるか。
     * @param availableYen 下限まで取り崩せる額。下限を決めていなければnull
     */
    data class Cover(
        val fundName: String,
        val shortfallYen: Long,
        val availableYen: Long?,
    ) {
        /** 下限を割らずに補えるか。下限が無ければnull(分からない)。 */
        val fits: Boolean? get() = availableYen?.let { shortfallYen <= it }
    }

    /**
     * @param goal 期日に向けて積み増す目標(E07-11)
     * @param funds 全項目の目標。生活費から目標額を出すもの(生活防衛資金)を探す
     * @return 積み増しの期間中か期日を過ぎていて、足りない分があれば。
     *   生活防衛資金が無い・値が分からなければnull
     */
    fun cover(goal: ItemOverview.Goal, funds: List<ItemOverview.Goal>, today: java.time.LocalDate): Cover? {
        val rampUp = RampUp.of(goal.item, goal.targetYen, goal.currentYen, today)
        if (rampUp !is RampUp.Active && rampUp != RampUp.Overdue) return null
        val shortfall = (goal.targetYen ?: return null) - (goal.currentYen ?: return null)
        if (shortfall <= 0) return null

        val fund = primaryFund(funds) ?: return null
        val current = fund.currentYen ?: return null
        val available = fund.floorYen?.let { (current - it).coerceAtLeast(0) }
        return Cover(fund.item.name, shortfall, available)
    }

    /**
     * 生活防衛資金として見る目標。生活費から目標額を出す目標が複数あれば、
     * 一覧で上にあるもの(隠したものは除く)。
     */
    fun primaryFund(goals: List<ItemOverview.Goal>): ItemOverview.Goal? =
        goals.filter { it.item.autoTarget != null && !it.item.hidden }.minByOrNull { it.item.sortOrder }
}
