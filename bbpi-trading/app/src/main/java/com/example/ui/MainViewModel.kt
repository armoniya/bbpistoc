package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.TradingApiService
import com.example.data.db.AppDatabase
import com.example.data.model.Candle
import com.example.data.model.DashboardResponse
import com.example.data.model.PositionInfo
import com.example.data.model.TimeframeEngine
import com.example.data.repository.TradingRepository
import com.example.widget.TradingWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val selectedTimeframe: String = "15m",
    val availableTimeframes: List<String> = listOf("15m", "1h", "4h"),
    val pollIntervalMs: Long = 1500L,
    val response: DashboardResponse? = null,
    val currentEngine: TimeframeEngine? = null,
    val lastPrice: Double? = null,
    val prevPrice: Double? = null,
    val sessionDeltaPct: Double = 0.0,
    val position: PositionInfo? = null,
    val bbpiValue: Double? = null,
    val stochK: Double? = null,
    val stochD: Double? = null,
    val candles: List<Candle> = emptyList(),
    val errorMessage: String? = null,
    val lastFetchOk: Long = 0L,
    val isStale: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = TradingRepository(TradingApiService.create(), db.dashboardDao())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        // First try to load from Room cache
        viewModelScope.launch {
            val cache = repository.getCachedDashboardOnce()
            if (cache != null && cache.rawJson.isNotEmpty()) {
                val cachedResponse = repository.parseRawJson(cache.rawJson)
                if (cachedResponse != null) {
                    applyDashboardResponse(cachedResponse, cache.timeframe)
                }
            }
            startPolling()
        }
    }

    fun setTimeframe(tf: String) {
        if (_uiState.value.selectedTimeframe == tf) return
        _uiState.update { it.copy(selectedTimeframe = tf) }
        _uiState.value.response?.let {
            applyDashboardResponse(it, tf)
        } ?: refreshNow()
    }

    fun setPollInterval(ms: Long) {
        _uiState.update { it.copy(pollIntervalMs = ms) }
        startPolling()
    }

    fun refreshNow() {
        viewModelScope.launch {
            fetchData()
        }
    }

    fun forceWidgetSync() {
        viewModelScope.launch {
            fetchData()
            TradingWidgetProvider.forceUpdate(getApplication())
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        val interval = _uiState.value.pollIntervalMs
        if (interval <= 0) return

        pollJob = viewModelScope.launch {
            while (true) {
                fetchData()
                delay(interval)
            }
        }
    }

    private suspend fun fetchData() {
        _uiState.update { it.copy(isLoading = it.response == null) }
        val tf = _uiState.value.selectedTimeframe
        val result = repository.fetchLatestDashboard(tf)

        result.onSuccess { response ->
            val now = System.currentTimeMillis()
            _uiState.update { it.copy(isLoading = false, errorMessage = null, lastFetchOk = now) }
            applyDashboardResponse(response, tf)
            // Trigger widget update
            TradingWidgetProvider.forceUpdate(getApplication())
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = err.localizedMessage ?: "Sunucu bağlantı hatası"
                )
            }
        }
    }

    private fun applyDashboardResponse(response: DashboardResponse, timeframe: String) {
        val engine = response.engines[timeframe] ?: response.engines.values.firstOrNull()
        val candles = engine?.candles ?: emptyList()

        val newPrice = candles.lastOrNull()?.c
        val oldPrice = _uiState.value.lastPrice ?: newPrice

        val firstCandle = candles.firstOrNull()
        val refPrice = firstCandle?.o ?: newPrice ?: 0.0
        val deltaPct = if (newPrice != null && refPrice > 0) {
            ((newPrice - refPrice) / refPrice) * 100
        } else {
            0.0
        }

        val bbpiSeries = engine?.bpiSeries ?: emptyList()
        val lastBbpi = bbpiSeries.lastOrNull { it != null }

        val kSeries = engine?.kSeries ?: emptyList()
        val dSeries = engine?.dSeries ?: emptyList()
        val lastK = kSeries.lastOrNull { it != null }
        val lastD = dSeries.lastOrNull { it != null }

        _uiState.update { state ->
            state.copy(
                response = response,
                currentEngine = engine,
                lastPrice = newPrice,
                prevPrice = oldPrice,
                sessionDeltaPct = deltaPct,
                position = engine?.position,
                bbpiValue = lastBbpi,
                stochK = lastK,
                stochD = lastD,
                candles = candles,
                isStale = (System.currentTimeMillis() - state.lastFetchOk) > 120000
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
