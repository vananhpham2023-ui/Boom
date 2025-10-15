package com.boom.timetracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateful builder that consumes {@link UsageEvent}s and emits completed {@link Session} objects.
 */
public final class SessionBuilder {

    public static final long DEFAULT_INACTIVITY_THRESHOLD = 15_000L;
    public static final long DEFAULT_STRAY_BACKGROUND_WINDOW = 5_000L;

    public static final class Stats {
        private final long emittedSessions;
        private final long strayBackgroundSessions;
        private final long autoRepairedSessions;

        public Stats(long emittedSessions, long strayBackgroundSessions, long autoRepairedSessions) {
            this.emittedSessions = emittedSessions;
            this.strayBackgroundSessions = strayBackgroundSessions;
            this.autoRepairedSessions = autoRepairedSessions;
        }

        public long getEmittedSessions() {
            return emittedSessions;
        }

        public long getStrayBackgroundSessions() {
            return strayBackgroundSessions;
        }

        public long getAutoRepairedSessions() {
            return autoRepairedSessions;
        }
    }

    private static final class MutableState {
        private final String packageName;
        private long start;
        private long lastEvent;
        private int eventCount;
        private final EnumSet<UsageEvent.Source> sources;
        private final LinkedHashMap<String, String> attributes;

        private MutableState(String packageName, long start, UsageEvent.Source source, Map<String, String> metadata) {
            this.packageName = packageName;
            this.start = start;
            this.lastEvent = start;
            this.eventCount = 1;
            this.sources = EnumSet.of(source);
            this.attributes = new LinkedHashMap<>();
            if (metadata != null) {
                this.attributes.putAll(metadata);
            }
        }

        private void update(UsageEvent event) {
            this.lastEvent = event.getTimestamp();
            this.eventCount += 1;
            this.sources.add(event.getSource());
            if (!event.getMetadata().isEmpty()) {
                this.attributes.putAll(event.getMetadata());
            }
        }

        private Session toSession(long endTime, Session.RepairStatus repairStatus) {
            return new Session(
                packageName,
                start,
                endTime,
                eventCount,
                sources,
                determineConfidence(sources),
                repairStatus,
                attributes
            );
        }
    }

    private final long inactivityThresholdMillis;
    private final long strayBackgroundWindowMillis;
    private long emitted = 0L;
    private long strayBackground = 0L;
    private long autoRepaired = 0L;
    private final Map<String, MutableState> activeStates = new HashMap<>();

    public SessionBuilder() {
        this(DEFAULT_INACTIVITY_THRESHOLD, DEFAULT_STRAY_BACKGROUND_WINDOW);
    }

    public SessionBuilder(long inactivityThresholdMillis, long strayBackgroundWindowMillis) {
        this.inactivityThresholdMillis = inactivityThresholdMillis;
        this.strayBackgroundWindowMillis = strayBackgroundWindowMillis;
    }

    public Stats stats() {
        return new Stats(emitted, strayBackground, autoRepaired);
    }

    public void reset() {
        emitted = 0;
        strayBackground = 0;
        autoRepaired = 0;
        activeStates.clear();
    }

    public List<Session> process(UsageEvent event) {
        switch (event.getEventType()) {
            case FOREGROUND:
                return handleForeground(event);
            case BACKGROUND:
                return handleBackground(event);
            default:
                return Collections.emptyList();
        }
    }

    public List<Session> flush(long now) {
        List<Session> completed = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, MutableState> entry : activeStates.entrySet()) {
            MutableState state = entry.getValue();
            if (now - state.lastEvent >= inactivityThresholdMillis) {
                completed.add(state.toSession(state.lastEvent, Session.RepairStatus.AUTO_REPAIRED));
                autoRepaired += 1;
                emitted += 1;
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            activeStates.remove(key);
        }
        return completed;
    }

    private List<Session> handleForeground(UsageEvent event) {
        MutableState state = activeStates.get(event.getPackageName());
        if (state == null) {
            activeStates.put(event.getPackageName(), new MutableState(
                event.getPackageName(), event.getTimestamp(), event.getSource(), event.getMetadata()
            ));
            return Collections.emptyList();
        }
        long gap = event.getTimestamp() - state.lastEvent;
        if (gap > inactivityThresholdMillis) {
            Session closed = state.toSession(state.lastEvent, Session.RepairStatus.AUTO_REPAIRED);
            autoRepaired += 1;
            emitted += 1;
            activeStates.put(event.getPackageName(), new MutableState(
                event.getPackageName(), event.getTimestamp(), event.getSource(), event.getMetadata()
            ));
            return Collections.singletonList(closed);
        }
        state.update(event);
        return Collections.emptyList();
    }

    private List<Session> handleBackground(UsageEvent event) {
        MutableState state = activeStates.get(event.getPackageName());
        if (state == null) {
            Session repaired = strayBackgroundSession(event);
            emitted += 1;
            strayBackground += 1;
            autoRepaired += 1;
            return Collections.singletonList(repaired);
        }
        long gap = event.getTimestamp() - state.lastEvent;
        if (gap > inactivityThresholdMillis) {
            List<Session> sessions = new ArrayList<>();
            Session closed = state.toSession(state.lastEvent, Session.RepairStatus.AUTO_REPAIRED);
            autoRepaired += 1;
            emitted += 1;
            sessions.add(closed);
            activeStates.remove(event.getPackageName());
            if (gap <= strayBackgroundWindowMillis) {
                Session repaired = strayBackgroundSession(event);
                sessions.add(repaired);
                strayBackground += 1;
                autoRepaired += 1;
                emitted += 1;
            }
            return sessions;
        }
        state.update(event);
        Session session = state.toSession(event.getTimestamp(), Session.RepairStatus.NONE);
        emitted += 1;
        activeStates.remove(event.getPackageName());
        return Collections.singletonList(session);
    }

    private Session strayBackgroundSession(UsageEvent event) {
        return new Session(
            event.getPackageName(),
            event.getTimestamp(),
            event.getTimestamp(),
            1,
            Collections.singleton(event.getSource()),
            Session.Confidence.LOW,
            Session.RepairStatus.AUTO_REPAIRED,
            event.getMetadata()
        );
    }

    private static Session.Confidence determineConfidence(EnumSet<UsageEvent.Source> sources) {
        boolean hasUsageStats = sources.contains(UsageEvent.Source.USAGE_STATS);
        boolean hasAccessibility = sources.contains(UsageEvent.Source.ACCESSIBILITY);
        if (hasUsageStats && hasAccessibility) {
            return Session.Confidence.HIGH;
        }
        if (hasUsageStats) {
            return Session.Confidence.HIGH;
        }
        if (hasAccessibility) {
            return Session.Confidence.MEDIUM;
        }
        return Session.Confidence.LOW;
    }
}
