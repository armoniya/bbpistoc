package com.example.data.repository

import com.example.data.api.TradingApiService
import com.example.data.db.DashboardDao
import com.example.data.db.DashboardEntity
import com.example.data.model.DashboardResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TradingRepository(
    private val apiService: TradingApiService = TradingApiService.create(),
    private val dashboardDao: DashboardDao
) {
    val cachedDashboard: Flow<DashboardEntity?> = dashboardDao.getCachedDashboard()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(DashboardResponse::class.java)

    suspend fun fetchLatestDashboard(selectedTimeframe: String = "15m"): Result<DashboardResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getDashboardData()
                
                // Process and cache to Room
                val engine = response.engines[selectedTimeframe] ?: response.engines.values.firstOrNull()
                val candles = engine?.candles ?: emptyList()
                val lastCandle = candles.lastOrNull()
                val firstCandle = candles.firstOrNull()
                
                val lastPrice = lastCandle?.c ?: 0.0
                val refPrice = firstCandle?.o ?: lastPrice
                val sessionDeltaPct = if (refPrice > 0) ((lastPrice - refPrice) / refPrice) * 100 else 0.0
                
                val bbpiSeries = engine?.bpiSeries ?: emptyList()
                val lastBbpi = bbpiSeries.lastOrNull { it != null }
                
                val kSeries = engine?.kSeries ?: emptyList()
                val dSeries = engine?.dSeries ?: emptyList()
                val lastK = kSeries.lastOrNull { it != null }
                val lastD = dSeries.lastOrNull { it != null }

                val position = engine?.position
                val rawJson = adapter.toJson(response)

                val entity = DashboardEntity(
                    id = 1,
                    symbol = response.symbol ?: "SOL/USDT",
                    updatedAt = response.updated ?: "Live",
                    timeframe = selectedTimeframe,
                    lastPrice = lastPrice,
                    prevPrice = if (candles.size > 1) candles[candles.size - 2].c else lastPrice,
                    sessionDeltaPct = sessionDeltaPct,
                    bbpiValue = lastBbpi,
                    stochK = lastK,
                    stochD = lastD,
                    positionSide = position?.side ?: if (position != null) "LONG" else null,
                    positionPnlPct = position?.upnlPct,
                    entryPrice = position?.entryPrice,
                    breakeven = position?.breakeven,
                    rawJson = rawJson,
                    cachedAtTimestamp = System.currentTimeMillis()
                )
                
                dashboardDao.saveDashboard(entity)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCachedDashboardOnce(): DashboardEntity? {
        return withContext(Dispatchers.IO) {
            dashboardDao.getCachedDashboardOnce()
        }
    }

    fun parseRawJson(rawJson: String): DashboardResponse? {
        return try {
            adapter.fromJson(rawJson)
        } catch (e: Exception) {
            null
        }
    }
}
