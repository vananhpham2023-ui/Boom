package com.boom.timetracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges adjacent sessions of the same application when the gap between them is within
 * {@code maxGapMillis}. When a merge happens, metadata is combined and confidence is promoted
 * to the strongest of the merged sessions.
 */
public final class SessionMerger {

    private SessionMerger() {}

    public static List<Session> merge(List<Session> sessions, long maxGapMillis) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        List<Session> sorted = new ArrayList<>(sessions);
        sorted.sort(Comparator.comparing(Session::getPackageName).thenComparing(Session::getStartTime));
        List<Session> result = new ArrayList<>();
        Session cursor = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            Session next = sorted.get(i);
            if (cursor.getPackageName().equals(next.getPackageName())
                && next.getStartTime() - cursor.getEndTime() <= maxGapMillis) {
                cursor = merge(cursor, next);
            } else {
                result.add(cursor);
                cursor = next;
            }
        }
        result.add(cursor);
        return result;
    }

    private static Session merge(Session first, Session second) {
        Map<String, String> mergedAttributes = new LinkedHashMap<>(first.getAttributes());
        for (Map.Entry<String, String> entry : second.getAttributes().entrySet()) {
            mergedAttributes.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Set<UsageEvent.Source> mergedSources = EnumSetBuilder.merge(first.getSources(), second.getSources());
        Session.Confidence confidence = max(first.getConfidence(), second.getConfidence());
        Session.RepairStatus repairStatus =
            first.getRepairStatus() == Session.RepairStatus.AUTO_REPAIRED ||
            second.getRepairStatus() == Session.RepairStatus.AUTO_REPAIRED
                ? Session.RepairStatus.AUTO_REPAIRED
                : Session.RepairStatus.NONE;
        return new Session(
            first.getPackageName(),
            Math.min(first.getStartTime(), second.getStartTime()),
            Math.max(first.getEndTime(), second.getEndTime()),
            first.getEventCount() + second.getEventCount(),
            mergedSources,
            confidence,
            repairStatus,
            mergedAttributes
        );
    }

    private static Session.Confidence max(Session.Confidence first, Session.Confidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static final class EnumSetBuilder {
        private static Set<UsageEvent.Source> merge(Set<UsageEvent.Source> a, Set<UsageEvent.Source> b) {
            Objects.requireNonNull(a, "a");
            Objects.requireNonNull(b, "b");
            java.util.EnumSet<UsageEvent.Source> set = java.util.EnumSet.noneOf(UsageEvent.Source.class);
            set.addAll(a);
            set.addAll(b);
            return set;
        }
    }
}
