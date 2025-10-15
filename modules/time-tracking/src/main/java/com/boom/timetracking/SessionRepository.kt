package com.boom.timetracking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal repository abstraction for MVP stage. For now it stores sessions in memory and exposes
 * hooks for persistence layer integration (Room/SQLCipher).
 */
class SessionRepository : SessionBuilder.SessionWriter {

    private val inMemoryStore = ConcurrentHashMap<String, MutableList<Session>>()

    override suspend fun upsert(session: Session) {
        withContext(Dispatchers.IO) {
            val sessions = inMemoryStore.getOrPut(session.packageName) { mutableListOf() }
            sessions.removeIf { it.startTime == session.startTime && it.endTime == session.endTime }
            sessions += session
            // TODO: replace with DAO insertOrUpdate once database layer is ready.
        }
    }

    suspend fun getSessions(packageName: String): List<Session> =
        withContext(Dispatchers.IO) { inMemoryStore[packageName]?.toList().orEmpty() }
}
