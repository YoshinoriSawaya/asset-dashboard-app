package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 期日に向けた積み増し(E07-11)。「期日まではまだ余裕がある間は何もしなくてよく、
 * 決めた時期に入ったら月々いくら積めば間に合うかを出す」。
 *
 * ## 目標の種類は、別の列を持たずに今ある設定で分ける
 * | 種類 | 決まり方 |
 * |------|---------|
 * | 一定額を保つ(生活防衛資金) | 目標額を生活費から出す(`autoTarget`。E07-06) |
 * | 期日に向けて積み増す | 期日と積み増しの時期がある(`dueDate` + `rampUpMonths`) |
 * | 毎年の枠 | `resetsYearly`(E07-09) |
 *
 * 種類の列を足すと、その列とこれらの設定が食い違う状態が作れてしまう。
 */
sealed interface RampUp {

    /** 積み増しを始めるまで。何もしなくてよい。 */
    data class Waiting(val startMonth: YearMonth) : RampUp

    /** 積み増しの期間中。期日まで月々いくら要るか。 */
    data class Active(val monthsLeft: Long, val monthlyYen: Long) : RampUp

    /** 期日を過ぎた(届いていない)。取り崩しはE07-12。 */
    data object Overdue : RampUp

    /** もう届いている。 */
    data object Achieved : RampUp

    companion object {
        /** 積み増しを決めていない、目標額・今の値が分からなければnull。 */
        fun of(goal: Item.Goal, targetYen: Long?, currentYen: Long?, today: LocalDate): RampUp? {
            val due = goal.dueDate ?: return null
            val months = goal.rampUpMonths ?: return null
            if (targetYen == null || currentYen == null) return null
            if (currentYen >= targetYen) return Achieved
            if (today.isAfter(due)) return Overdue

            val start = startMonth(due, months)
            if (YearMonth.from(today) < start) return Waiting(start)

            // 月数の数え方は「期日に間に合わせるには」(E09-01)とそろえる。
            // 期日の月に入ってしまったら、残りを今月で
            val monthsLeft = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(due))
            val monthly = GoalForecast.monthlyNeededForDue(targetYen, currentYen, due, today) ?: (targetYen - currentYen)
            return Active(monthsLeft.coerceAtLeast(1), monthly)
        }

        /** 積み増しを始める月。期日の月から[months]か月前。 */
        fun startMonth(due: LocalDate, months: Int): YearMonth = YearMonth.from(due).minusMonths(months.toLong())
    }
}
