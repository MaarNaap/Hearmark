package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ListeningActivityLineChart(
    points: List<ActivityChartDataPoint>,
    statsFilter: String,
    totalPeriodActualMs: Long,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    val maxVal = remember(points) {
        maxOf(points.maxOfOrNull { it.actualDurationMs } ?: 0L, 60_000L)
    }
    val hasActivity = remember(points) {
        points.any { it.actualDurationMs > 0L }
    }

    var selectedIndex by remember(points) {
        mutableStateOf<Int?>(if (points.isNotEmpty()) points.size - 1 else null)
    }
    val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (statsFilter == "today") Loc.getText("hourly_chart_title") else Loc.getText("activity_chart_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = primaryColor
                    )
                }

                if (hasActivity) {
                    val peakMs = points.maxOf { it.actualDurationMs }
                    Box(
                        modifier = Modifier
                            .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${Loc.getText("peak_activity")}: ${formatStatsDuration(peakMs)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }
            }

            if (activePoint != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activePoint.fullTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = onSurfaceColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activePoint.playCount > 0) {
                                Text(
                                    text = "${activePoint.playCount} ${Loc.getText("total_listens")}",
                                    fontSize = 10.sp,
                                    color = onSurfaceVariantColor
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatStatsDuration(activePoint.actualDurationMs),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (activePoint.actualDurationMs > 0L) primaryColor else onSurfaceVariantColor
                            )
                            if (activePoint.contentDurationMs != activePoint.actualDurationMs && activePoint.contentDurationMs > 0L) {
                                Text(
                                    text = "(${formatStatsDuration(activePoint.contentDurationMs)})",
                                    fontSize = 10.sp,
                                    color = onSurfaceVariantColor
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val n = points.size
                            if (n > 1) {
                                val paddingStartPx = 16.dp.toPx()
                                val paddingEndPx = 16.dp.toPx()
                                val chartWidth = size.width - paddingStartPx - paddingEndPx
                                val relX = (offset.x - paddingStartPx).coerceIn(0f, chartWidth)
                                val fraction = relX / chartWidth
                                val idx = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                selectedIndex = idx
                            } else if (n == 1) {
                                selectedIndex = 0
                            }
                        }
                    }
                    .pointerInput(points) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val n = points.size
                            if (n > 1) {
                                val paddingStartPx = 16.dp.toPx()
                                val paddingEndPx = 16.dp.toPx()
                                val chartWidth = size.width - paddingStartPx - paddingEndPx
                                val relX = (change.position.x - paddingStartPx).coerceIn(0f, chartWidth)
                                val fraction = relX / chartWidth
                                val idx = (fraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                selectedIndex = idx
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padStart = 16.dp.toPx()
                    val padEnd = 16.dp.toPx()
                    val padTop = 14.dp.toPx()
                    val padBottom = 16.dp.toPx()
                    val chartW = w - padStart - padEnd
                    val chartH = h - padTop - padBottom

                    val gridSteps = 3
                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (i in 0..gridSteps) {
                        val y = padTop + chartH * (i.toFloat() / gridSteps)
                        drawLine(
                            color = gridLineColor,
                            start = Offset(padStart, y),
                            end = Offset(w - padEnd, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashPathEffect
                        )
                    }

                    val n = points.size
                    if (n > 0) {
                        val coords = points.mapIndexed { idx, p ->
                            val x = if (n == 1) padStart + chartW / 2f
                            else padStart + idx * (chartW / (n - 1))
                            val ratio = (p.actualDurationMs.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                            val y = padTop + chartH * (1f - ratio)
                            Offset(x, y)
                        }

                        val strokePath = Path()
                        val fillPath = Path()
                        if (coords.isNotEmpty()) {
                            strokePath.moveTo(coords[0].x, coords[0].y)
                            fillPath.moveTo(coords[0].x, padTop + chartH)
                            fillPath.lineTo(coords[0].x, coords[0].y)

                            for (i in 0 until coords.size - 1) {
                                val p0 = coords[i]
                                val p1 = coords[i + 1]
                                val ctrl1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                                val ctrl2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                                strokePath.cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p1.x, p1.y)
                                fillPath.cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, p1.x, p1.y)
                            }

                            fillPath.lineTo(coords.last().x, padTop + chartH)
                            fillPath.close()

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = if (hasActivity) 0.35f else 0.08f),
                                        primaryColor.copy(alpha = 0.01f)
                                    ),
                                    startY = padTop,
                                    endY = padTop + chartH
                                )
                            )

                            drawPath(
                                path = strokePath,
                                color = if (hasActivity) primaryColor else primaryColor.copy(alpha = 0.4f),
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            coords.forEachIndexed { i, pt ->
                                val ptVal = points[i].actualDurationMs
                                if (ptVal > 0L) {
                                    drawCircle(
                                        color = surfaceColor,
                                        radius = 4.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 2.5.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }

                            val selIdx = selectedIndex
                            if (selIdx != null && selIdx in coords.indices) {
                                val selPt = coords[selIdx]
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.5f),
                                    start = Offset(selPt.x, padTop),
                                    end = Offset(selPt.x, padTop + chartH),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.25f),
                                    radius = 8.dp.toPx(),
                                    center = selPt
                                )
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 5.dp.toPx(),
                                    center = selPt
                                )
                                drawCircle(
                                    color = primaryColor,
                                    radius = 3.5.dp.toPx(),
                                    center = selPt
                                )
                            }
                        }
                    }
                }
            }

            val visibleMilestones = points.filter { it.showAxisLabel }
            if (visibleMilestones.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleMilestones.forEach { m ->
                        Text(
                            text = m.axisLabel,
                            fontSize = 10.sp,
                            fontWeight = if (activePoint?.timestamp == m.timestamp) FontWeight.Bold else FontWeight.Medium,
                            color = if (activePoint?.timestamp == m.timestamp) primaryColor else onSurfaceVariantColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = Loc.getText("chart_drag_hint"),
                fontSize = 10.sp,
                color = onSurfaceVariantColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
