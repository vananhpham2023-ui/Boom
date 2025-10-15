package com.boom.timetracking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a foreground/background transition for a single application package.
 */
public final class UsageEvent {

    public enum EventType { FOREGROUND, BACKGROUND }

    public enum Source {
        USAGE_STATS,
        ACCESSIBILITY,
        HEARTBEAT
    }

    private final String packageName;
    private final EventType eventType;
    private final long timestamp;
    private final Source source;
    private final Map<String, String> metadata;

    public UsageEvent(String packageName,
                      EventType eventType,
                      long timestamp,
                      Source source,
                      Map<String, String> metadata) {
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.timestamp = timestamp;
        this.source = Objects.requireNonNull(source, "source");
        Map<String, String> copy = new LinkedHashMap<>();
        if (metadata != null) {
            copy.putAll(metadata);
        }
        this.metadata = Collections.unmodifiableMap(copy);
    }

    public UsageEvent(String packageName,
                      EventType eventType,
                      long timestamp,
                      Source source) {
        this(packageName, eventType, timestamp, source, Collections.emptyMap());
    }

    public String getPackageName() {
        return packageName;
    }

    public EventType getEventType() {
        return eventType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Source getSource() {
        return source;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
