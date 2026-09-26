package com.yswy.assetdashboard.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.data.Allocation

/**
 * 目標ごとの色(E07-13)。
 *
 * - 色の番号は、一覧に出ている目標の中で上から何番目か。積み上げグラフと
 *   目標の詳細で同じ番号を使うので、同じ目標は同じ色になる
 * - ライトとダークで同じ色相の明るさ違いを使う。テーマの色(動的カラー)から
 *   取ると目標どうしの色が近くなり見分けにくいので、ここだけ固定の色にする
 * - 「自由に使えるお金」はテーマの中立色(outline)。目的の決まった分と分ける
 * - 赤は避ける(減った・下限割れの error と紛れる)
 */
object GoalColors {
    private val light = listOf(
        Color(0xFF1E88E5), // 青
        Color(0xFF43A047), // 緑
        Color(0xFFFB8C00), // 橙
        Color(0xFF8E24AA), // 紫
        Color(0xFF00ACC1), // 水色
        Color(0xFF6D4C41), // 茶
        Color(0xFFC0CA33), // 黄緑
        Color(0xFF3949AB), // 藍
    )
    private val dark = listOf(
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFFFFB74D),
        Color(0xFFBA68C8),
        Color(0xFF4DD0E1),
        Color(0xFFA1887F),
        Color(0xFFDCE775),
        Color(0xFF7986CB),
    )

    /** 色の番号がnull(自由に使えるお金)なら中立色。8色を超えたら頭から使い回す。 */
    @Composable
    fun of(index: Int?): Color {
        if (index == null) return MaterialTheme.colorScheme.outline
        val palette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light
        return palette[index % palette.size]
    }
}

/** 凡例や目標の詳細に出す、色の丸。 */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/**
 * 系列の推移を、目標ごとの配分で積み上げて塗り分ける(E07-13)。
 * 一番上の縁が系列の値そのもの(ふつうの折れ線と同じ形)になる。
 */
@Composable
fun StackedChart(
    stack: Allocation.Stack,
    modifier: Modifier = Modifier,
    /** 目盛りの文字。(値, 最新の点の合計) → 文字。nullなら出さない(E06-04) */
    axisLabel: (Long, Long) -> String?,
) {
    val colors = chartColors()
    val layerColors = stack.layers.map { GoalColors.of(it.colorIndex) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.label)
    val totals = stack.dates.indices.map { i -> stack.layers.sumOf { it.values[i] } }
    val axis = ValueAxis.of(totals, includeZero = true)

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (stack.dates.isEmpty()) return@Canvas
        val latest = totals.last()
        val plot = drawValueAxis(axis, measurer, labelStyle, colors) { axisLabel(it, latest) }

        val firstDay = stack.dates.first().toEpochDay()
        val span = (stack.dates.last().toEpochDay() - firstDay).coerceAtLeast(1)
        fun x(i: Int) =
            if (stack.dates.size == 1) plot.center.x
            else plot.left + plot.width * (stack.dates[i].toEpochDay() - firstDay) / span
        fun y(value: Long) = plot.bottom - plot.height * axis.fraction(value)

        // 下の層から、下の縁 → 上の縁 を1つの形にして塗る
        val lower = LongArray(stack.dates.size)
        stack.layers.forEachIndexed { layerIndex, layer ->
            val upper = LongArray(stack.dates.size) { lower[it] + layer.values[it] }
            val path = Path()
            stack.dates.indices.forEach { i -> if (i == 0) path.moveTo(x(i), y(upper[i])) else path.lineTo(x(i), y(upper[i])) }
            stack.dates.indices.reversed().forEach { i -> path.lineTo(x(i), y(lower[i])) }
            path.close()
            drawPath(path, layerColors[layerIndex].copy(alpha = 0.85f))
            upper.copyInto(lower)
        }
        // 合計(系列の値)の縁を線で引いておく
        val top = Path()
        stack.dates.indices.forEach { i -> if (i == 0) top.moveTo(x(i), y(totals[i])) else top.lineTo(x(i), y(totals[i])) }
        drawPath(top, colors.baseline, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        drawCircle(colors.baseline, 3.dp.toPx(), Offset(x(stack.dates.lastIndex), y(totals.last())))

        drawBottomLabel(measurer, stack.dates.first().yearMonthText(), labelStyle, plot.left, plot, alignEnd = false)
        drawBottomLabel(measurer, stack.dates.last().yearMonthText(), labelStyle, plot.right, plot, alignEnd = true)
    }
}
