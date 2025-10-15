package com.boom.timetracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Global relay that allows the accessibility service (running in a separate context) to feed
 * usage events back into the application layer without holding a hard reference to activities.
 */
object AccessibilityEventRelay {

    private val sinks = CopyOnWriteArraySet<UsageEventCollector.UsageEventSink>()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _events = MutableSharedFlow<UsageEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<UsageEvent> = _events.asSharedFlow()

    fun registerSink(sink: UsageEventCollector.UsageEventSink) {
        sinks += sink
    }

    fun unregisterSink(sink: UsageEventCollector.UsageEventSink) {
        sinks -= sink
    }

    fun publish(event: UsageEvent) {
        scope.launch {
            sinks.forEach { sink ->
                runCatching { sink.emit(event) }
            }
            _events.emit(event)
        }
    }
}
