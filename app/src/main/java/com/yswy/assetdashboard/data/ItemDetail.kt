package com.yswy.assetdashboard.data

import java.time.LocalDate

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
        /** 複数の目標で分け合っていれば、配分の積み上げ(E07-13)。 */
        val stack: Allocation.Stack? = null,
        /** 想定利回りを決めた系列の、このまま積み立てたときの将来の評価額(E09-03)。 */
        val future: FutureValue? = null,
    ) : ItemDetail

    data class Goal(
        override val overview: ItemOverview.Goal,
        /** 今のペースならいつ届くか(E09-01)。予測しない目標ならnull。 */
        val forecast: GoalForecast? = null,
        /** 期日に向けた目標の足りない分を、生活防衛資金で補えるか(E07-12)。 */
        val cover: Drawdown.Cover? = null,
        /** 目標の色の番号(E07-13)。積み上げグラフと同じ色を使う。 */
        val colorIndex: Int? = null,
    ) : ItemDetail {
        /** 目標まであといくら。達成済みなら0。進捗不明ならnull。 */
        val remainingYen: Long?
            get() {
                val target = overview.targetYen ?: return null
                return overview.currentYen?.let { (target - it).coerceAtLeast(0) }
            }

        /** 届いていないとき、月々いくらで何か月で届くか(E07-07)。 */
        val recovery: RecoveryPlan?
            get() = RecoveryPlan.of(overview.targetYen, overview.currentYen)

        /** 期日に向けた積み増し(E07-11)。決めていなければnull。 */
        fun rampUp(today: LocalDate): RampUp? = RampUp.of(overview.item, overview.targetYen, overview.currentYen, today)
    }

    data class Reminder(override val overview: ItemOverview.Reminder) : ItemDetail

    companion object {
        /**
         * [series]はMetricならその系列の点。Goal・Reminderでは使わない。
         * [investMonthlyYen]は積立投資の月平均(E07-21)。想定利回りを決めたMetricの将来の評価額に使う。
         */
        fun of(
            overview: ItemOverview,
            series: List<MetricPointEntity>,
            all: List<ItemOverview> = emptyList(),
            today: LocalDate = LocalDate.now(),
            investMonthlyYen: Long? = null,
        ): ItemDetail {
            val goals = all.filterIsInstance<ItemOverview.Goal>()
            return build(overview, series, goals, today, investMonthlyYen)
        }

        /** 目標の色の番号。一覧に出ている目標の中で上から何番目か(0始まり)。 */
        fun colorIndexOf(goals: List<ItemOverview.Goal>, id: String): Int? =
            goals.indexOfFirst { it.item.id == id }.takeIf { it >= 0 }

        private fun build(
            overview: ItemOverview,
            series: List<MetricPointEntity>,
            goals: List<ItemOverview.Goal>,
            today: LocalDate,
            investMonthlyYen: Long?,
        ): ItemDetail = when (overview) {
            is ItemOverview.Metric -> Metric(
                overview = overview,
                monthly = Summary.monthlyMetric(overview.item.metricKey, series).reversed(),
                series = series.sortedBy { it.date },
                pointCount = series.size,
                correctedCount = series.count { it.origin != MetricOrigin.CSV },
                stack = Allocation.stack(
                    series,
                    goals.filter { it.share?.metricKey == overview.item.metricKey },
                ) { colorIndexOf(goals, it) },
                future = FutureValue.of(overview.latest?.valueYen, overview.item.expectedReturnBp, investMonthlyYen),
            )
            is ItemOverview.Goal -> Goal(
                overview,
                // 分け合っているなら、系列が上の目標の分と自分の目標額の合計に届いたときが達成(E07-10)
                GoalForecast.of(overview.item, overview.targetYen?.let { it + (overview.share?.aheadYen ?: 0) }, series),
                Drawdown.cover(overview, goals, today),
                colorIndexOf(goals, overview.item.id),
            )
            is ItemOverview.Reminder -> Reminder(overview)
        }

        /** @param all 一覧の全項目。生活防衛資金を探すのに使う(E07-12) */
        suspend fun load(db: AppDatabase, overview: ItemOverview, all: List<ItemOverview> = emptyList()): ItemDetail {
            // Metricは推移の表とグラフに、Goalは達成の予測(E09-01)に、系列の点を使う
            val key = when (val item = overview.item) {
                is Item.Metric -> item.metricKey
                is Item.Goal -> item.metricKey
                is Item.Reminder -> null
            }
            val series = key?.let { db.metricPointDao().series(it) }.orEmpty()
            // 想定利回りを決めた系列だけ、明細から積立投資の月平均を出す(E09-03)
            val today = LocalDate.now()
            val invest = (overview.item as? Item.Metric)?.expectedReturnBp?.let {
                InvestPlan.currentMonthlyYen(Summary.monthlyCashflow(db.bankTransactionDao().all()), today)
            }
            return of(overview, series, all, today, invest)
        }
    }
}
