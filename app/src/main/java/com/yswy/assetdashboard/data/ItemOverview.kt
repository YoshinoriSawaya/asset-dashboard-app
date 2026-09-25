package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 一覧(E03-01)の1行ぶん。項目と、その項目を一目で見るための値。
 *
 * 値は項目に持たせず、表示のたびに[MetricPointEntity]から計算する(E02-01)。
 */
sealed interface ItemOverview {
    val item: Item

    data class Metric(
        override val item: Item.Metric,
        /** 最新の点。まだデータが無ければnull。 */
        val latest: MetricPointEntity?,
        /** 今月の増減(E02-05)。今月のデータが無い、比べる相手が無いならnull。 */
        val monthChangeYen: Long?,
    ) : ItemOverview

    data class Goal(
        override val item: Item.Goal,
        /** 紐づけた系列の最新値。系列が未設定かデータが無ければnull。 */
        val currentYen: Long?,
        /** 支出から自動で出したときの計算結果(E07-06)。決まった額の目標ならnull。 */
        val auto: AutoTargets.Result? = null,
    ) : ItemOverview {
        /** 実際に使う目標額。自動の目標で、計算に使える月が無ければnull。 */
        val targetYen: Long?
            get() = if (item.autoTarget != null) auto?.targetYen else item.targetYen

        /** 進捗(1.0で達成)。目標額を超えても頭打ちにしない。 */
        val progress: Double?
            get() {
                val target = targetYen?.takeIf { it > 0 } ?: return null
                return currentYen?.let { it.toDouble() / target }
            }
    }

    data class Reminder(
        override val item: Item.Reminder,
        /** 期日まであと何日か。過ぎていれば負。 */
        val daysLeft: Long,
    ) : ItemOverview

    companion object {
        /**
         * 項目と、その項目が参照する系列の点から1行を作る。
         * @param pointsByKey metricKeyごとの点(順序は問わない)
         */
        fun of(
            item: Item,
            pointsByKey: Map<String, List<MetricPointEntity>>,
            today: LocalDate,
            /** 月ごとの入出金。支出から目標額を出すGoal(E07-06)で使う。 */
            monthlyCashflow: List<Cashflow> = emptyList(),
        ): ItemOverview =
            when (item) {
                is Item.Metric -> {
                    val points = pointsByKey[item.metricKey].orEmpty()
                    Metric(
                        item = item,
                        latest = points.maxByOrNull { it.date },
                        monthChangeYen = Summary.metricChange(
                            item.metricKey, points, Summary.month(YearMonth.from(today)),
                        ).changeYen,
                    )
                }
                is Item.Goal -> Goal(
                    item = item,
                    currentYen = item.metricKey?.let { key ->
                        val points = pointsByKey[key].orEmpty()
                        if (item.resetsYearly) {
                            // 毎年リセットする枠(E07-09): 今年の最新の累計。今年まだ無ければ0
                            points.filter { it.date.year == today.year }.maxByOrNull { it.date }?.valueYen ?: 0L
                        } else {
                            points.maxByOrNull { it.date }?.valueYen
                        }
                    },
                    auto = item.autoTarget?.let { AutoTargets.compute(it, monthlyCashflow, today) },
                )
                is Item.Reminder -> Reminder(item, ChronoUnit.DAYS.between(today, item.dueDate))
            }

        /** 隠していない項目を、並び順どおりに1行ずつにする。読めない行は飛ばす(E02-02)。 */
        suspend fun load(db: AppDatabase, today: LocalDate = LocalDate.now()): List<ItemOverview> {
            val items = db.itemDao().getAll().mapNotNull { it.toItem() }.filterNot { it.hidden }
            val keys = items.mapNotNull {
                when (it) {
                    is Item.Metric -> it.metricKey
                    is Item.Goal -> it.metricKey
                    is Item.Reminder -> null
                }
            }.distinct()
            val pointsByKey = keys.associateWith { db.metricPointDao().series(it) }
            // 支出から目標額を出すGoalがあるときだけ明細を読む
            val monthly = if (items.any { it is Item.Goal && it.autoTarget != null }) {
                Summary.monthlyCashflow(db.bankTransactionDao().all())
            } else {
                emptyList()
            }
            return items.map { of(it, pointsByKey, today, monthly) }
        }
    }
}
