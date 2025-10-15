package com.boom.timetracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.boom.timetracking.AccessibilityEventRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Foreground service responsible for keeping the collector alive, monitoring the process state
 * and rehydrating pending sessions after process death.
 */
class TimeTrackingService : Service(), UsageEventCollector.UsageEventSink {

    private lateinit var scope: CoroutineScope
    private lateinit var serviceJob: Job
    private lateinit var collector: UsageEventCollector
    private lateinit var sessionBuilder: SessionBuilder
    private lateinit var repository: SessionRepository

    override fun onCreate() {
        super.onCreate()
        serviceJob = SupervisorJob()
        scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
        repository = SessionRepository()
        sessionBuilder = SessionBuilder(scope, repository)
        collector = UsageEventCollector(
            context = this,
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager,
            eventSink = sessionBuilder,
            scope = scope
        )
        AccessibilityEventRelay.registerSink(sessionBuilder)
        sessionBuilder.start()
        collector.start()
        startForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collector.stop()
        sessionBuilder.stop()
        AccessibilityEventRelay.unregisterSink(sessionBuilder)
        serviceJob.cancel()
        super.onDestroy()
    }

    override suspend fun emit(event: UsageEvent) {
        sessionBuilder.emit(event)
    }

    private fun startForegroundService() {
        val channelId = "time_tracking_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Usage Tracking",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在记录短视频使用情况")
            .setContentText("保持应用运行以获取准确统计")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, TimeTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimeTrackingService::class.java))
        }

        fun batteryOptimizationIntent(context: Context): Intent {
            val packageUri = "package:" + context.packageName
            return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse(packageUri)
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }
}
