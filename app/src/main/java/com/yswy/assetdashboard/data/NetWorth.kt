package com.yswy.assetdashboard.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * 純資産の推移(E10-01)。人が「純資産に数える」とした系列([Item.Metric.inNetWorth])を合算する。
 *
 * ## 全部の系列は足さない
 * 資産推移CSVの「合計」とその内訳、証券口座の区分と「投資信託」のように、系列どうしが
 * 重なる。列名で見分けて決め打ちするとCLAUDE.mdの「対応付けはしない」に反するので、
 * どれを数えるかは人が系列ごとに選ぶ(本人が選んだ)。
 *
 * ## 日付ごとに、各系列の「その日までの最新値」を足す
 * 系列ごとに点の日付が揃っていない(資産推移は毎日、保有商品一覧は取り込んだ日だけ)。
 * どれかの系列に点がある日を全部並べ、その日以前の最新値を足す。
 * 同じ日に点が無い系列を0とすると、日によって純資産が大きく欠ける。
 *
 * まだ始まっていない系列(最初の点より前)は足さない。そのぶん途中から段が付くので、
 * 後から始まった系列は[lateStarts]で画面に出す(黙って段を付けない)。
 */
data class NetWorth(
    /** 数えている系列。一覧の並び順。 */
    val metrics: List<Item.Metric>,
    /** 合算した点。日付順。metricKeyは[KEY]。 */
    val series: List<MetricPointEntity>,
    /** 合算の始まりより後から加わった系列と、その最初の日付。 */
    val lateStarts: List<Pair<Item.Metric, LocalDate>>,
    /** 今月の増減。比べる相手が無ければnull。 */
    val monthChangeYen: Long?,
) {
    val latest: MetricPointEntity? get() = series.lastOrNull()

    /** 月ごとの推移。新しい月が先頭(詳細画面の表と同じ向き)。 */
    val monthly: List<MetricChange> get() = Summary.monthlyMetric(KEY, series).reversed()

    companion object {
        /**
         * 合算した点のmetricKey。画面の中だけで使い、DBにもDriveにも書かない。
         * CSVの列名(`（円）`を落としたもの)に`#`で始まるものは無いので、系列と取り違えない。
         */
        const val KEY = "#純資産"

        /**
         * @param metrics Metric項目全部(隠しているものも)。数えるものだけ使う
         * @return 数える系列が1つも無ければnull
         */
        fun of(
            metrics: List<Item.Metric>,
            pointsByKey: Map<String, List<MetricPointEntity>>,
            today: LocalDate,
        ): NetWorth? {
            val counted = metrics.filter { it.inNetWorth }.distinctBy { it.metricKey }
            if (counted.isEmpty()) return null

            val byDate = counted
                .flatMap { pointsByKey[it.metricKey].orEmpty() }
                .groupBy { it.date }
                .toSortedMap()

            val latest = HashMap<String, Long>()
            val series = byDate.map { (date, points) ->
                points.forEach { latest[it.metricKey] = it.valueYen }
                MetricPointEntity(KEY, date, latest.values.sum(), MetricOrigin.CSV)
            }

            val start = series.firstOrNull()?.date
            val lateStarts = counted.mapNotNull { metric ->
                val first = pointsByKey[metric.metricKey].orEmpty().minOfOrNull { it.date } ?: return@mapNotNull null
                if (start != null && first > start) metric to first else null
            }

            return NetWorth(
                metrics = counted,
                series = series,
                lateStarts = lateStarts,
                monthChangeYen = Summary.metricChange(KEY, series, Summary.month(YearMonth.from(today))).changeYen,
            )
        }

        suspend fun load(db: AppDatabase, today: LocalDate = LocalDate.now()): NetWorth? {
            val metrics = db.itemDao().getAll().mapNotNull { it.toItem() as? Item.Metric }
            val keys = metrics.filter { it.inNetWorth }.map { it.metricKey }.distinct()
            return of(metrics, keys.associateWith { db.metricPointDao().series(it) }, today)
        }
    }
}
