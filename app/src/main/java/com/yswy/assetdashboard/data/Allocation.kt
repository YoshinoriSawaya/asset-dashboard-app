package com.yswy.assetdashboard.data

/**
 * 1つの系列(口座)を複数の目標で分け合う(E07-10。封筒方式)。
 *
 * ## 一覧の上から順に満たす
 * 同じ系列を測る目標が2つ以上あるとき、系列の値を一覧で上の目標から順に、
 * それぞれの目標額まで割り当てる。余りは「自由に使えるお金」。
 *
 * 配分額を本人が入れる方式・割合で分ける方式もあったが、上から満たすなら
 * 入れるものが無く、残高が変われば進捗も勝手に追従する(2026-09-26に本人が選んだ)。
 * 生活防衛資金を上に置けば「まず生活防衛資金、余った分で車」になる。
 *
 * ## 目標が1つだけの系列は分けない
 * 分けると進捗が100%で頭打ちになり、生活防衛資金の「何か月分あるか」(E09-02)も
 * 目標額で止まる。分け合う相手がいないなら、これまでどおり系列の値をそのまま使う。
 *
 * ## 分けない目標
 * - 毎年の枠(E07-09)。系列は使った額の累計で、口座の残高ではない
 * - 目標額が分からない目標(生活費から出す目標で、明細がまだ無い)。0を割り当て、
 *   下の目標の分を食わない
 */
object Allocation {

    /** 分け合っている目標の、配分の内訳。 */
    data class Share(
        val metricKey: String,
        /** 系列の今の値(分ける前)。 */
        val seriesYen: Long,
        /** 自分より上の目標に割り当てた分の目標額の合計。 */
        val aheadYen: Long,
        /** 上から何番目か(1始まり)。 */
        val position: Int,
        /** 分け合っている目標の数。 */
        val count: Int,
        /** どの目標にも割り当たらなかった分。 */
        val freeYen: Long,
    )

    /** 分け合う対象になる目標か。 */
    fun shares(goal: Item.Goal): Boolean = !goal.resetsYearly && goal.metricKey != null

    /**
     * [balance]を[targets]の順に満たす。目標額がnullなら0を割り当てる。
     * @return 目標ごとの割当額(同じ順)と、余り
     */
    fun fill(balance: Long, targets: List<Long?>): Pair<List<Long>, Long> {
        var rest = balance.coerceAtLeast(0)
        val allocated = targets.map { target ->
            val amount = minOf(rest, (target ?: 0).coerceAtLeast(0))
            rest -= amount
            amount
        }
        return allocated to rest
    }

    /**
     * 一覧の行に配分を反映する。分け合っている目標は[ItemOverview.Goal.currentYen]を
     * 割当額に置き換え、[ItemOverview.Goal.share]に内訳を入れる。順番は変えない。
     */
    fun apply(overviews: List<ItemOverview>): List<ItemOverview> {
        val groups = overviews.filterIsInstance<ItemOverview.Goal>()
            .filter { shares(it.item) && it.currentYen != null }
            .groupBy { it.item.metricKey!! }
            .filterValues { it.size >= 2 }
        if (groups.isEmpty()) return overviews

        val replaced = mutableMapOf<String, ItemOverview.Goal>()
        for ((key, goals) in groups) {
            val balance = goals.first().currentYen!!
            val targets = goals.map { it.targetYen }
            val (allocated, free) = fill(balance, targets)
            var ahead = 0L
            goals.forEachIndexed { i, goal ->
                replaced[goal.item.id] = goal.copy(
                    currentYen = allocated[i],
                    share = Share(key, balance, ahead, i + 1, goals.size, free),
                )
                ahead += (targets[i] ?: 0).coerceAtLeast(0)
            }
        }
        return overviews.map { replaced[it.item.id] ?: it }
    }

    /** 積み上げグラフ(E07-13)の1層。 */
    data class Layer(
        /** 目標のid。自由に使えるお金ならnull。 */
        val goalId: String?,
        val name: String,
        /** 色の番号([ItemDetail]の目標の色と同じ)。自由に使えるお金ならnull。 */
        val colorIndex: Int?,
        /** [Stack.dates]の日ごとの額。 */
        val values: List<Long>,
    )

    /** 系列の推移を、目標ごとの配分で積み上げたもの。下の層から順。 */
    data class Stack(val dates: List<java.time.LocalDate>, val layers: List<Layer>)

    /**
     * 系列の各点を、今の目標額で上から満たして積み上げる。
     *
     * 過去の点にも**今の**目標額を当てる(目標額の履歴は持っていない)。
     * 「今の配分の決まりで、これまでの残高を分けるとこうなる」という見え方。
     *
     * @param goals この系列を分け合っている目標(配分の順)
     * @param colorIndexOf 目標の色の番号
     */
    fun stack(series: List<MetricPointEntity>, goals: List<ItemOverview.Goal>, colorIndexOf: (String) -> Int?): Stack? {
        if (goals.size < 2 || series.isEmpty()) return null
        val sorted = series.sortedBy { it.date }
        val targets = goals.map { it.targetYen }
        val fills = sorted.map { fill(it.valueYen, targets) }
        val layers = goals.mapIndexed { i, goal ->
            Layer(goal.item.id, goal.item.name, colorIndexOf(goal.item.id), fills.map { it.first[i] })
        } + Layer(null, "自由に使えるお金", null, fills.map { it.second })
        return Stack(sorted.map { it.date }, layers)
    }
}
