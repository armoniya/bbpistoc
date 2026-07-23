package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.api.TradingApiService
import com.example.data.db.AppDatabase
import com.example.data.repository.TradingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TradingWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    updateAllWidgets(context, appWidgetManager, appWidgetIds)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = AppDatabase.getDatabase(context)
        val repository = TradingRepository(TradingApiService.create(), db.dashboardDao())

        // Fetch fresh data
        repository.fetchLatestDashboard("15m")
        val cache = db.dashboardDao().getCachedDashboardOnce()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_trading_dashboard)

            if (cache != null) {
                views.setTextViewText(R.id.tv_widget_symbol, cache.symbol)
                views.setTextViewText(R.id.tv_widget_tf, cache.timeframe)
                
                // Price formatting
                views.setTextViewText(R.id.tv_widget_price, String.format(Locale.US, "%.2f", cache.lastPrice))

                // Delta formatting
                val deltaPrefix = if (cache.sessionDeltaPct >= 0) "▲ +" else "▼ "
                views.setTextViewText(
                    R.id.tv_widget_delta,
                    String.format(Locale.US, "%s%.2f%%", deltaPrefix, cache.sessionDeltaPct)
                )
                if (cache.sessionDeltaPct >= 0) {
                    views.setInt(R.id.tv_widget_delta, "setBackgroundResource", R.drawable.bg_widget_chip_up)
                    views.setTextColor(R.id.tv_widget_delta, 0xFFFFD24A.toInt())
                } else {
                    views.setInt(R.id.tv_widget_delta, "setBackgroundResource", R.drawable.bg_widget_chip_dn)
                    views.setTextColor(R.id.tv_widget_delta, 0xFFECE8DC.toInt())
                }

                // BBPI Badge
                val bbpiVal = cache.bbpiValue
                if (bbpiVal != null) {
                    views.setTextViewText(R.id.tv_widget_bbpi, String.format(Locale.US, "BBPI %.0f", bbpiVal))
                    if (bbpiVal <= 35) {
                        views.setTextColor(R.id.tv_widget_bbpi, 0xFFFFD24A.toInt()) // Low / Bullish
                    } else if (bbpiVal >= 65) {
                        views.setTextColor(R.id.tv_widget_bbpi, 0xFFECE8DC.toInt()) // High / Bearish
                    } else {
                        views.setTextColor(R.id.tv_widget_bbpi, 0xFFF5C518.toInt()) // Neutral
                    }
                } else {
                    views.setTextViewText(R.id.tv_widget_bbpi, "BBPI —")
                }

                // Position
                val side = cache.positionSide
                val pnl = cache.positionPnlPct
                if (side != null && pnl != null) {
                    views.setTextViewText(R.id.tv_widget_pos_side, side.uppercase(Locale.US))
                    val pnlPrefix = if (pnl >= 0) "+" else ""
                    views.setTextViewText(R.id.tv_widget_pnl, String.format(Locale.US, "%s%.2f%%", pnlPrefix, pnl))
                    views.setTextColor(
                        R.id.tv_widget_pnl,
                        if (pnl >= 0) 0xFFFFD24A.toInt() else 0xFFECE8DC.toInt()
                    )

                    val entryStr = cache.entryPrice?.let { String.format(Locale.US, "giriş %.2f", it) } ?: ""
                    val beStr = cache.breakeven?.let { String.format(Locale.US, "be %.2f", it) } ?: ""
                    val subText = listOf(entryStr, beStr).filter { it.isNotEmpty() }.joinToString(" · ")
                    views.setTextViewText(R.id.tv_widget_pos_sub, if (subText.isNotEmpty()) subText else "işlem aktif")
                } else {
                    views.setTextViewText(R.id.tv_widget_pos_side, "YOK")
                    views.setTextViewText(R.id.tv_widget_pnl, "—")
                    views.setTextColor(R.id.tv_widget_pnl, 0xFF645C74.toInt())
                    views.setTextViewText(R.id.tv_widget_pos_sub, "işlem yok")
                }

                val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                views.setTextViewText(R.id.tv_widget_updated, "Son Güncelleme: " + timeFormatter.format(Date()))
            } else {
                views.setTextViewText(R.id.tv_widget_price, "—")
                views.setTextViewText(R.id.tv_widget_updated, "Yükleniyor...")
            }

            // Click root to open app
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

            // Click refresh button to update widget
            val refreshIntent = Intent(context, TradingWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.example.widget.ACTION_WIDGET_REFRESH"

        fun forceUpdate(context: Context) {
            val intent = Intent(context, TradingWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
