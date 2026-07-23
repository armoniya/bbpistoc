package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentBluePill
import com.example.ui.theme.AccentBluePillText
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WidgetInfoCard(
    onSyncWidgetClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .background(GlassCardBg)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = AccentIndigo,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Android Ana Ekran Widget'ı",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Bu uygulamadaki canlı BIST/Crypto verilerini, Stoch RSI ve BBPI indikatörlerini Android ana ekranınızda bir Widget olarak görüntüleyebilirsiniz:",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "1. Ana ekranda boş bir alana basılı tutun.", color = TextMuted, fontSize = 11.sp)
            Text(text = "2. 'Widget'lar' menüsünden 'BBPI Trading' seçeneğini bulun.", color = TextMuted, fontSize = 11.sp)
            Text(text = "3. Widget'ı sürükleyip ana ekranınıza yerleştirin.", color = TextMuted, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSyncWidgetClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBluePill,
                contentColor = AccentBluePillText
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = AccentBluePillText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Widget Verilerini Şimdi Senkronize Et",
                fontSize = 12.sp,
                color = AccentBluePillText,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}
