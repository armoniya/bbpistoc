package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Candle
import com.example.ui.theme.BearishWhite
import com.example.ui.theme.BullishYellow
import com.example.ui.theme.CaseYellow
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardWhite
import com.example.ui.theme.GridLine
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextColdWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    positionEntryPrice: Double?,
    currentPrice: Double?,
    visibleCandlesCount: Int = 46,
    modifier: Modifier = Modifier
) {
    var selectedCandle by remember { mutableStateOf<Candle?>(null) }

    val viewCandles = remember(candles, visibleCandlesCount) {
        if (candles.size > visibleCandlesCount) candles.takeLast(visibleCandlesCount) else candles
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .background(GlassCardWhite)
            .padding(8.dp)
    ) {
        if (viewCandles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mum grafiği verisi bekleniyor...",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(viewCandles) {
                        detectTapGestures { offset ->
                            val n = viewCandles.size
                            if (n > 0) {
                                val cw = size.width / n
                                val index = (offset.x / cw).toInt().coerceIn(0, n - 1)
                                selectedCandle = viewCandles[index]
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val n = viewCandles.size
                val cw = w / n

                var lo = Double.MAX_VALUE
                var hi = Double.MIN_VALUE
                viewCandles.forEach {
                    lo = min(lo, it.l)
                    hi = max(hi, it.h)
                }

                positionEntryPrice?.let {
                    lo = min(lo, it)
                    hi = max(hi, it)
                }

                val pad = (hi - lo) * 0.12
                val minVal = lo - if (pad == 0.0) 1.0 else pad
                val maxVal = hi + if (pad == 0.0) 1.0 else pad

                fun getY(priceVal: Double): Float {
                    return (h - ((priceVal - minVal) / (maxVal - minVal)) * h).toFloat()
                }

                // Grid lines (4 divisions)
                val gridEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                for (g in 0..4) {
                    val y = h * g / 4f
                    drawLine(
                        color = GridLine,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                // Draw Entry Price Line if available
                positionEntryPrice?.let { entry ->
                    val ye = getY(entry)
                    drawLine(
                        color = CaseYellow.copy(alpha = 0.6f),
                        start = Offset(0f, ye),
                        end = Offset(w, ye),
                        strokeWidth = 2f,
                        pathEffect = gridEffect
                    )
                }

                // Draw Candles
                viewCandles.forEachIndexed { i, candle ->
                    val x = i * cw + cw / 2f
                    val isBullish = candle.c >= candle.o
                    val color = if (isBullish) BullishYellow else BearishWhite

                    // High - Low Wick
                    val yh = getY(candle.h)
                    val yl = getY(candle.l)
                    drawLine(
                        color = color,
                        start = Offset(x, yh),
                        end = Offset(x, yl),
                        strokeWidth = 2f
                    )

                    // Open - Close Body
                    val yo = getY(candle.o)
                    val yc = getY(candle.c)
                    val bw = max(2f, cw * 0.62f)
                    val topY = min(yo, yc)
                    val bodyHeight = max(2f, abs(yc - yo))

                    drawRect(
                        color = color,
                        topLeft = Offset(x - bw / 2f, topY),
                        size = Size(bw, bodyHeight)
                    )
                }

                // Draw Current Price Line
                currentPrice?.let { cp ->
                    val yp = getY(cp)
                    val pColor = if ((viewCandles.lastOrNull()?.c ?: cp) >= (viewCandles.firstOrNull()?.o ?: cp)) BullishYellow else BearishWhite
                    drawLine(
                        color = pColor.copy(alpha = 0.5f),
                        start = Offset(0f, yp),
                        end = Offset(w, yp),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }
            }

            // Top overlay showing details if tapped
            selectedCandle?.let { candle ->
                val timeStr = try {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(candle.t))
                } catch (e: Exception) { "" }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xE606040C))
                        .border(1.dp, PanelBorder, RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Saat: $timeStr", color = CaseYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Açılış: ${candle.o}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "Yüksek: ${candle.h}", color = BullishYellow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "Düşük: ${candle.l}", color = BearishWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "Kapanış: ${candle.c}", color = TextColdWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            } ?: run {
                Text(
                    text = "MUM GRAFİĞİ (15m/1h/4h)",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                )
            }
        }
    }
}
