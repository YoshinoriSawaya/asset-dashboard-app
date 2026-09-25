package com.yswy.assetdashboard.ui.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * グラフの縦軸の範囲と目盛り。描画から切り離して、テストできるようにしてある。
 *
 * 目盛りは「切りのいい値」(1・2・5 × 10のべき)の間隔で置く。
 * 資産額は数百万の桁で上下するので、データの最小〜最大ぴったりに
 * 目盛りを置くと `5,432,108` のような読みにくい値になる。
 */
data class ValueAxis(val min: Long, val max: Long, val ticks: List<Long>) {

    /** 値を 0.0(下端)〜1.0(上端)に写す。 */
    fun fraction(value: Long): Float =
        if (max == min) 0.5f else ((value - min).toDouble() / (max - min)).toFloat()

    companion object {
        /**
         * @param includeZero 増減の棒グラフのように、0を基準線として必ず入れたいとき
         */
        fun of(values: List<Long>, includeZero: Boolean = false, tickCount: Int = 4): ValueAxis {
            val all = if (includeZero) values + 0L else values
            if (all.isEmpty()) return ValueAxis(0, 1, listOf(0, 1))
            val lo = all.min()
            val hi = all.max()
            if (lo == hi) {
                // 全部同じ値。上下に少し余白を取って真ん中に線を引く
                val pad = maxOf(abs(lo) / 10, 1L)
                return of(listOf(lo - pad, hi + pad), includeZero, tickCount)
            }

            val step = niceStep((hi - lo).toDouble() / tickCount)
            val min = (floor(lo / step) * step).toLong()
            val max = (ceil(hi / step) * step).toLong()
            val ticks = generateSequence(min) { it + step.toLong() }.takeWhile { it <= max }.toList()
            return ValueAxis(min, max, ticks)
        }

        /** 1・2・5 × 10のべき のうち、[rough]以上で最小のもの。 */
        fun niceStep(rough: Double): Double {
            if (rough <= 0) return 1.0
            val magnitude = 10.0.pow(floor(log10(rough)))
            val residual = rough / magnitude
            val nice = when {
                residual <= 1 -> 1.0
                residual <= 2 -> 2.0
                residual <= 5 -> 5.0
                else -> 10.0
            }
            return maxOf(nice * magnitude, 1.0)
        }
    }
}
