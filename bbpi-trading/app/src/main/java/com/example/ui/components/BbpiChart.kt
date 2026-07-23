package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BearishWhite
import com.example.ui.theme.BullishYellow
import com.example.ui.theme.CaseYellow
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardWhite
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextColdWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun BbpiChart(
    bpiSeries: List<Double?>,
    bbpiValue: Double?,
    visibleCount: Int = 46,
    modifier: Modifier = Modifier
) {
    val arr = remember(bpiSeries, visibleCount) {
        if (bpiSeries.size > visibleCount) bpiSeries.takeLast(visibleCount) else bpiSeries
    }

    val lineColor = when {
        bbpiValue == null -> CaseYellow
        bbpiValue <= 35 -> BullishYellow
        bbpiValue >= 65 -> BearishWhite
        else -> CaseYellow
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .background(GlassCardWhite)
            .padding(10.dp)
    ) {
        // Legend Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BBPI (BULL/BEAR POWER INDEX)",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "değer", color = TextSecondary, fontSize = 11.sp)
                Text(
                    text = bbpiValue?.let { String.format(Locale.US, "%.0f", it) } ?: "—",
                    color = lineColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Plot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val lo = -4.0
                val hi = 104.0

                fun getY(v: Double): Float {
                    return (h - ((v - lo) / (hi - lo)) * h).toFloat()
                }

                // Grid reference levels: 30, 50, 70
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                listOf(30.0, 50.0, 70.0).forEach { ref ->
                    val y = getY(ref)
                    drawLine(
                        color = Color(0x33A29CB4),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                }

                if (arr.isNotEmpty()) {
                    fun getX(i: Int): Float = i * (w / (maxOf(1, arr.size - 1)))

                    val path = Path()
                    var started = false
                    for (i in arr.indices) {
                        val v = arr[i] ?: continue
                        val x = getX(i)
                        val y = getY(v)
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    if (started) {
                        drawPath(path, color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f))
                    }

                    bbpiValue?.let { lastV ->
                        drawCircle(color = lineColor, radius = 6f, center = Offset(w, getY(lastV)))
                    }
                }
            }
        }
    }
}
