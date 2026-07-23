package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PositionInfo
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BearishWhite
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishYellow
import com.example.ui.theme.CaseYellow
import com.example.ui.theme.PanelBg
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextColdWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun HeroSection(
    lastPrice: Double?,
    prevPrice: Double?,
    sessionDeltaPct: Double,
    position: PositionInfo?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Big Price Readout & Delta Chip
        Column(
            modifier = Modifier.weight(1.3f)
        ) {
            val priceStr = lastPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
            val priceColor = when {
                lastPrice == null || prevPrice == null -> TextColdWhite
                lastPrice > prevPrice -> BullishYellow
                lastPrice < prevPrice -> BearishWhite
                else -> TextColdWhite
            }

            Text(
                text = priceStr,
                color = priceColor,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Delta Chip
            val isUp = sessionDeltaPct >= 0
            val chipBg = if (isUp) Color(0x20FFD24A) else Color(0x20ECE8DC)
            val chipTextColor = if (isUp) BullishGreen else BearishRed
            val chipText = String.format(
                Locale.US,
                "%s%.2f%%",
                if (isUp) "▲ +" else "▼ ",
                sessionDeltaPct
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = chipText,
                        color = chipTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "oturum",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Right Column: Position Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(PanelBg)
                .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POZİSYON",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                val side = position?.side ?: if (position != null) "LONG" else null
                val tagColor = when (side?.uppercase(Locale.US)) {
                    "LONG" -> BullishYellow
                    "SHORT" -> BearishWhite
                    else -> TextMuted
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, tagColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = side?.uppercase(Locale.US) ?: "YOK",
                        color = tagColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val pnlPct = position?.upnlPct
            val pnlStr = if (pnlPct != null) {
                String.format(Locale.US, "%s%.2f%%", if (pnlPct >= 0) "+" else "", pnlPct)
            } else "—"
            val pnlColor = when {
                pnlPct == null -> TextMuted
                pnlPct >= 0 -> BullishYellow
                else -> BearishWhite
            }

            Text(
                text = pnlStr,
                color = pnlColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(2.dp))

            val subText = if (position != null) {
                val entry = position.entryPrice?.let { String.format(Locale.US, "giriş %.2f", it) } ?: ""
                val be = position.breakeven?.let { String.format(Locale.US, "be %.2f", it) } ?: ""
                listOf(entry, be).filter { it.isNotEmpty() }.joinToString(" · ")
            } else {
                "işlem yok"
            }

            Text(
                text = subText,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
