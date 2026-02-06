package com.example.session.proxy;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating change-tracking proxy wrappers around collections.
 *
 * <p>When a modification is made to the wrapped collection, the {@code onChange} callback
 * is invoked.
 */
public final class ChangeTrackingProxy {

    private ChangeTrackingProxy() {
    }

    /**
     * Wraps an object with change tracking if it's a supported collection type.
     * Recursively wraps nested collections on access.
     *
     * @param obj      the object to wrap
     * @param onChange callback invoked when a modification is detected
     * @param <T>      the type of the object
     * @return the wrapped object, or the original if not a supported type
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(T obj, Runnable onChange) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof TrackedList || obj instanceof TrackedMap || obj instanceof TrackedSet) {
            // Already wrapped
            return obj;
        }

        if (obj instanceof List) {
            return (T) new TrackedList<>((List<Object>) obj, onChange);
        }

        if (obj instanceof Map) {
            return (T) new TrackedMap<>((Map<Object, Object>) obj, onChange);
        }

        if (obj instanceof Set) {
            return (T) new TrackedSet<>((Set<Object>) obj, onChange);
        }

        // Not a collection, return as-is
        return obj;
    }

    /**
     * Shallow-unwrap a tracked collection back to its original delegate.
     *
     * <p>This is used to avoid accidentally storing tracked wrapper instances inside
     * other collections (which would couple persisted session data to these wrapper
     * classes).
     */
    @SuppressWarnings("unchecked")
    public static <T> T unwrapDelegate(T obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof TrackedList<?> tracked) {
            return (T) tracked.getDelegate();
        }
        if (obj instanceof TrackedMap<?, ?> tracked) {
            return (T) tracked.getDelegate();
        }
        if (obj instanceof TrackedSet<?> tracked) {
            return (T) tracked.getDelegate();
        }

        return obj;
    }

    /**
     * Deep-unwrapping helper that converts tracked wrappers into plain JDK collections.
     *
     * <p>Primarily useful when a tracked wrapper instance might leak into a session attribute
     * via application code.
     *
     * @param obj the object to unwrap
     * @param <T> the type of the object
     * @return the unwrapped object
     */
    @SuppressWarnings("unchecked")
    public static <T> T unwrap(T obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof TrackedList<?> tracked) {
            List<Object> unwrapped = new java.util.ArrayList<>();
            for (Object item : tracked.getDelegate()) {
                unwrapped.add(unwrap(item));
            }
            return (T) unwrapped;
        }

        if (obj instanceof TrackedMap<?, ?> tracked) {
            Map<Object, Object> unwrapped = new java.util.HashMap<>();
            for (Map.Entry<?, ?> entry : tracked.getDelegate().entrySet()) {
                unwrapped.put(unwrap(entry.getKey()), unwrap(entry.getValue()));
            }
            return (T) unwrapped;
        }

        if (obj instanceof TrackedSet<?> tracked) {
            Set<Object> unwrapped = new java.util.HashSet<>();
            for (Object item : tracked.getDelegate()) {
                unwrapped.add(unwrap(item));
            }
            return (T) unwrapped;
        }

        return obj;
    }
}

