package com.boom.timetracking

/**
 * Represents a single app foreground/background transition emitted by UsageStats
 * or synthesized from accessibility signals. The timestamp is expressed in epoch millis.
 */
data class UsageEvent(
    val packageName: String,
    val eventType: EventType,
    val timestamp: Long,
    val source: Source,
    val taskRootClass: String? = null
) {
    enum class EventType { FOREGROUND, BACKGROUND }

    enum class Source { USAGE_STATS, ACCESSIBILITY }

    companion object {
        fun now(
            packageName: String,
            eventType: EventType,
            source: Source,
            taskRootClass: String? = null,
            clock: () -> Long = { System.currentTimeMillis() }
        ): UsageEvent = UsageEvent(
            packageName = packageName,
            eventType = eventType,
            timestamp = clock(),
            source = source,
            taskRootClass = taskRootClass
        )
    }
}
