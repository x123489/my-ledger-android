package com.example.myledger

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 分类占比饼图 */
@Composable
fun PieChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier.size(180.dp)) {
    Canvas(modifier) {
        val total = data.sumOf { it.second }
        if (total <= 0.0) {
            drawCircle(Color(0xFFE5E7EB), radius = size.minDimension / 2, center = center)
            return@Canvas
        }
        var start = -90f
        val d = size.minDimension
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        data.forEachIndexed { i, (_, v) ->
            val sweep = (v / total * 360).toFloat()
            drawArc(
                color = Color(Util.CHART_COLORS[i % Util.CHART_COLORS.size]),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = Size(d, d)
            )
            start += sweep
        }
    }
}

/** 通用柱状图：每组一根柱子，可正可负；负值时可选画绿色（冲销） */
@Composable
fun BarsCanvas(bars: List<Pair<Double, Color>>, modifier: Modifier) {
    Canvas(modifier) {
        val maxAbs = (bars.maxOfOrNull { abs(it.first) } ?: 1.0).coerceAtLeast(1.0)
        val n = bars.size.coerceAtLeast(1)
        val gap = size.width / n
        val barW = (gap * 0.6f).coerceAtLeast(2f)
        val baseY = size.height * 0.92f
        val scale = (size.height * 0.8f) / maxAbs.toFloat()
        bars.forEachIndexed { i, (v, color) ->
            val h = abs(v).toFloat() * scale
            val top = if (v >= 0) baseY - h else baseY
            val bottom = if (v >= 0) baseY else baseY + h
            drawRect(
                color,
                topLeft = Offset(gap * i + (gap - barW) / 2, top),
                size = Size(barW, bottom - top)
            )
        }
        drawLine(
            Color(0xFF9CA3AF),
            Offset(0f, baseY),
            Offset(size.width, baseY),
            strokeWidth = 1f
        )
    }
}

/** 月度收支趋势图（红=支出，绿=收入） */
@Composable
fun MonthTrendChart(months: List<String>, income: List<Double>, expense: List<Double>) {
    val bars = mutableListOf<Pair<Double, Color>>()
    // 并排：每个月两根柱子（支出、收入）
    val m = months.size.coerceAtLeast(1)
    val gap = 1f / m
    // 用单个 BarsCanvas 不方便并排，这里直接自绘
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        drawTrend(this, months, income, expense)
    }
}

private fun drawTrend(
    scope: DrawScope,
    months: List<String>,
    income: List<Double>,
    expense: List<Double>
) {
    with(scope) {
        val n = months.size.coerceAtLeast(1)
        val maxAbs = (income.maxOfOrNull { it } ?: 0.0).coerceAtLeast(expense.maxOfOrNull { it } ?: 0.0)
            .coerceAtLeast(1.0)
        val gap = size.width / n
        val baseY = size.height * 0.92f
        val scale = (size.height * 0.8f) / maxAbs.toFloat()
        months.forEachIndexed { i, _ ->
            val ex = expense.getOrElse(i) { 0.0 }
            val inC = income.getOrElse(i) { 0.0 }
            val bw = (gap * 0.32f).coerceAtLeast(2f)
            // 支出（红）
            val exH = abs(ex).toFloat() * scale
            drawRect(
                Color(0xFFEF4444),
                topLeft = Offset(gap * i + gap * 0.12f, baseY - exH),
                size = Size(bw, exH)
            )
            // 收入（绿）
            val inH = abs(inC).toFloat() * scale
            drawRect(
                Color(0xFF22C55E),
                topLeft = Offset(gap * i + gap * 0.12f + bw + gap * 0.06f, baseY - inH),
                size = Size(bw, inH)
            )
        }
        drawLine(Color(0xFF9CA3AF), Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1f)
    }
}

/** 分类月度对比图（净支出，负=冲销画绿） */
@Composable
fun CategoryCompareChart(months: List<String>, values: List<Double>) {
    val bars = values.map {
        it to if (it < 0) Color(0xFF22C55E) else Color(0xFFEF4444)
    }
    BarsCanvas(bars, Modifier.fillMaxWidth().height(160.dp))
}
