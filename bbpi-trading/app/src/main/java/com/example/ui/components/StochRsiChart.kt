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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardWhite
import com.example.ui.theme.IndicatorD
import com.example.ui.theme.IndicatorK
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextColdWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun StochRsiChart(
    kSeries: List<Double?>,
    dSeries: List<Double?>,
    stochKVal: Double?,
    stochDVal: Double?,
    visibleCount: Int = 46,
    modifier: Modifier = Modifier
) {
    val f = remember(kSeries, visibleCount) {
        if (kSeries.size > visibleCount) kSeries.takeLast(visibleCount) else kSeries
    }
    val s = remember(dSeries, visibleCount) {
        if (dSeries.size > visibleCount) dSeries.takeLast(visibleCount) else dSeries
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .background(GlassCardWhite)
            .padding(10.dp)
    ) {
        // Header / Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STOCH RSI",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // %K
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp, 3.dp)
                            .clip(CircleShape)
                            .background(IndicatorK)
                    )
                    Text(text = "%K", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = stochKVal?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        color = TextColdWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // %D
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp, 3.dp)
                            .clip(CircleShape)
                            .background(IndicatorD)
                    )
                    Text(text = "%D", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = stochDVal?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        color = TextColdWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Canvas Plot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val lo = -4.0
                val hi = 104.0

                fun getY(v: Double): Float {
                    return (h - ((v - lo) / (hi - lo)) * h).toFloat()
                }

                // Dotted reference level lines: 30, 50, 70
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

                if (f.isNotEmpty() && s.isNotEmpty()) {
                    val count = maxOf(f.size, s.size)
                    fun getX(i: Int): Float = i * (w / (maxOf(1, count - 1)))

                    // Draw filled region between %K and %D
                    val fillPath = Path()
                    var started = false
                    for (i in f.indices) {
                        val valK = f[i] ?: continue
                        val x = getX(i)
                        val y = getY(valK)
                        if (!started) {
                            fillPath.moveTo(x, y)
                            started = true
                        } else {
                            fillPath.lineTo(x, y)
                        }
                    }
                    for (i in s.indices.reversed()) {
                        val valD = s[i] ?: continue
                        val x = getX(i)
                        val y = getY(valD)
                        fillPath.lineTo(x, y)
                    }
                    if (started) {
                        fillPath.close()
                        drawPath(fillPath, color = Color(0x1EF5C518))
                    }

                    // Draw %D Line (White/Gray)
                    val pathD = Path()
                    var dStarted = false
                    for (i in s.indices) {
                        val valD = s[i] ?: continue
                        val x = getX(i)
                        val y = getY(valD)
                        if (!dStarted) {
                            pathD.moveTo(x, y)
                            dStarted = true
                        } else {
                            pathD.lineTo(x, y)
                        }
                    }
                    if (dStarted) {
                        drawPath(pathD, color = IndicatorD, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                    }

                    // Draw %K Line (Yellow)
                    val pathK = Path()
                    var kStarted = false
                    for (i in f.indices) {
                        val valK = f[i] ?: continue
                        val x = getX(i)
                        val y = getY(valK)
                        if (!kStarted) {
                            pathK.moveTo(x, y)
                            kStarted = true
                        } else {
                            pathK.lineTo(x, y)
                        }
                    }
                    if (kStarted) {
                        drawPath(pathK, color = IndicatorK, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                    }

                    // Last value dots
                    f.lastOrNull { it != null }?.let { lastK ->
                        drawCircle(color = IndicatorK, radius = 5f, center = Offset(w, getY(lastK)))
                    }
                    s.lastOrNull { it != null }?.let { lastD ->
                        drawCircle(color = IndicatorD, radius = 5f, center = Offset(w, getY(lastD)))
                    }
                }
            }
        }
    }
}
