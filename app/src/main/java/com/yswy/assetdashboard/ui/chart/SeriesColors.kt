package com.yswy.assetdashboard.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.yswy.assetdashboard.data.Item

/**
 * 系列(Metric)ごとの色(E03-08)。トップの一覧の色の丸・推移の線・純資産の円グラフで同じ色にする。
 *
 * ## 色の番号は系列の並び順で決める
 * 項目の並び(並び順・名前)で、Metric項目に上から番号を振る。**隠している系列も数える**ので、
 * 隠したり戻したりしても他の系列の色は変わらない。系列が増えると、それより後ろの色はずれる。
 * 色を人が選ぶ設定は持たない(本人が選んだ。settingsを増やさない)。
 *
 * 色そのものは目標の色([GoalColors])と同じ8色を使う。系列の色と目標の色が同じ画面に並ぶのは
 * トップの一覧だけで、目標の行には色の丸を出さないので取り違えない。
 */
object SeriesColors {

    /** metricKey → 色の番号。 */
    fun indexOf(metrics: List<Item.Metric>): Map<String, Int> =
        metrics.map { it.metricKey }.distinct().withIndex().associate { (i, key) -> key to i }

    /** 系列の色。知らない系列(純資産の合算など)ならnull。 */
    @Composable
    fun of(metricKey: String): Color? = LocalSeriesColors.current[metricKey]?.let { GoalColors.of(it) }
}

/** metricKey → 色の番号。AppRootで配る。 */
val LocalSeriesColors = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }
