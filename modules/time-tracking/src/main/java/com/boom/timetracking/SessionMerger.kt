package com.boom.timetracking

/**
 * Utilities to merge, repair and split sessions to satisfy KPIs defined in T3.
 */
object SessionMerger {

    /**
     * Merge sessions that have small gaps or cross midnight boundaries.
     */
    fun mergeSessions(
        sessions: List<Session>,
        mergeGapMillis: Long,
        maxSessionDurationMillis: Long
    ): List<Session> {
        if (sessions.isEmpty()) return emptyList()
        val sorted = sessions.sortedBy { it.startTime }
        val merged = mutableListOf<Session>()
        var current = sorted.first()
        for (candidate in sorted.drop(1)) {
            if (candidate.packageName == current.packageName &&
                candidate.startTime - current.endTime <= mergeGapMillis &&
                candidate.startTime - current.startTime <= maxSessionDurationMillis
            ) {
                current = current.copy(
                    endTime = maxOf(current.endTime, candidate.endTime),
                    eventCount = current.eventCount + candidate.eventCount,
                    confidence = minOf(current.confidence, candidate.confidence),
                    sourceMask = current.sourceMask or candidate.sourceMask
                )
            } else {
                merged += current
                current = candidate
            }
        }
        merged += current
        return merged
    }

    /**
     * Repair overlapping sessions by trimming to avoid double counting.
     */
    fun repairOverlaps(sessions: List<Session>): List<Session> {
        if (sessions.isEmpty()) return emptyList()
        val sorted = sessions.sortedWith(compareBy<Session> { it.packageName }.thenBy { it.startTime })
        val repaired = mutableListOf<Session>()
        var lastByPackage = mutableMapOf<String, Session>()
        for (session in sorted) {
            val last = lastByPackage[session.packageName]
            if (last == null || session.startTime >= last.endTime) {
                repaired += session
                lastByPackage[session.packageName] = session
            } else {
                val clippedStart = maxOf(session.startTime, last.endTime)
                if (clippedStart < session.endTime) {
                    val trimmed = session.copy(startTime = clippedStart)
                    repaired += trimmed
                    lastByPackage[session.packageName] = trimmed
                }
            }
        }
        return repaired
    }
}
