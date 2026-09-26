package com.yswy.assetdashboard.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 割合のドーナツ(E10-03)。[slices]は(割合, 色)で、割合の合計は1。
 *
 * 数字は描かない。割合と額は凡例に出す(見せ方の切り替え(E06-04)を凡例だけで済ませるため)。
 * 塗りつぶしの円ではなく輪にしたのは、隣り合う色の境目が見やすいから。
 */
@Composable
fun DonutChart(slices: List<Pair<Double, Color>>, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.22f
        val diameter = this.size.minDimension - stroke
        val topLeft = Offset((this.size.width - diameter) / 2, (this.size.height - diameter) / 2)
        // 12時の位置から時計回りに
        var start = -90f
        slices.forEach { (ratio, color) ->
            val sweep = (ratio * 360).toFloat()
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}
