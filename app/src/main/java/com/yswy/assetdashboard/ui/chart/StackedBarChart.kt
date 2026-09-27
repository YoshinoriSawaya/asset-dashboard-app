package com.yswy.assetdashboard.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.ui.Formatters

/**
 * 積み上げの棒グラフ(E07-27)。棒1本が1つの期間で、中を色ごとに積み上げる。
 * [selected]の棒は枠で囲む。棒を押すと[onSelect]にその番号を渡す。
 *
 * 値がマイナス(カードの返品が多い月など)の部分は描かない(積み上げが崩れるので0として扱う)。
 */
@Composable
fun StackedBarChart(
    labels: List<String>,
    /** 棒ごとに、[colors]の順の値。 */
    stacks: List<List<Long>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
    /** 目盛りの文字。nullなら出さない(プライバシーモード。E06-04) */
    axisLabel: (Long) -> String? = { Formatters.yenCompact(it) },
) {
    val chart = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = chart.label)
    val totals = stacks.map { bar -> bar.sumOf { it.coerceAtLeast(0) } }
    val axis = ValueAxis.of(totals, includeZero = true)
    // 押した位置から棒を決めるため、描いたときの描画領域を覚えておく(状態にはしない。描くたびに変わらない)
    val plotHolder = remember { arrayOfNulls<Plot>(1) }

    Canvas(
        modifier = modifier.fillMaxWidth().height(180.dp).pointerInput(stacks.size) {
            detectTapGestures { pos ->
                val plot = plotHolder[0] ?: return@detectTapGestures
                if (stacks.isEmpty() || pos.x < plot.left || pos.x > plot.right) return@detectTapGestures
                val i = ((pos.x - plot.left) / (plot.width / stacks.size)).toInt().coerceIn(0, stacks.lastIndex)
                onSelect(i)
            }
        },
    ) {
        if (stacks.isEmpty()) return@Canvas
        val plot = drawValueAxis(axis, measurer, labelStyle, chart, axisLabel)
        plotHolder[0] = plot
        val zeroY = plot.bottom - plot.height * axis.fraction(0)
        drawLine(chart.baseline, Offset(plot.left, zeroY), Offset(plot.right, zeroY), 1.dp.toPx())

        val slot = plot.width / stacks.size
        val labelEvery = (stacks.size + 5) / 6
        stacks.forEachIndexed { i, bar ->
            val left = plot.left + slot * i + slot * 0.2f
            val width = slot * 0.6f
            var base = 0L
            bar.forEachIndexed { c, value ->
                val v = value.coerceAtLeast(0)
                if (v == 0L) return@forEachIndexed
                val bottomY = plot.bottom - plot.height * axis.fraction(base)
                val topY = plot.bottom - plot.height * axis.fraction(base + v)
                drawRect(colors[c % colors.size], topLeft = Offset(left, topY), size = Size(width, bottomY - topY))
                base += v
            }
            if (i == selected) {
                val topY = plot.bottom - plot.height * axis.fraction(base)
                val pad = 2.dp.toPx()
                drawRect(
                    chart.label,
                    topLeft = Offset(left - pad, topY - pad),
                    size = Size(width + pad * 2, zeroY - topY + pad * 2),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            if (i % labelEvery == 0 || i == selected) {
                drawBottomLabel(measurer, labels[i], labelStyle, left + width / 2, plot, alignEnd = false, center = true)
            }
        }
    }
}
