package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 今のペースなら目標にいつ届くか(E09-01)。
 *
 * ## ペースは「約半年前の点から最新の点まで」の増え方
 * 資産推移の点は等間隔ではない(直近1か月に点が詰まっている)。月ごとの増減の
 * 平均を取ると、点の多い月に引っ張られる。約半年前と最新の2点の差を、
 * その間の日数で割った「1日あたりの増え方」をペースにする。
 *
 * 投資信託のように値動きのある系列では、半年の増え方には積立だけでなく
 * 値上がり・値下がりも入る。あくまで「このまま行けば」の目安。
 */
sealed interface GoalForecast {

    /** もう届いている。 */
    data object Achieved : GoalForecast

    /** 点が足りない(期間が短すぎてペースを出せない)。 */
    data object NotEnoughData : GoalForecast

    /** 横ばいか減っている。このままでは届かない。 */
    data class NotReaching(val monthlyPaceYen: Long) : GoalForecast

    data class Reaching(
        /** 届く見込みの月。 */
        val month: YearMonth,
        val monthlyPaceYen: Long,
        /** 期日があるとき、間に合うか。期日が無ければnull。 */
        val onTime: Boolean?,
    ) : GoalForecast

    companion object {
        /** ペースを見る期間。 */
        const val LOOKBACK_DAYS = 182L

        /** これより短い期間しか無ければ、ペースを出さない。 */
        const val MIN_SPAN_DAYS = 60L

        private const val DAYS_PER_MONTH = 365.2425 / 12

        /**
         * @param points 目標の系列の点
         * @return 目標額・系列が無い、毎年の枠(E07-09)ならnull(予測しない)
         */
        fun of(goal: Item.Goal, targetYen: Long?, points: List<MetricPointEntity>): GoalForecast? {
            if (goal.resetsYearly || targetYen == null || points.isEmpty()) return null
            val sorted = points.sortedBy { it.date }
            val latest = sorted.last()
            if (latest.valueYen >= targetYen) return Achieved

            // 約半年前の点。それより古い点が無ければ、いちばん古い点
            val from = sorted.lastOrNull { !it.date.isAfter(latest.date.minusDays(LOOKBACK_DAYS)) } ?: sorted.first()
            val days = ChronoUnit.DAYS.between(from.date, latest.date)
            if (days < MIN_SPAN_DAYS) return NotEnoughData

            val perDay = (latest.valueYen - from.valueYen).toDouble() / days
            val monthly = Math.round(perDay * DAYS_PER_MONTH)
            if (perDay <= 0) return NotReaching(monthly)

            val daysToGo = Math.ceil((targetYen - latest.valueYen) / perDay).toLong()
            val eta = latest.date.plusDays(daysToGo)
            return Reaching(YearMonth.from(eta), monthly, goal.dueDate?.let { !eta.isAfter(it) })
        }

        /** 期日に間に合わせるには月々いくら要るか。期日が過ぎていればnull。 */
        fun monthlyNeededForDue(targetYen: Long, currentYen: Long, due: LocalDate, today: LocalDate): Long? {
            val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(due))
            if (months <= 0) return null
            val rest = (targetYen - currentYen).coerceAtLeast(0)
            return (rest + months - 1) / months
        }
    }
}
