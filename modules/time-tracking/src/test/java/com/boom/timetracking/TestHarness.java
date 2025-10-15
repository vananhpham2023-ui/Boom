package com.boom.timetracking;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Minimal assertion harness that exercises the time-tracking core logic without external
 * dependencies. Throws {@link AssertionError} when a KPI check fails.
 */
public final class TestHarness {

    public static void main(String[] args) {
        shouldBuildSessionsWithHighAccuracy();
        shouldFlushInactiveSessions();
        shouldMergeSessionsWithinThreshold();
        shouldRepairOverlapsAndCrossDaySessions();
        System.out.println("All time-tracking module checks passed.");
    }

    private static void shouldBuildSessionsWithHighAccuracy() {
        SessionBuilder builder = new SessionBuilder(5_000L, 2_000L);
        List<UsageEvent> events = List.of(
            event("app.a", UsageEvent.EventType.FOREGROUND, 0L, UsageEvent.Source.USAGE_STATS),
            event("app.a", UsageEvent.EventType.BACKGROUND, 4_000L, UsageEvent.Source.USAGE_STATS),
            event("app.b", UsageEvent.EventType.FOREGROUND, 10_000L, UsageEvent.Source.ACCESSIBILITY),
            event("app.b", UsageEvent.EventType.BACKGROUND, 15_000L, UsageEvent.Source.USAGE_STATS),
            event("app.a", UsageEvent.EventType.FOREGROUND, 20_000L, UsageEvent.Source.ACCESSIBILITY),
            event("app.a", UsageEvent.EventType.FOREGROUND, 22_000L, UsageEvent.Source.USAGE_STATS),
            event("app.a", UsageEvent.EventType.BACKGROUND, 25_000L, UsageEvent.Source.USAGE_STATS),
            event("app.c", UsageEvent.EventType.BACKGROUND, 40_000L, UsageEvent.Source.ACCESSIBILITY)
        );
        List<Session> produced = new ArrayList<>();
        for (UsageEvent event : events) {
            produced.addAll(builder.process(event));
        }
        List<Session> expected = List.of(
            new Session("app.a", 0L, 4_000L, 2, EnumSet.of(UsageEvent.Source.USAGE_STATS), Session.Confidence.HIGH),
            new Session("app.b", 10_000L, 15_000L, 2, EnumSet.of(UsageEvent.Source.ACCESSIBILITY, UsageEvent.Source.USAGE_STATS), Session.Confidence.HIGH),
            new Session("app.a", 20_000L, 25_000L, 3, EnumSet.of(UsageEvent.Source.ACCESSIBILITY, UsageEvent.Source.USAGE_STATS), Session.Confidence.HIGH),
            new Session("app.c", 40_000L, 40_000L, 1, EnumSet.of(UsageEvent.Source.ACCESSIBILITY), Session.Confidence.LOW, Session.RepairStatus.AUTO_REPAIRED, Map.of())
        );
        assertEquals(expected, produced, "Session builder output mismatch");
        Metrics metrics = accuracy(expected, produced);
        assertTrue(metrics.accuracy >= 0.95, "accuracy < 95%");
        assertTrue(metrics.missRate < 0.02, "miss rate >= 2%");
        assertTrue(metrics.duplicateRate < 0.01, "duplicate rate >= 1%");
        SessionBuilder.Stats stats = builder.stats();
        assertEquals(4L, stats.getEmittedSessions(), "emitted sessions count mismatch");
        assertEquals(1L, stats.getStrayBackgroundSessions(), "stray background sessions count mismatch");
        assertEquals(1L, stats.getAutoRepairedSessions(), "auto repaired sessions count mismatch");
    }

    private static void shouldFlushInactiveSessions() {
        SessionBuilder builder = new SessionBuilder(5_000L, 2_000L);
        builder.process(event("app.d", UsageEvent.EventType.FOREGROUND, 0L, UsageEvent.Source.USAGE_STATS));
        List<Session> flushed = builder.flush(10_000L);
        assertEquals(1, flushed.size(), "flush should emit one session");
        Session session = flushed.get(0);
        assertEquals("app.d", session.getPackageName(), "flush package mismatch");
        assertEquals(Session.RepairStatus.AUTO_REPAIRED, session.getRepairStatus(), "flush repair status");
        assertTrue(session.getEndTime() <= 10_000L, "flush end time beyond now");
    }

    private static void shouldMergeSessionsWithinThreshold() {
        List<Session> input = List.of(
            new Session("pkg", 0L, 5_000L, 2, EnumSet.of(UsageEvent.Source.USAGE_STATS), Session.Confidence.MEDIUM),
            new Session("pkg", 5_500L, 8_000L, 1, EnumSet.of(UsageEvent.Source.USAGE_STATS), Session.Confidence.HIGH),
            new Session("pkg", 20_000L, 25_000L, 1, EnumSet.of(UsageEvent.Source.USAGE_STATS), Session.Confidence.LOW)
        );
        List<Session> merged = SessionMerger.merge(input, 1_000L);
        assertEquals(2, merged.size(), "merge result size");
        Session first = merged.get(0);
        assertEquals(0L, first.getStartTime(), "merged start time");
        assertEquals(8_000L, first.getEndTime(), "merged end time");
        assertEquals(3, first.getEventCount(), "merged event count");
        assertEquals(Session.Confidence.HIGH, first.getConfidence(), "merged confidence");
    }

    private static void shouldRepairOverlapsAndCrossDaySessions() {
        long dayStart = 86_400_000L;
        long nextDayOverflow = dayStart + 86_400_000L + 20_000L;
        List<Session> sessions = List.of(
            new Session("pkg", dayStart + 10_000L, dayStart + 60_000L, 1, EnumSet.of(UsageEvent.Source.USAGE_STATS), Session.Confidence.MEDIUM),
            new Session("pkg", dayStart + 50_000L, nextDayOverflow, 1, EnumSet.of(UsageEvent.Source.ACCESSIBILITY), Session.Confidence.LOW)
        );
        List<Session> repaired = SessionRepair.repair(sessions);
        assertEquals(2, repaired.size(), "repaired size");
        Session first = repaired.get(0);
        assertEquals(dayStart + 10_000L, first.getStartTime(), "repaired first start");
        assertEquals(dayStart + 60_000L, first.getEndTime(), "repaired first end");
        assertEquals(Session.RepairStatus.NONE, first.getRepairStatus(), "first repair status");
        Session second = repaired.get(1);
        assertEquals(dayStart + 60_000L, second.getStartTime(), "repaired second start");
        assertEquals(dayStart + 86_400_000L, second.getEndTime(), "repaired second end");
        assertEquals(Session.RepairStatus.AUTO_REPAIRED, second.getRepairStatus(), "second repair status");
    }

    private static UsageEvent event(String pkg, UsageEvent.EventType type, long timestamp, UsageEvent.Source source) {
        return new UsageEvent(pkg, type, timestamp, source);
    }

    private record Metrics(double accuracy, double missRate, double duplicateRate) {}

    private static Metrics accuracy(List<Session> expected, List<Session> actual) {
        long matches = expected.stream().filter(actual::contains).count();
        double accuracy = matches / (double) expected.size();
        double missRate = (expected.size() - matches) / (double) expected.size();
        double duplicateRate = (actual.size() - matches) / (double) actual.size();
        return new Metrics(accuracy, missRate, duplicateRate);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
