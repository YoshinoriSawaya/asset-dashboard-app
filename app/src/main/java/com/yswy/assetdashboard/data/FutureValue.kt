package com.yswy.assetdashboard.data

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * このまま積み立てたときの将来の評価額(E09-03)。あくまで目安。
 *
 * ```
 * 評価額 = 今の評価額 × (1 + m)^n + 月の積立額 × ((1 + m)^n − 1) / m
 *   m = (1 + 年利回り)^(1/12) − 1(月あたりの利回り)、n = 月数
 * ```
 * - 月の積立額は、明細の「積立投資」の月平均(E07-21。本人が決めた)。分からなければ0で、
 *   今の評価額だけを運用した場合になる
 * - 利回りは月ごとに複利。年利回りから、1年で同じ増え方になる月の利回りを出す
 *   (年利回り÷12にすると、年あたりの増え方が想定より少し大きくなる)
 * - 積立は月末に入れる扱い(最初の月から満額の利回りを付けない。多めに出さない)
 * - 1万円単位で四捨五入(「約◯万円」の目安)
 */
data class FutureValue(
    /** 想定利回り。年率の1万分率(3% = 300)。 */
    val rateBp: Int,
    /** 積立投資の月平均。分からなければnull(今の評価額だけで計算した)。 */
    val monthlyYen: Long?,
    val rows: List<Row>,
) {
    data class Row(
        val years: Int,
        val valueYen: Long,
        /** 積み立てた元本(今の評価額 + 積立額の合計)。増えた分との比べに使う。 */
        val principalYen: Long,
    )

    companion object {
        /** 何年後を出すか(本人が決めた)。 */
        val YEARS = listOf(10, 20, 30)

        /**
         * @param currentYen 今の評価額(系列の最新の点)
         * @param monthlyYen 積立投資の月平均。分からなければnull
         * @return 利回りを決めていない・今の評価額が分からないならnull
         */
        fun of(currentYen: Long?, rateBp: Int?, monthlyYen: Long?, years: List<Int> = YEARS): FutureValue? {
            if (rateBp == null || currentYen == null) return null
            val monthly = monthlyYen ?: 0L
            val m = (1 + rateBp / 10_000.0).pow(1.0 / 12) - 1
            val rows = years.map { y ->
                val n = y * 12
                val growth = (1 + m).pow(n)
                val value = if (m == 0.0) {
                    currentYen.toDouble() + monthly * n
                } else {
                    currentYen * growth + monthly * (growth - 1) / m
                }
                Row(y, roundMan(value), currentYen + monthly * n)
            }
            return FutureValue(rateBp, monthlyYen, rows)
        }

        private fun roundMan(yen: Double): Long = (yen / 10_000).roundToLong() * 10_000
    }
}
