package com.example.session;

import com.example.session.proxy.ChangeTrackingProxy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Session implementation backed by Hazelcast with JSON serialization support.
 * Uses change tracking proxies to detect modifications to nested collections.
 */
public class HazelcastSession implements Session {

    private static final int DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS = 1800; // 30 minutes

    private String id;
    private String originalId;
    private Map<String, Object> attributes;
    private long creationTime;
    private long lastAccessedTime;
    private int maxInactiveIntervalSeconds;

    /**
     * Transient fields - not serialized to JSON.
     */
    @JsonIgnore
    private transient boolean changed;

    @JsonIgnore
    private transient Runnable changeCallback;

    /**
     * Default constructor for Jackson deserialization.
     */
    public HazelcastSession() {
        this.attributes = new HashMap<>();
        this.maxInactiveIntervalSeconds = DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;
    }

    /**
     * Creates a new session with a generated ID.
     *
     * @return a new HazelcastSession
     */
    public static HazelcastSession create() {
        HazelcastSession session = new HazelcastSession();
        session.id = UUID.randomUUID().toString();
        session.originalId = session.id;
        session.creationTime = System.currentTimeMillis();
        session.lastAccessedTime = session.creationTime;
        session.changed = true;
        return session;
    }

    /**
     * Sets the change callback that will be invoked when the session is modified.
     *
     * @param callback the callback to invoke on changes
     */
    public void setChangeCallback(Runnable callback) {
        this.changeCallback = callback;
    }

    private void markChanged() {
        this.changed = true;
        if (changeCallback != null) {
            changeCallback.run();
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the original session ID before any changes.
     *
     * @return the original session ID
     */
    public String getOriginalId() {
        return this.originalId;
    }

    public void setOriginalId(String originalId) {
        this.originalId = originalId;
    }

    @Override
    public String changeSessionId() {
        String newId = UUID.randomUUID().toString();
        this.id = newId;
        markChanged();
        return newId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attributeName) {
        Object value = this.attributes.get(attributeName);
        if (value == null) {
            return null;
        }
        // Wrap collections with change tracking proxy
        return (T) ChangeTrackingProxy.wrap(value, this::markChanged);
    }

    /**
     * Gets the raw attributes map for serialization.
     *
     * @return the attributes map
     */
    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    /**
     * Sets the attributes map (used during deserialization).
     *
     * @param attributes the attributes map
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    @Override
    @JsonIgnore
    public Set<String> getAttributeNames() {
        return this.attributes.keySet();
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
        if (attributeValue == null) {
            removeAttribute(attributeName);
        } else {
            // Unwrap if it's a tracked collection to store the original
            Object valueToStore = ChangeTrackingProxy.unwrap(attributeValue);
            this.attributes.put(attributeName, valueToStore);
            markChanged();
        }
    }

    @Override
    public void removeAttribute(String attributeName) {
        Object removed = this.attributes.remove(attributeName);
        if (removed != null) {
            markChanged();
        }
    }

    @Override
    @JsonIgnore
    public Instant getCreationTime() {
        return Instant.ofEpochMilli(this.creationTime);
    }

    public long getCreationTimeMillis() {
        return this.creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime.toEpochMilli();
        markChanged();
    }

    @Override
    @JsonIgnore
    public Instant getLastAccessedTime() {
        return Instant.ofEpochMilli(this.lastAccessedTime);
    }

    public long getLastAccessedTimeMillis() {
        return this.lastAccessedTime;
    }

    public void setLastAccessedTimeMillis(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    @Override
    public void setMaxInactiveInterval(Duration interval) {
        this.maxInactiveIntervalSeconds = (int) interval.getSeconds();
        markChanged();
    }

    @Override
    @JsonIgnore
    public Duration getMaxInactiveInterval() {
        return Duration.ofSeconds(this.maxInactiveIntervalSeconds);
    }

    public int getMaxInactiveIntervalSeconds() {
        return this.maxInactiveIntervalSeconds;
    }

    public void setMaxInactiveIntervalSeconds(int maxInactiveIntervalSeconds) {
        this.maxInactiveIntervalSeconds = maxInactiveIntervalSeconds;
    }

    @Override
    @JsonIgnore
    public boolean isExpired() {
        if (this.maxInactiveIntervalSeconds < 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long expiryTime = this.lastAccessedTime + (this.maxInactiveIntervalSeconds * 1000L);
        return now > expiryTime;
    }

    /**
     * Checks if the session has changed since last save.
     *
     * @return true if changed
     */
    @JsonIgnore
    public boolean isChanged() {
        return this.changed;
    }

    /**
     * Clears the change flag after saving.
     */
    public void clearChanged() {
        this.changed = false;
    }

    /**
     * Marks the session as changed.
     */
    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    /**
     * Resets the original ID to the current ID after a successful save with a new ID.
     */
    public void resetOriginalId() {
        this.originalId = this.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HazelcastSession that = (HazelcastSession) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "HazelcastSession{" +
                "id='" + id + '\'' +
                ", creationTime=" + creationTime +
                ", lastAccessedTime=" + lastAccessedTime +
                ", maxInactiveIntervalSeconds=" + maxInactiveIntervalSeconds +
                ", attributeCount=" + attributes.size() +
                ", changed=" + changed +
                '}';
    }
}
