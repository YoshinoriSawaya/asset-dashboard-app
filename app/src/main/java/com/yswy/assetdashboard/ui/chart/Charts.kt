package com.yswy.assetdashboard.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.yswy.assetdashboard.ui.Formatters
import java.time.LocalDate

/**
 * グラフの配色ルール(E03-06)。
 *
 * 色は全部 MaterialTheme から取る。ライト/ダークはテーマ側で切り替わるので、
 * グラフ側で分岐しない。
 *
 * | 役割 | 色 |
 * |------|----|
 * | 系列の線・増えた棒 | primary |
 * | 減った棒 | error(増減の文字色と揃える) |
 * | 目盛りの線 | outlineVariant(データより目立たせない) |
 * | 0の基準線 | outline |
 * | 目盛りの文字 | onSurfaceVariant |
 *
 * 目標ごとの積み上げ(E07-13)の色は [GoalColors]。
 */
internal data class ChartColors(
    val line: Color,
    val negative: Color,
    val grid: Color,
    val baseline: Color,
    val label: Color,
)

@Composable
internal fun chartColors() = ChartColors(
    line = MaterialTheme.colorScheme.primary,
    negative = MaterialTheme.colorScheme.error,
    grid = MaterialTheme.colorScheme.outlineVariant,
    baseline = MaterialTheme.colorScheme.outline,
    label = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * 値の推移の折れ線。横軸は日付の実際の間隔で置く
 * (資産推移CSVの点は等間隔ではないので、点の順番で並べると形が歪む)。
 */
@Composable
fun LineChart(
    points: List<Pair<LocalDate, Long>>,
    modifier: Modifier = Modifier,
    /** 目盛りの文字。(値, 最初の点の値) → 文字。nullなら出さない(プライバシーモード。E06-04) */
    axisLabel: (Long, Long) -> String? = { v, _ -> Formatters.yenCompact(v) },
) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.label)
    val sorted = points.sortedBy { it.first }
    val axis = ValueAxis.of(sorted.map { it.second })

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (sorted.isEmpty()) return@Canvas
        val first = sorted.first().second
        val plot = drawValueAxis(axis, measurer, labelStyle, colors) { axisLabel(it, first) }

        val firstDay = sorted.first().first.toEpochDay()
        val span = (sorted.last().first.toEpochDay() - firstDay).coerceAtLeast(1)
        fun x(date: LocalDate) =
            if (sorted.size == 1) plot.center.x
            else plot.left + plot.width * (date.toEpochDay() - firstDay) / span
        fun y(value: Long) = plot.bottom - plot.height * axis.fraction(value)

        val path = Path()
        sorted.forEachIndexed { i, (date, value) ->
            if (i == 0) path.moveTo(x(date), y(value)) else path.lineTo(x(date), y(value))
        }
        drawPath(path, colors.line, style = Stroke(width = 2.dp.toPx()))
        sorted.forEach { (date, value) -> drawCircle(colors.line, 2.5.dp.toPx(), Offset(x(date), y(value))) }
        // 最新の点を目立たせる
        sorted.last().let { (date, value) -> drawCircle(colors.line, 4.5.dp.toPx(), Offset(x(date), y(value))) }

        // 横軸は両端の年月だけ。中間は月次の表で読める
        drawBottomLabel(measurer, sorted.first().first.yearMonthText(), labelStyle, plot.left, plot, alignEnd = false)
        drawBottomLabel(measurer, sorted.last().first.yearMonthText(), labelStyle, plot.right, plot, alignEnd = true)
    }
}

/**
 * 期間ごとの増減の棒。0を基準に、増えたら上、減ったら下。
 * 値がnull(データの無い期間)は棒を描かずに間を空ける。
 */
@Composable
fun ChangeBarChart(
    bars: List<Pair<String, Long?>>,
    modifier: Modifier = Modifier,
    /** 目盛りの文字。nullなら出さない(プライバシーモード。E06-04) */
    axisLabel: (Long) -> String? = { Formatters.yenCompact(it) },
) {
    val colors = chartColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.label)
    val axis = ValueAxis.of(bars.mapNotNull { it.second }, includeZero = true)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        if (bars.isEmpty()) return@Canvas
        val plot = drawValueAxis(axis, measurer, labelStyle, colors, axisLabel)
        val zeroY = plot.bottom - plot.height * axis.fraction(0)
        drawLine(colors.baseline, Offset(plot.left, zeroY), Offset(plot.right, zeroY), 1.dp.toPx())

        val slot = plot.width / bars.size
        // ラベルは多くても6つ程度に間引く
        val labelEvery = (bars.size + 5) / 6
        bars.forEachIndexed { i, (label, value) ->
            val left = plot.left + slot * i + slot * 0.2f
            if (value != null && value != 0L) {
                val top = plot.bottom - plot.height * axis.fraction(value)
                drawRect(
                    color = if (value < 0) colors.negative else colors.line,
                    topLeft = Offset(left, minOf(top, zeroY)),
                    size = Size(slot * 0.6f, kotlin.math.abs(zeroY - top)),
                )
            }
            if (i % labelEvery == 0) {
                drawBottomLabel(measurer, label, labelStyle, left + slot * 0.3f, plot, alignEnd = false, center = true)
            }
        }
    }
}

/** 描画する矩形。左に縦軸の文字、下に横軸の文字の余白を取った残り。 */
internal data class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val center get() = Offset((left + right) / 2, (top + bottom) / 2)
}

/** 縦軸の目盛り線と文字を描き、残りの描画領域を返す。 */
internal fun DrawScope.drawValueAxis(
    axis: ValueAxis,
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
    label: (Long) -> String?,
): Plot {
    // 目盛りの文字が出ないとき(マスク)は、線だけ引いて左の余白も詰める
    val labels = axis.ticks.map { tick -> label(tick)?.let { measurer.measure(it, style) } }
    val gutter = (labels.maxOfOrNull { it?.size?.width ?: 0 } ?: 0) + 6.dp.toPx()
    val bottomLabel = measurer.measure("0", style).size.height + 4.dp.toPx()
    // 右と上は、端の点(最新の点は半径4.5dp)が切れない分だけ空ける
    val plot = Plot(gutter, 6.dp.toPx(), size.width - 6.dp.toPx(), size.height - bottomLabel)

    axis.ticks.zip(labels).forEach { (tick, text) ->
        val y = plot.bottom - plot.height * axis.fraction(tick)
        drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
        text?.let { drawText(it, topLeft = Offset(gutter - 6.dp.toPx() - it.size.width, y - it.size.height / 2)) }
    }
    return plot
}

internal fun DrawScope.drawBottomLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    plot: Plot,
    alignEnd: Boolean,
    center: Boolean = false,
) {
    val layout = measurer.measure(text, style)
    val left = when {
        center -> x - layout.size.width / 2
        alignEnd -> x - layout.size.width
        else -> x
    }.coerceIn(0f, size.width - layout.size.width)
    drawText(layout, topLeft = Offset(left, plot.bottom + 2.dp.toPx()))
}

internal fun LocalDate.yearMonthText() = "%d-%02d".format(year, monthValue)
