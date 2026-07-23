package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BbpiChart
import com.example.ui.components.CandlestickChart
import com.example.ui.components.HeaderBar
import com.example.ui.components.HeroSection
import com.example.ui.components.StochRsiChart
import com.example.ui.components.WidgetInfoCard
import com.example.ui.theme.FrostedBg
import com.example.ui.theme.FrostedHeaderTop

@Composable
fun DashboardScreen(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            FrostedHeaderTop,
            FrostedBg,
            FrostedBg
        )
    )

    Scaffold(
        containerColor = FrostedBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header Bar
                HeaderBar(
                    symbol = uiState.response?.symbol ?: "SOL/USDT",
                    selectedTimeframe = uiState.selectedTimeframe,
                    availableTimeframes = uiState.availableTimeframes,
                    onTimeframeSelected = { viewModel.setTimeframe(it) },
                    bbpiValue = uiState.bbpiValue,
                    pollIntervalMs = uiState.pollIntervalMs,
                    onPollIntervalChanged = { viewModel.setPollInterval(it) },
                    isStale = uiState.isStale,
                    isLoading = uiState.isLoading,
                    onRefreshClicked = { viewModel.refreshNow() }
                )

                // Error Message if any
                uiState.errorMessage?.let { err ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252))
                    ) {
                        Text(
                            text = "Bağlantı Notu: $err (Önbellekteki veriler gösteriliyor)",
                            color = Color(0xFFFF8A80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Hero Section
                HeroSection(
                    lastPrice = uiState.lastPrice,
                    prevPrice = uiState.prevPrice,
                    sessionDeltaPct = uiState.sessionDeltaPct,
                    position = uiState.position
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Candlestick Chart
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    CandlestickChart(
                        candles = uiState.candles,
                        positionEntryPrice = uiState.position?.entryPrice,
                        currentPrice = uiState.lastPrice
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stoch RSI Chart
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StochRsiChart(
                        kSeries = uiState.currentEngine?.kSeries ?: emptyList(),
                        dSeries = uiState.currentEngine?.dSeries ?: emptyList(),
                        stochKVal = uiState.stochK,
                        stochDVal = uiState.stochD
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BBPI Chart
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BbpiChart(
                        bpiSeries = uiState.currentEngine?.bpiSeries ?: emptyList(),
                        bbpiValue = uiState.bbpiValue
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Widget Info Card
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    WidgetInfoCard(
                        onSyncWidgetClicked = { viewModel.forceWidgetSync() }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
