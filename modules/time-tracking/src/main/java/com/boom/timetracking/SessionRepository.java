package com.boom.timetracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public interface SessionRepository {
    void upsert(Session session);
    List<Session> readAll();

    class InMemorySessionRepository implements SessionRepository {
        private final List<Session> sessions = new ArrayList<>();
        private final Lock lock = new ReentrantLock();

        @Override
        public void upsert(Session session) {
            lock.lock();
            try {
                int index = -1;
                for (int i = 0; i < sessions.size(); i++) {
                    Session existing = sessions.get(i);
                    if (existing.getPackageName().equals(session.getPackageName())
                        && existing.getStartTime() == session.getStartTime()
                        && existing.getEndTime() == session.getEndTime()) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0) {
                    sessions.set(index, session);
                } else {
                    sessions.add(session);
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public List<Session> readAll() {
            lock.lock();
            try {
                return Collections.unmodifiableList(new ArrayList<>(sessions));
            } finally {
                lock.unlock();
            }
        }
    }
}
