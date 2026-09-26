package com.yswy.assetdashboard.data

import java.time.LocalDate

/**
 * 向こう数年の大型出費(E09-04)。「いつ頃まとまったお金が要るか」を先に見る。
 *
 * ## 何を載せるか
 * - **見込み額を入れたリマインダー**(車検・保険の更新など)。毎年のものは
 *   期間内の各年に展開する。毎月のものは「単発で大きい出費」ではないので載せない
 * - **期日のある目標**(車の購入など)。目標額を、その期日の出費として載せる。
 *   毎年の枠(ふるさと納税など)は出費の予定ではないので載せない
 *
 * 見込み額の無いリマインダーは載せない(額の分からない予定は、ここでは役に立たない)。
 *
 * 何年ごとのリマインダー(E07-15)はその間隔で展開する。大型出費の積立に入れたリマインダーは、
 * 積立の物価上昇率を掛けた額で載せる(積立の目標額と同じ額に揃える)。
 */
object ExpenseCalendar {

    const val DEFAULT_YEARS = 3L

    data class Entry(
        /** 元の項目のid。タップで詳細を開く。 */
        val itemId: String,
        val date: LocalDate,
        val name: String,
        val amountYen: Long,
        val source: Source,
    )

    enum class Source { REMINDER, GOAL }

    fun build(items: List<Item>, today: LocalDate, years: Long = DEFAULT_YEARS): List<Entry> {
        val end = today.plusYears(years)
        fun inRange(d: LocalDate) = !d.isBefore(today) && !d.isAfter(end)

        // 積立先の目標の物価上昇率(E07-15)
        val growthByFund = items.filterIsInstance<Item.Goal>()
            .mapNotNull { goal -> goal.sinking?.let { goal.id to it.growthRateBp } }.toMap()

        val entries = mutableListOf<Entry>()
        for (item in items) {
            when (item) {
                is Item.Reminder -> {
                    val amount = item.amountYen ?: continue
                    val growth = item.fundId?.let { growthByFund[it] } ?: 0
                    fun entry(d: LocalDate) = Entry(item.id, d, item.name, SinkingPlan.grown(amount, growth, d.year - today.year), Source.REMINDER)
                    when (item.repeat) {
                        Repeat.MONTHLY -> Unit
                        Repeat.NONE -> if (inRange(item.dueDate)) entries += entry(item.dueDate)
                        Repeat.YEARLY -> generateSequence(item.dueDate) { it.plusYears(item.repeatYears.coerceAtLeast(1).toLong()) }
                            .takeWhile { !it.isAfter(end) }
                            .filter(::inRange)
                            .forEach { entries += entry(it) }
                    }
                }
                is Item.Goal -> {
                    val due = item.dueDate ?: continue
                    // 生活費から出す目標・大型出費の積立は出費の予定ではない(積立の中身はリマインダーとして載る)
                    val amount = item.targetYen ?: continue
                    if (!item.resetsYearly && inRange(due)) entries += Entry(item.id, due, item.name, amount, Source.GOAL)
                }
                is Item.Metric -> Unit
            }
        }
        return entries.sortedWith(compareBy({ it.date }, { it.name }))
    }

    /** 年ごとの合計。画面の見出しに出す。 */
    fun totalsByYear(entries: List<Entry>): Map<Int, Long> =
        entries.groupBy { it.date.year }.mapValues { (_, list) -> list.sumOf { it.amountYen } }.toSortedMap()
}
