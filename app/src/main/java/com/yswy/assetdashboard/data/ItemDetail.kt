package com.yswy.assetdashboard.data

/**
 * 詳細画面(E03-02)に出すもの。一覧の1行([ItemOverview])に、
 * 種類ごとの詳しい値を足したもの。
 */
sealed interface ItemDetail {
    val overview: ItemOverview

    data class Metric(
        override val overview: ItemOverview.Metric,
        /** 月ごとの推移(E02-05)。新しい月が先頭。 */
        val monthly: List<MetricChange>,
        /** 系列の全点(日付順)。推移グラフ(E03-06)に使う。 */
        val series: List<MetricPointEntity> = emptyList(),
        val pointCount: Int,
        /** 手で直した・足した点の数(E01-10)。 */
        val correctedCount: Int,
    ) : ItemDetail

    data class Goal(override val overview: ItemOverview.Goal) : ItemDetail {
        /** 目標まであといくら。達成済みなら0。進捗不明ならnull。 */
        val remainingYen: Long?
            get() {
                val target = overview.targetYen ?: return null
                return overview.currentYen?.let { (target - it).coerceAtLeast(0) }
            }

        /** 届いていないとき、月々いくらで何か月で届くか(E07-07)。 */
        val recovery: RecoveryPlan?
            get() = RecoveryPlan.of(overview.targetYen, overview.currentYen)
    }

    data class Reminder(override val overview: ItemOverview.Reminder) : ItemDetail

    companion object {
        /** [series]はMetricならその系列の点。Goal・Reminderでは使わない。 */
        fun of(overview: ItemOverview, series: List<MetricPointEntity>): ItemDetail = when (overview) {
            is ItemOverview.Metric -> Metric(
                overview = overview,
                monthly = Summary.monthlyMetric(overview.item.metricKey, series).reversed(),
                series = series.sortedBy { it.date },
                pointCount = series.size,
                correctedCount = series.count { it.origin != MetricOrigin.CSV },
            )
            is ItemOverview.Goal -> Goal(overview)
            is ItemOverview.Reminder -> Reminder(overview)
        }

        suspend fun load(db: AppDatabase, overview: ItemOverview): ItemDetail {
            val series = (overview.item as? Item.Metric)
                ?.let { db.metricPointDao().series(it.metricKey) }
                .orEmpty()
            return of(overview, series)
        }
    }
}
