package com.yswy.assetdashboard.data

import java.time.LocalDate

/**
 * ファンドごとの取得額と評価額(E01-17)。証券口座の保有商品一覧(E01-15)の明細から取る。
 *
 * ## 資産推移の点として持つ
 * ファンドごと・値ごとに1つの系列にして、ほかの系列と同じ `metric_point` に入れる。名前は
 * `#保有|区分|ファンド名|評価額` の形(`#` で始まる名前はCSVの列名に無いので取り違えない。純資産の合算(E10-01)と同じ)。
 * DBの作りを変えずに済み、Driveの backup にもそのまま残る(失っても作り直せる)。
 * `#` で始まる系列には一覧の項目を作らない(ファンドごとにトップの行が増えないように。本人は区分ごとの行を選んだ(E01-15))。
 *
 * ## 取得額 = 評価額 − 損益
 * 投資信託の数量は口数で、取得単価・現在値は1万口あたりのことが多い。数量×単価で出すと単位を取り違えるので、
 * CSVの損益の列から出す。取得単価・現在値はCSVの値をそのまま持つ(平均取得単価と今の単価の比べに使う)。
 */
data class FundHoldings(val funds: List<Fund>) {

    /** いま持っているファンド(いちばん新しい取り込みにあるもの)。 */
    val held: List<Fund> get() = funds.filter { it.soldOutBy == null }

    /** 売り切ったファンド(E01-19)。 */
    val soldOut: List<Fund> get() = funds.filter { it.soldOutBy != null }

    /**
     * 1本のファンド。[history]は取り込んだ日ごと、新しい日が先頭。
     * [soldOutBy]は、このファンドが載っていなかった最初の取り込みの日(売り切った。E01-19)。持っていればnull。
     */
    data class Fund(
        val section: String,
        val name: String,
        val history: List<Snapshot>,
        val soldOutBy: LocalDate? = null,
    ) {
        val latest: Snapshot get() = history.first()

        /** 売却(E01-19)。取り込みと取り込みのあいだで口数が減ったところ。新しい順。 */
        val sales: List<Sale> get() {
            val asc = history.reversed()
            val partial = asc.zipWithNext().mapNotNull { (before, after) ->
                val u0 = before.units ?: return@mapNotNull null
                val u1 = after.units ?: return@mapNotNull null
                val share = 1 - u1 / u0
                if (share < SALE_MIN_SHARE) null else Sale(before.date, after.date, share, after.price)
            }
            val full = soldOutBy?.let { listOf(Sale(latest.date, it, 1.0, null)) }.orEmpty()
            return (partial + full).sortedByDescending { it.by }
        }
    }

    /**
     * 売却(E01-19)。売った日は分からないので、[after]より後・[by]までのあいだ。
     * [share]は減った口数の割合(売り切りは1)。あいだに積み立てた分と相殺した残り(売った口数そのものではない)。
     * [price]は[by]の取り込みの現在値(一部売却のとき。売り切ったときは一覧に無いのでnull)。
     */
    data class Sale(val after: LocalDate, val by: LocalDate, val share: Double, val price: Long?)

    data class Snapshot(
        val date: LocalDate,
        val valueYen: Long,
        /** 取得額。損益の列が無ければnull。 */
        val costYen: Long?,
        /** 平均取得単価(CSVの値のまま)。 */
        val unitCost: Long?,
        /** 現在値(CSVの値のまま)。 */
        val price: Long?,
    ) {
        /** 含み益(評価額 − 取得額)。 */
        val gainYen: Long? get() = costYen?.let { valueYen - it }

        /** 含み益の割合(取得額に対する)。 */
        val gainRatio: Double? get() = costYen?.takeIf { it > 0 }?.let { (valueYen - it).toDouble() / it }

        /**
         * 口数の目安(評価額 ÷ 現在値)。単位(1万口あたりなど)はそろえていないので、同じファンドの比べにだけ使う(E01-19)。
         * 口数の列は系列に持っていない(持つとDriveのbackupを作り直すことになる)ので、評価額と現在値から出す。
         */
        val units: Double? get() = price?.takeIf { it > 0 }?.let { valueYen.toDouble() / it }
    }

    companion object {
        const val PREFIX = "#保有|"
        const val VALUE = "評価額"
        const val COST = "取得額"
        const val UNIT_COST = "取得単価"
        const val PRICE = "現在値"

        /**
         * 口数がこれ以上減ったら売却とみなす(E01-19)。評価額は円に丸めてあるので、口数の目安には小さな揺れがある。
         * 積立では口数は増えるだけなので、揺れより大きく減ったら売った。
         */
        const val SALE_MIN_SHARE = 0.005

        fun key(section: String, name: String, field: String) = "$PREFIX$section|$name|$field"

        /** 名前を(区分, ファンド名, 値の種類)に分ける。形が違えばnull。 */
        fun parseKey(key: String): Triple<String, String, String>? {
            if (!key.startsWith(PREFIX)) return null
            val parts = key.removePrefix(PREFIX).split("|")
            if (parts.size < 3) return null
            // ファンド名に「|」が入っていても、最初と最後で区分と値の種類を取る
            return Triple(parts.first(), parts.subList(1, parts.size - 1).joinToString("|"), parts.last())
        }

        /**
         * `#保有|` で始まる点からファンドごとにまとめる。評価額の新しい順。
         * 保有商品一覧は持っているファンドを全部載せるので、ほかのファンドが載っている取り込みにいないファンドは売り切った(E01-19)。
         */
        fun of(points: List<MetricPointEntity>): FundHoldings {
            val byFund = points.mapNotNull { p -> parseKey(p.metricKey)?.let { it to p } }
                .groupBy({ (k, _) -> k.first to k.second }, { (k, p) -> Triple(k.third, p.date, p.valueYen) })
            val funds = byFund.mapNotNull { (id, values) ->
                val byDate = values.groupBy { it.second }
                val history = byDate.mapNotNull { (date, list) ->
                    fun get(field: String) = list.firstOrNull { it.first == field }?.third
                    val value = get(VALUE) ?: return@mapNotNull null
                    Snapshot(date, value, get(COST), get(UNIT_COST), get(PRICE))
                }.sortedByDescending { it.date }
                if (history.isEmpty()) null else Fund(id.first, id.second, history)
            }
            // 取り込んだ日(どれかのファンドが載っている日)
            val importDates = funds.flatMap { f -> f.history.map { it.date } }.toSortedSet()
            val withSold = funds.map { f -> f.copy(soldOutBy = importDates.firstOrNull { it > f.latest.date }) }
            return FundHoldings(withSold.sortedByDescending { it.latest.valueYen })
        }
    }
}
