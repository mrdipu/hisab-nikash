package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CategoryBreakdown
import com.example.domain.DailyPoint
import com.example.domain.MoneyFormatter
import com.example.domain.PeriodBarPoint
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandExpenseRed
import com.example.ui.theme.BrandIncomeGreen
import kotlin.math.max

@Composable
fun IncomeExpenseBarChart(
    points: List<PeriodBarPoint>,
    isBangla: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val maxVal = max(
        points.maxOfOrNull { max(it.incomePoisha, it.expensePoisha) } ?: 1L,
        1L
    )
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val h = size.height
            val w = size.width
            // Horizontal reference lines
            for (step in 1..3) {
                val y = h * (step / 4f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            val groupWidth = w / points.size
            val barWidth = (groupWidth * 0.28f).coerceAtMost(24.dp.toPx())
            val gap = (barWidth * 0.25f)

            points.forEachIndexed { idx, p ->
                val centerX = groupWidth * idx + groupWidth / 2f
                val incHeight = ((p.incomePoisha.toFloat() / maxVal.toFloat()) * (h * 0.9f)).coerceAtLeast(if (p.incomePoisha > 0) 4.dp.toPx() else 0f)
                val expHeight = ((p.expensePoisha.toFloat() / maxVal.toFloat()) * (h * 0.9f)).coerceAtLeast(if (p.expensePoisha > 0) 4.dp.toPx() else 0f)

                if (incHeight > 0f) {
                    drawRoundRect(
                        color = BrandIncomeGreen,
                        topLeft = Offset(centerX - barWidth - gap / 2f, h - incHeight),
                        size = Size(barWidth, incHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                if (expHeight > 0f) {
                    drawRoundRect(
                        color = BrandExpenseRed,
                        topLeft = Offset(centerX + gap / 2f, h - expHeight),
                        size = Size(barWidth, expHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            points.forEach { p ->
                Text(
                    text = MoneyFormatter.localizeDigits(p.label, isBangla),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    items: List<CategoryBreakdown>,
    isBangla: Boolean,
    hidden: Boolean,
    modifier: Modifier = Modifier
) {
    val total = items.sumOf { it.totalPoisha }
    if (total <= 0L) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(124.dp)) {
                val stroke = 20.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var startAngle = -90f
                items.forEach { item ->
                    val sweep = (item.totalPoisha.toFloat() / total.toFloat()) * 360f
                    drawArc(
                        color = Color(item.category.colorHex),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = MoneyFormatter.formatBdt(total, isBangla, hidden = hidden),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.take(6).forEach { item ->
                val name = if (isBangla) item.category.nameBn else item.category.nameEn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(item.category.colorHex))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = MoneyFormatter.localizeDigits("${item.sharePercent.toInt()}%", isBangla),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DailySpendingTrendChart(
    points: List<DailyPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val maxVal = max(points.maxOfOrNull { it.expensePoisha } ?: 1L, 1L)
    val lineColor = BrandBlue
    val fillColor = BrandBlue.copy(alpha = 0.14f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(vertical = 8.dp)
    ) {
        val w = size.width
        val h = size.height
        if (points.size == 1) {
            drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(w / 2f, h / 2f))
            return@Canvas
        }
        val stepX = w / (points.size - 1)
        val strokePath = Path()
        val fillPath = Path()

        points.forEachIndexed { index, pt ->
            val x = index * stepX
            val y = h - ((pt.expensePoisha.toFloat() / maxVal.toFloat()) * (h * 0.85f)) - 4.dp.toPx()
            if (index == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == points.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }
        drawPath(fillPath, color = fillColor)
        drawPath(strokePath, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
