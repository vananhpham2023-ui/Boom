package com.boom.timetracking

import com.boom.timetracking.AccessibilityEventRelay

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Collects usage events from UsageStatsManager and AccessibilityService. The collector keeps
 * a hot flow of [UsageEvent] instances for downstream session builders.
 */
class UsageEventCollector(
    private val context: Context,
    private val usageStatsManager: UsageStatsManager,
    private val eventSink: UsageEventSink,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    interface UsageEventSink {
        suspend fun emit(event: UsageEvent)
    }

    companion object {
        private val LOOKBACK = TimeUnit.MINUTES.toMillis(5)
    }

    private var pollingJob: Job? = null

    fun start() {
        if (!hasUsageStatsPermission(context)) {
            throw IllegalStateException("Usage stats permission missing")
        }
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            var lastTimestamp = clock() - LOOKBACK
            while (true) {
                val now = clock()
                val usageEvents = usageStatsManager.queryEvents(lastTimestamp, now)
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    val mapped = event.toUsageEvent() ?: continue
                    lastTimestamp = maxOf(lastTimestamp, mapped.timestamp)
                    eventSink.emit(mapped)
                }
                lastTimestamp = now
                delay(TimeUnit.SECONDS.toMillis(5))
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun UsageEvents.Event.toUsageEvent(): UsageEvent? {
        val pkg = packageName ?: return null
        return when (eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEvent(
                packageName = pkg,
                eventType = UsageEvent.EventType.FOREGROUND,
                timestamp = timeStamp,
                source = UsageEvent.Source.USAGE_STATS,
                taskRootClass = className
            )
            UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEvent(
                packageName = pkg,
                eventType = UsageEvent.EventType.BACKGROUND,
                timestamp = timeStamp,
                source = UsageEvent.Source.USAGE_STATS,
                taskRootClass = className
            )
            else -> null
        }
    }
}

/**
 * Accessibility service implementation that emits foreground/background events when usage stats is
 * throttled or missing (e.g. on certain OEM builds).
 */
abstract class TimelineAccessibilityService : AccessibilityService(), UsageEventCollector.UsageEventSink {

    private val serviceScope = CoroutineScope(Job() + Dispatchers.IO)
    private val _events = MutableSharedFlow<UsageEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<UsageEvent> = _events.asSharedFlow()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val timestamp = event.eventTime
        serviceScope.launch {
            val usageEvent = UsageEvent(
                packageName = packageName,
                eventType = UsageEvent.EventType.FOREGROUND,
                timestamp = timestamp,
                source = UsageEvent.Source.ACCESSIBILITY,
                taskRootClass = event.className?.toString()
            )
            _events.emit(usageEvent)
            AccessibilityEventRelay.publish(usageEvent)
        }
    }

    override fun onInterrupt() {}

    override suspend fun emit(event: UsageEvent) {
        withContext(Dispatchers.IO) {
            _events.emit(event)
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
