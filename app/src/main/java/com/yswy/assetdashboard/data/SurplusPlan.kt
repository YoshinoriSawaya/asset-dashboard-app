package com.yswy.assetdashboard.data

import java.time.LocalDate

/**
 * 余剰資金(ボーナス・臨時収入)をどの目標へ回すかの案(E10-02)。案を出すだけで、振替は本人がする。
 *
 * ## 決まった段と、一覧の順(本人が選んだ)
 * 急ぐものから段を固定し、同じ段の中は目標の一覧の並び(E07-10の配分の順番と同じ)で満たす。
 * 1. [Tier.FLOOR] 下限(E07-12)を割った目標を、下限まで戻す
 * 2. [Tier.RAMP_UP] 積み増し中の目標(E07-11)に今月の月額。期日を過ぎて届いていなければ不足の全額
 * 3. [Tier.SHORTFALL] 残りを、各目標の不足分(目標額 − 今の値 − ここまでで回した額)まで
 * 4. 余り([freeYen])。NISAの上乗せなど、自由に使える
 *
 * 目標ごとに割合を決める方式・一覧の順だけの方式もあったが、段を固定すれば新しい設定が要らず、
 * 下限割れや積み増しの時期を見落とさない。順番を変えたければ一覧で並べ替える。
 *
 * ## 数えない目標
 * - 毎年の枠(E07-09)。系列は使った額で、お金を貯める先ではない
 * - 目標額か今の値が分からない目標。足りない額が出せないので[unknown]に並べて知らせる
 */
data class SurplusPlan(
    val surplusYen: Long,
    /** 回す先。段の順、段の中は一覧の順。同じ目標が複数の段に出ることがある。 */
    val lines: List<Line>,
    /** どの目標にも回らなかった分。 */
    val freeYen: Long,
    /** 足りない額が分からず、案に入れられなかった目標。 */
    val unknown: List<Item.Goal>,
) {
    enum class Tier { FLOOR, RAMP_UP, SHORTFALL }

    data class Line(val tier: Tier, val goal: Item.Goal, val amountYen: Long)

    /** 目標ごとの合計。 */
    fun totalFor(goalId: String): Long = lines.filter { it.goal.id == goalId }.sumOf { it.amountYen }

    companion object {
        /** @param goals 一覧に出ている目標(一覧の順)。分け合っている目標は割当額が今の値 */
        fun of(surplusYen: Long, goals: List<ItemOverview.Goal>, today: LocalDate): SurplusPlan {
            val counted = goals.filterNot { it.item.resetsYearly }
            val (known, unknown) = counted.partition { it.targetYen != null && it.currentYen != null }

            var rest = surplusYen.coerceAtLeast(0)
            val given = HashMap<String, Long>()
            val lines = mutableListOf<Line>()

            /** 目標の不足のうち、まだ回していない分。 */
            fun remaining(goal: ItemOverview.Goal): Long =
                (goal.targetYen!! - goal.currentYen!! - (given[goal.item.id] ?: 0)).coerceAtLeast(0)

            fun give(tier: Tier, goal: ItemOverview.Goal, wanted: Long) {
                val amount = minOf(rest, wanted.coerceAtMost(remaining(goal)).coerceAtLeast(0))
                if (amount <= 0) return
                rest -= amount
                given[goal.item.id] = (given[goal.item.id] ?: 0) + amount
                lines += Line(tier, goal.item, amount)
            }

            // 1. 下限を割った分(目標額より下限が低いので、不足分の中に収まる)
            known.forEach { goal ->
                val floor = goal.floorYen ?: return@forEach
                give(Tier.FLOOR, goal, floor - goal.currentYen!!)
            }
            // 2. 積み増し中は今月の月額、期日を過ぎていれば不足の全額
            known.forEach { goal ->
                when (val rampUp = RampUp.of(goal.item, goal.targetYen, goal.currentYen, today)) {
                    is RampUp.Active -> give(Tier.RAMP_UP, goal, rampUp.monthlyYen)
                    RampUp.Overdue -> give(Tier.RAMP_UP, goal, remaining(goal))
                    else -> Unit
                }
            }
            // 3. 残りの不足分を一覧の順に
            known.forEach { goal -> give(Tier.SHORTFALL, goal, remaining(goal)) }

            return SurplusPlan(surplusYen, lines, rest, unknown.map { it.item })
        }
    }
}
