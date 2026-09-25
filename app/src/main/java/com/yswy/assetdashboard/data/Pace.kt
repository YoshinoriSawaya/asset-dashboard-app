package com.yswy.assetdashboard.data

import java.time.temporal.ChronoUnit

/**
 * 系列の増え方(E09)。目標の達成予測(E09-01)と、生活防衛資金の見通し(E09-02)で使う。
 *
 * ## 「約半年前の点から最新の点まで」
 * 資産推移の点は等間隔ではない(直近1か月に点が詰まっている)。月ごとの増減の
 * 平均を取ると、点の多い月に引っ張られる。約半年前と最新の2点の差を、
 * その間の日数で割る。半年より前の点が無ければいちばん古い点から。
 *
 * 系列の値の増え方なので、投資信託なら値動きも入る。
 */
data class Pace(val perDayYen: Double) {

    val monthlyYen: Long get() = Math.round(perDayYen * DAYS_PER_MONTH)

    companion object {
        const val LOOKBACK_DAYS = 182L

        /** これより短い期間しか無ければ、ペースを出さない(短い期間のぶれで極端になる)。 */
        const val MIN_SPAN_DAYS = 60L

        const val DAYS_PER_MONTH = 365.2425 / 12

        fun of(points: List<MetricPointEntity>): Pace? {
            if (points.isEmpty()) return null
            val sorted = points.sortedBy { it.date }
            val latest = sorted.last()
            val from = sorted.lastOrNull { !it.date.isAfter(latest.date.minusDays(LOOKBACK_DAYS)) } ?: sorted.first()
            val days = ChronoUnit.DAYS.between(from.date, latest.date)
            if (days < MIN_SPAN_DAYS) return null
            return Pace((latest.valueYen - from.valueYen).toDouble() / days)
        }
    }
}
