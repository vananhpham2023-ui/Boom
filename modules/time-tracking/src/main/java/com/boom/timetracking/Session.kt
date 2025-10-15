package com.boom.timetracking

/**
 * Represents a contiguous foreground session for a package.
 */
data class Session(
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val eventCount: Int,
    val confidence: Confidence = Confidence.HIGH,
    val sourceMask: Int = SourceMask.fromSources(setOf(UsageEvent.Source.USAGE_STATS))
) {
    val durationMillis: Long
        get() = (endTime - startTime).coerceAtLeast(0L)

    enum class Confidence { HIGH, MEDIUM, LOW }
}

object SourceMask {
    private val bitMapping: Map<UsageEvent.Source, Int> = mapOf(
        UsageEvent.Source.USAGE_STATS to 0b01,
        UsageEvent.Source.ACCESSIBILITY to 0b10
    )

    fun fromSources(sources: Set<UsageEvent.Source>): Int =
        sources.fold(0) { acc, source -> acc or (bitMapping[source] ?: 0) }

    fun toSources(mask: Int): Set<UsageEvent.Source> =
        bitMapping.filterValues { value -> mask and value != 0 }.keys
}
