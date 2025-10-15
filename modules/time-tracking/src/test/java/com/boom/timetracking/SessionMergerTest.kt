package com.boom.timetracking

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionMergerTest {

    @Test
    fun mergeSessionsWithinGap() {
        val sessions = listOf(
            Session("a", 0, 5_000, 2),
            Session("a", 6_000, 10_000, 2)
        )
        val merged = SessionMerger.mergeSessions(sessions, mergeGapMillis = 2_000, maxSessionDurationMillis = 60_000)
        assertEquals(1, merged.size)
        assertEquals(0, merged[0].startTime)
        assertEquals(10_000, merged[0].endTime)
        assertEquals(4, merged[0].eventCount)
    }

    @Test
    fun mergeSessionsRespectMaxDuration() {
        val sessions = listOf(
            Session("a", 0, 10_000, 2),
            Session("a", 15_000, 20_000, 2)
        )
        val merged = SessionMerger.mergeSessions(sessions, mergeGapMillis = 10_000, maxSessionDurationMillis = 12_000)
        assertEquals(2, merged.size)
    }

    @Test
    fun repairOverlapsTrimsSecondSession() {
        val sessions = listOf(
            Session("a", 0, 10_000, 2),
            Session("a", 5_000, 12_000, 2)
        )
        val repaired = SessionMerger.repairOverlaps(sessions)
        assertEquals(2, repaired.size)
        assertEquals(10_000, repaired[1].startTime)
        assertEquals(12_000, repaired[1].endTime)
    }
}
