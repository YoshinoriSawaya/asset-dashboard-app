package com.yswy.assetdashboard.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 大型出費の積立の計算(E07-15)。家電・車のように、何年かごとに来る出費に向けて貯める。
 *
 * 予定は見込み額つきのリマインダーで持つ(車検は2年ごと、洗濯機は10年ごと、のように)。
 * 積立の目標([Item.Goal.sinking])を積立先にしたリマインダーの回を先まで並べ、
 * - **目標額** = 向こう1年に来る回の見込み額の合計(今年のぶんが今の積立で足りるか)
 * - **月々の積立額** = 向こう[SinkingFund.horizonYears]年の回の合計 ÷ その月数(ならした額)
 * を出す。本人のシートの「年初に要る額」と「AVE◯Y/M」に当たる。
 *
 * ## 物価上昇は年単位で掛ける
 * 見込み額は今の値段で入れ、回の年が今年からN年先なら (1 + 率)^N を掛ける。月単位で
 * 細かく掛けても、見込み額そのものの粗さのほうがずっと大きい。
 *
 * ## 期日を過ぎた回も数える
 * 済みにしていないリマインダーの期日が過ぎていれば、まだ払っていない出費として
 * 向こう1年に入れる(黙って落とすより、多めに見えて気づけるほうがよい)。
 */
object SinkingPlan {

    /** 1回の出費。[amountYen]は物価上昇を掛けたあとの額。 */
    data class Occurrence(val reminder: Item.Reminder, val date: LocalDate, val amountYen: Long)

    data class Result(
        /** 向こう1年に来る回の合計。目標額になる。 */
        val nextYearYen: Long,
        /** 向こう[horizonYears]年をならした月々の積立額。 */
        val monthlyYen: Long,
        val horizonYears: Int,
        /** 向こう[horizonYears]年の回。日付順。 */
        val occurrences: List<Occurrence>,
    ) {
        /** 向こう1年の回。 */
        fun nextYear(today: LocalDate): List<Occurrence> = occurrences.filter { it.date.isBefore(today.plusYears(1)) }
    }

    /** 積立の目標でなければnull。 */
    fun of(goal: Item.Goal, reminders: List<Item.Reminder>, today: LocalDate): Result? {
        val fund = goal.sinking ?: return null
        val years = fund.horizonYears.coerceAtLeast(1)
        val end = today.plusYears(years.toLong())
        val occurrences = reminders
            .filter { it.fundId == goal.id }
            .flatMap { occurrences(it, end, today, fund.growthRateBp) }
            .sortedWith(compareBy({ it.date }, { it.reminder.name }))
        val nextYear = today.plusYears(1)
        return Result(
            nextYearYen = occurrences.filter { it.date.isBefore(nextYear) }.sumOf { it.amountYen },
            monthlyYen = ceil(occurrences.sumOf { it.amountYen }.toDouble() / (years * 12)).toLong(),
            horizonYears = years,
            occurrences = occurrences,
        )
    }

    /**
     * [reminder]の回を[end]の前まで並べる。見込み額が無ければ空。
     * 最初の回は期日そのもの(過ぎていても入れる)、あとは繰り返しの間隔で進める。
     */
    fun occurrences(reminder: Item.Reminder, end: LocalDate, today: LocalDate, growthRateBp: Int): List<Occurrence> {
        val amount = reminder.amountYen ?: return emptyList()
        val dates = when (reminder.repeat) {
            Repeat.NONE -> sequenceOf(reminder.dueDate)
            Repeat.MONTHLY -> generateSequence(reminder.dueDate) { it.plusMonths(1) }
            Repeat.YEARLY -> generateSequence(reminder.dueDate) { it.plusYears(reminder.repeatYears.coerceAtLeast(1).toLong()) }
        }
        return dates.takeWhile { it.isBefore(end) }
            .map { Occurrence(reminder, it, grown(amount, growthRateBp, it.year - today.year)) }
            .toList()
    }

    /** 今の値段[yen]に、[years]年ぶんの物価上昇を掛ける。今年より前・今年は掛けない。 */
    fun grown(yen: Long, growthRateBp: Int, years: Int): Long {
        if (growthRateBp == 0 || years <= 0) return yen
        return (yen * (1 + growthRateBp / 10_000.0).pow(years)).roundToLong()
    }
}
