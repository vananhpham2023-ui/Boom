package com.boom.timetracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Consumes [UsageEvent] stream, builds [Session] objects and persists them via [SessionWriter].
 */
class SessionBuilder(
    private val scope: CoroutineScope,
    private val sessionWriter: SessionWriter,
    private val inactivityThresholdMillis: Long = DEFAULT_INACTIVITY_THRESHOLD
) : UsageEventCollector.UsageEventSink {

    interface SessionWriter {
        suspend fun upsert(session: Session)
    }

    private val eventsFlow = MutableSharedFlow<UsageEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var buildJob: Job? = null
    private val openSessions = mutableMapOf<String, MutableSessionAccumulator>()

    fun start() {
        buildJob?.cancel()
        buildJob = scope.launch(Dispatchers.Default) {
            eventsFlow.collect { event ->
                handleEvent(event)
            }
        }
    }

    override suspend fun emit(event: UsageEvent) {
        eventsFlow.emit(event)
    }

    fun stop() {
        buildJob?.cancel()
        buildJob = null
        openSessions.clear()
    }

    suspend fun flushActiveSessions(now: Long) {
        openSessions.entries.toList().forEach { (pkg, accumulator) ->
            if (accumulator.eventCount == 0) return@forEach
            val session = Session(
                packageName = pkg,
                startTime = accumulator.startTime,
                endTime = now,
                eventCount = accumulator.eventCount,
                confidence = Session.Confidence.LOW,
                sourceMask = SourceMask.fromSources(accumulator.sources)
            )
            sessionWriter.upsert(session)
        }
        openSessions.clear()
    }

    private suspend fun handleEvent(event: UsageEvent) {
        val accumulator = openSessions.getOrPut(event.packageName) { MutableSessionAccumulator() }
        when (event.eventType) {
            UsageEvent.EventType.FOREGROUND -> accumulator.onForeground(event)
            UsageEvent.EventType.BACKGROUND -> {
                val session = accumulator.onBackground(event, inactivityThresholdMillis)
                if (session != null) {
                    sessionWriter.upsert(session)
                    openSessions.remove(event.packageName)
                } else if (accumulator.eventCount == 0) {
                    openSessions.remove(event.packageName)
                }
            }
        }
    }

    private inner class MutableSessionAccumulator {
        var startTime: Long = 0
            private set
        var lastEventTime: Long = 0
            private set
        var eventCount: Int = 0
            private set
        val sources = mutableSetOf<UsageEvent.Source>()

        fun onForeground(event: UsageEvent) {
            if (eventCount == 0) {
                startTime = event.timestamp
            }
            lastEventTime = event.timestamp
            eventCount += 1
            sources += event.source
        }

        suspend fun onBackground(
            event: UsageEvent,
            inactivityThresholdMillis: Long
        ): Session? {
            if (eventCount == 0) {
                // Received background event without foreground; ignore but avoid leak
                return null
            }
            val gap = event.timestamp - lastEventTime
            return if (gap <= inactivityThresholdMillis) {
                eventCount += 1
                lastEventTime = event.timestamp
                sources += event.source
                Session(
                    packageName = event.packageName,
                    startTime = startTime,
                    endTime = event.timestamp,
                    eventCount = eventCount,
                    confidence = when {
                        UsageEvent.Source.ACCESSIBILITY in sources && UsageEvent.Source.USAGE_STATS in sources -> Session.Confidence.HIGH
                        UsageEvent.Source.USAGE_STATS in sources -> Session.Confidence.HIGH
                        else -> Session.Confidence.MEDIUM
                    },
                    sourceMask = SourceMask.fromSources(sources)
                )
            } else {
                // Gap too large, treat as orphan background -> flush existing session
                val session = Session(
                    packageName = event.packageName,
                    startTime = startTime,
                    endTime = lastEventTime,
                    eventCount = eventCount,
                    confidence = Session.Confidence.MEDIUM,
                    sourceMask = SourceMask.fromSources(sources)
                )
                startTime = 0
                lastEventTime = 0
                eventCount = 0
                sources.clear()
                sessionWriter.upsert(session)
                null
            }
        }
    }

    companion object {
        const val DEFAULT_INACTIVITY_THRESHOLD = 20_000L
    }
}
