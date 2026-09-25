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

        val entries = mutableListOf<Entry>()
        for (item in items) {
            when (item) {
                is Item.Reminder -> {
                    val amount = item.amountYen ?: continue
                    when (item.repeat) {
                        Repeat.MONTHLY -> Unit
                        Repeat.NONE -> if (inRange(item.dueDate)) entries += Entry(item.id, item.dueDate, item.name, amount, Source.REMINDER)
                        Repeat.YEARLY -> generateSequence(item.dueDate) { it.plusYears(1) }
                            .takeWhile { !it.isAfter(end) }
                            .filter(::inRange)
                            .forEach { entries += Entry(item.id, it, item.name, amount, Source.REMINDER) }
                    }
                }
                is Item.Goal -> {
                    val due = item.dueDate ?: continue
                    val amount = item.targetYen ?: continue // 生活費から出す目標は出費の予定ではない
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
