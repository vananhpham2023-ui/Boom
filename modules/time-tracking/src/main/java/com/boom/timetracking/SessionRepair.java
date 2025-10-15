package com.boom.timetracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies deterministic repair rules to sessions: removes negative durations, fixes overlaps and
 * clamps cross-day sessions to midnight boundaries.
 */
public final class SessionRepair {

    private static final long ONE_DAY = 86_400_000L;

    private SessionRepair() {}

    public static List<Session> repair(List<Session> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        List<Session> sorted = new ArrayList<>(sessions);
        sorted.sort(Comparator.comparing(Session::getPackageName).thenComparing(Session::getStartTime));
        List<Session> result = new ArrayList<>();
        Map<String, Long> lastEndByPackage = new HashMap<>();
        for (Session session : sorted) {
            long lastEnd = lastEndByPackage.getOrDefault(session.getPackageName(), session.getStartTime());
            long normalizedStart = Math.max(session.getStartTime(), lastEnd);
            long normalizedEnd = Math.max(normalizedStart, session.getEndTime());
            Range clamped = clampToDay(normalizedStart, normalizedEnd);
            boolean repaired = clamped.start != session.getStartTime()
                || clamped.end != session.getEndTime()
                || normalizedStart != session.getStartTime();
            Session repairedSession = repaired
                ? new Session(
                    session.getPackageName(),
                    clamped.start,
                    clamped.end,
                    session.getEventCount(),
                    session.getSources(),
                    session.getConfidence(),
                    Session.RepairStatus.AUTO_REPAIRED,
                    session.getAttributes()
                )
                : session;
            result.add(repairedSession);
            lastEndByPackage.put(session.getPackageName(), repairedSession.getEndTime());
        }
        return result;
    }

    private static Range clampToDay(long start, long end) {
        long dayStart = startOfDay(start);
        long dayEnd = dayStart + ONE_DAY;
        if (end <= dayEnd) {
            return new Range(start, end);
        }
        return new Range(start, dayEnd);
    }

    private static long startOfDay(long timestamp) {
        long days = Math.floorDiv(timestamp, ONE_DAY);
        return days * ONE_DAY;
    }

    private static final class Range {
        final long start;
        final long end;

        private Range(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
}
