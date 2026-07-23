package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DashboardResponse(
    @Json(name = "symbol") val symbol: String? = "SOL/USDT",
    @Json(name = "updated") val updated: String? = null,
    @Json(name = "engines") val engines: Map<String, TimeframeEngine> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class TimeframeEngine(
    @Json(name = "balance") val balance: Double? = null,
    @Json(name = "position") val position: PositionInfo? = null,
    @Json(name = "candles") val candles: List<Candle> = emptyList(),
    @Json(name = "bpi_series") val bpiSeries: List<Double?> = emptyList(),
    @Json(name = "d_series") val dSeries: List<Double?> = emptyList(),
    @Json(name = "k_series") val kSeries: List<Double?> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Candle(
    @Json(name = "c") val c: Double,
    @Json(name = "h") val h: Double,
    @Json(name = "l") val l: Double,
    @Json(name = "o") val o: Double,
    @Json(name = "t") val t: Long
)

@JsonClass(generateAdapter = true)
data class PositionInfo(
    @Json(name = "entry_price") val entryPrice: Double? = null,
    @Json(name = "breakeven") val breakeven: Double? = null,
    @Json(name = "upnl_pct") val upnlPct: Double? = null,
    @Json(name = "side") val side: String? = "LONG"
)
