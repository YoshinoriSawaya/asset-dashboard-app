package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 固定費・サブスクの一覧(E07-26)。毎月同じように出ていく支払いを明細から見つけ、月額と年額の見込みを出す。
 *
 * - 見る期間は、今月より前で明細のある直近[WINDOW_MONTHS]か月(積立投資の目安(E10-04)と同じ月の選び方)
 * - その期間のうち[minMonths]か月以上で出ている摘要を「毎月の支払い」とみなす
 * - 月に平均[MAX_PER_MONTH]件を超える摘要は外す(自販機・コンビニのような、よく買う店。固定費ではない)
 * - 振替・積立投資・カードの引き落としの行は外す(使ったお金ではない、または内訳がカードの明細にある)
 * - 額の数え方は[Summary.usedYen](積立投資の目安・カテゴリ別の支出と同じ)
 *
 * 年に1回の支払い(年会費など)は、この見つけ方では拾えない。
 */
data class FixedCosts(
    /** 見た月(古い順)。 */
    val months: List<YearMonth>,
    /** 月の平均の多い順。 */
    val items: List<Item>,
    /** 同じ期間の消費(生活費 + 遊び代 + カテゴリなし)の月平均。割合を出すのに使う。 */
    val consumptionYen: Long,
) {
    data class Item(
        val description: String,
        val category: String?,
        val kind: CategoryKind?,
        /** 月ごとの額(見た月の順。出ていない月は0)。 */
        val byMonth: List<Long>,
        val lastDate: LocalDate,
    ) {
        /** 出ていた月の数。 */
        val hitMonths: Int get() = byMonth.count { it != 0L }

        /** 出ていた月の平均。 */
        val monthlyYen: Long get() = byMonth.filter { it != 0L }.let { if (it.isEmpty()) 0 else it.sum() / it.size }

        /** 年額の見込み。 */
        val yearlyYen: Long get() = monthlyYen * 12

        /** 定額か。出ていた月の額の幅が平均の[FIXED_TOLERANCE]以内。 */
        val fixedAmount: Boolean
            get() {
                val hits = byMonth.filter { it != 0L }
                return hits.isNotEmpty() && (hits.max() - hits.min()) <= abs(monthlyYen) * FIXED_TOLERANCE
            }

        /** 前の月までは出ていたのに、いちばん新しい月に出ていない(解約済みかもしれない)。 */
        val missingLastMonth: Boolean get() = byMonth.lastOrNull() == 0L

        /**
         * 値上がり(E07-28)。前の月まで定額だった支払い(2か月以上出ていて、額の幅が5%以内)が、いちばん新しい月に
         * それまでの最大額より5%を超えて高くなったら、(前の額, 新しい額)。そうでなければnull。
         */
        val priceIncrease: Pair<Long, Long>?
            get() {
                val last = byMonth.lastOrNull()?.takeIf { it != 0L } ?: return null
                val before = byMonth.dropLast(1).filter { it != 0L }
                if (before.size < 2) return null
                val avg = before.sum() / before.size
                if (before.max() - before.min() > abs(avg) * FIXED_TOLERANCE) return null
                val old = before.max()
                return if (last > old + abs(old) * FIXED_TOLERANCE) old to last else null
            }
    }

    /** いちばん新しい月に値上がりした支払い(E07-28)。 */
    val increased: List<Item> get() = items.filter { it.priceIncrease != null }

    val monthlyTotalYen: Long get() = items.sumOf { it.monthlyYen }
    val yearlyTotalYen: Long get() = monthlyTotalYen * 12

    /** 固定費のうち消費に入るものが、消費の何割か。消費が無ければnull。 */
    val consumptionShare: Double?
        get() = if (consumptionYen > 0) items.filter { it.kind?.isConsumption ?: true }.sumOf { it.monthlyYen }.toDouble() / consumptionYen else null

    companion object {
        const val WINDOW_MONTHS = 6
        const val MAX_PER_MONTH = 2.0
        const val FIXED_TOLERANCE = 0.05

        /** 何か月出ていれば毎月の支払いとみなすか。見た月の2/3(切り上げ)、少なくとも3か月。 */
        fun minMonths(window: Int): Int = maxOf(3, (window * 2 + 2) / 3)

        /** @return 見る月が3か月に満たなければnull(毎月かどうかを決められない) */
        fun of(transactions: List<BankTransactionEntity>, today: LocalDate): FixedCosts? {
            val thisMonth = YearMonth.from(today)
            val months = transactions.map { YearMonth.from(it.date) }.distinct()
                .filter { it < thisMonth }.sorted().takeLast(WINDOW_MONTHS)
            if (months.size < 3) return null
            val inWindow = transactions.filter { YearMonth.from(it.date) in months }

            val consumption = inWindow.filter { it.categoryKind?.isConsumption ?: true }.sumOf { Summary.usedYen(it) } / months.size
            val need = minMonths(months.size)
            val items = inWindow
                .filter {
                    it.categoryKind != CategoryKind.TRANSFER && it.categoryKind != CategoryKind.INVESTMENT &&
                        Summary.usedYen(it) > 0
                }
                .groupBy { it.description }
                .mapNotNull { (description, rows) ->
                    val byMonth = months.map { m -> rows.filter { YearMonth.from(it.date) == m }.sumOf { Summary.usedYen(it) } }
                    val hit = byMonth.count { it != 0L }
                    if (hit < need || rows.size.toDouble() / hit > MAX_PER_MONTH) return@mapNotNull null
                    val last = rows.maxBy { it.date }
                    Item(description, last.category, last.categoryKind, byMonth, last.date)
                }
                .sortedByDescending { it.monthlyYen }
            return FixedCosts(months, items, consumption)
        }
    }
}
