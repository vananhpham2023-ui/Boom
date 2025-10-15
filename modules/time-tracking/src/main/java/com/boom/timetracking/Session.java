package com.boom.timetracking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregated usage window derived from multiple {@link UsageEvent} instances.
 */
public final class Session {

    public enum Confidence { LOW, MEDIUM, HIGH }

    public enum RepairStatus { NONE, AUTO_REPAIRED }

    private final String packageName;
    private final long startTime;
    private final long endTime;
    private final int eventCount;
    private final Set<UsageEvent.Source> sources;
    private final Confidence confidence;
    private final RepairStatus repairStatus;
    private final Map<String, String> attributes;

    public Session(String packageName,
                   long startTime,
                   long endTime,
                   int eventCount,
                   Set<UsageEvent.Source> sources,
                   Confidence confidence,
                   RepairStatus repairStatus,
                   Map<String, String> attributes) {
        if (endTime < startTime) {
            throw new IllegalArgumentException("Session end time must be >= start time");
        }
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.startTime = startTime;
        this.endTime = endTime;
        this.eventCount = eventCount;
        this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.repairStatus = Objects.requireNonNull(repairStatus, "repairStatus");
        Map<String, String> copy = new LinkedHashMap<>();
        if (attributes != null) {
            copy.putAll(attributes);
        }
        this.attributes = Collections.unmodifiableMap(copy);
    }

    public Session(String packageName,
                   long startTime,
                   long endTime,
                   int eventCount,
                   Set<UsageEvent.Source> sources,
                   Confidence confidence) {
        this(packageName, startTime, endTime, eventCount, sources, confidence, RepairStatus.NONE, Collections.emptyMap());
    }

    public String getPackageName() {
        return packageName;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public int getEventCount() {
        return eventCount;
    }

    public Set<UsageEvent.Source> getSources() {
        return sources;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public RepairStatus getRepairStatus() {
        return repairStatus;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public long getDurationMillis() {
        return endTime - startTime;
    }

    public boolean overlaps(Session other) {
        return packageName.equals(other.packageName)
            && startTime < other.endTime
            && other.startTime < endTime;
    }

    public Session copyWith(long start, long end, RepairStatus status) {
        return new Session(packageName, start, end, eventCount, sources, confidence, status, attributes);
    }

    public Session copyWith(long start, long end, RepairStatus status, Confidence confidenceOverride) {
        return new Session(packageName, start, end, eventCount, sources, confidenceOverride, status, attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Session)) return false;
        Session session = (Session) o;
        return startTime == session.startTime &&
            endTime == session.endTime &&
            eventCount == session.eventCount &&
            packageName.equals(session.packageName) &&
            sources.equals(session.sources) &&
            confidence == session.confidence &&
            repairStatus == session.repairStatus &&
            attributes.equals(session.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, startTime, endTime, eventCount, sources, confidence, repairStatus, attributes);
    }

    @Override
    public String toString() {
        return "Session{" +
            "package='" + packageName + '\'' +
            ", start=" + startTime +
            ", end=" + endTime +
            ", events=" + eventCount +
            ", sources=" + sources +
            ", confidence=" + confidence +
            ", repairStatus=" + repairStatus +
            '}';
    }
}
