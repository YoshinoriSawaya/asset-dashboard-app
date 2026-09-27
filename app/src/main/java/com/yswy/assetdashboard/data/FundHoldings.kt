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

    /** 1本のファンド。[history]は取り込んだ日ごと、新しい日が先頭。 */
    data class Fund(val section: String, val name: String, val history: List<Snapshot>) {
        val latest: Snapshot get() = history.first()
    }

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
    }

    companion object {
        const val PREFIX = "#保有|"
        const val VALUE = "評価額"
        const val COST = "取得額"
        const val UNIT_COST = "取得単価"
        const val PRICE = "現在値"

        fun key(section: String, name: String, field: String) = "$PREFIX$section|$name|$field"

        /** 名前を(区分, ファンド名, 値の種類)に分ける。形が違えばnull。 */
        fun parseKey(key: String): Triple<String, String, String>? {
            if (!key.startsWith(PREFIX)) return null
            val parts = key.removePrefix(PREFIX).split("|")
            if (parts.size < 3) return null
            // ファンド名に「|」が入っていても、最初と最後で区分と値の種類を取る
            return Triple(parts.first(), parts.subList(1, parts.size - 1).joinToString("|"), parts.last())
        }

        /** `#保有|` で始まる点からファンドごとにまとめる。評価額の新しい順。 */
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
            }.sortedByDescending { it.latest.valueYen }
            return FundHoldings(funds)
        }
    }
}
