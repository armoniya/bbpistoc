package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_cache")
data class DashboardEntity(
    @PrimaryKey val id: Int = 1,
    val symbol: String,
    val updatedAt: String,
    val timeframe: String,
    val lastPrice: Double,
    val prevPrice: Double,
    val sessionDeltaPct: Double,
    val bbpiValue: Double?,
    val stochK: Double?,
    val stochD: Double?,
    val positionSide: String?,
    val positionPnlPct: Double?,
    val entryPrice: Double?,
    val breakeven: Double?,
    val rawJson: String,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)
