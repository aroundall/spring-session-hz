package com.example.session.proxy;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A Map wrapper that tracks modifications and invokes a callback when changes occur.
 * Recursively wraps nested collections for change tracking.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class TrackedMap<K, V> implements Map<K, V> {

    private final Map<K, V> delegate;
    private final Runnable onChange;

    public TrackedMap(Map<K, V> delegate, Runnable onChange) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.onChange = Objects.requireNonNull(onChange, "onChange must not be null");
    }

    /**
     * Returns the underlying delegate map.
     */
    public Map<K, V> getDelegate() {
        return delegate;
    }

    private void markChanged() {
        onChange.run();
    }

    @SuppressWarnings("unchecked")
    private V wrapValue(V value) {
        return (V) ChangeTrackingProxy.wrap(value, onChange);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        V value = delegate.get(key);
        return wrapValue(value);
    }

    @Override
    public V put(K key, V value) {
        markChanged();
        return delegate.put(key, value);
    }

    @Override
    public V remove(Object key) {
        V removed = delegate.remove(key);
        if (removed != null) {
            markChanged();
        }
        return removed;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        if (!m.isEmpty()) {
            markChanged();
        }
        delegate.putAll(m);
    }

    @Override
    public void clear() {
        if (!delegate.isEmpty()) {
            markChanged();
        }
        delegate.clear();
    }

    @Override
    public Set<K> keySet() {
        return new TrackedSet<>(delegate.keySet(), onChange);
    }

    @Override
    public Collection<V> values() {
        // Return a tracked collection that wraps values
        return new TrackedValues<>(delegate.values(), onChange);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new TrackedEntrySet<>(delegate.entrySet(), onChange);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        V value = delegate.getOrDefault(key, defaultValue);
        return wrapValue(value);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach((k, v) -> action.accept(k, wrapValue(v)));
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        markChanged();
        delegate.replaceAll(function);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V existing = delegate.get(key);
        if (existing == null) {
            markChanged();
            return delegate.put(key, value);
        }
        return wrapValue(existing);
    }

    @Override
    public boolean remove(Object key, Object value) {
        boolean removed = delegate.remove(key, value);
        if (removed) {
            markChanged();
        }
        return removed;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        boolean replaced = delegate.replace(key, oldValue, newValue);
        if (replaced) {
            markChanged();
        }
        return replaced;
    }

    @Override
    public V replace(K key, V value) {
        V existing = delegate.get(key);
        if (existing != null) {
            markChanged();
            return delegate.replace(key, value);
        }
        return null;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V existing = delegate.get(key);
        if (existing == null) {
            V newValue = mappingFunction.apply(key);
            if (newValue != null) {
                markChanged();
                delegate.put(key, newValue);
                return wrapValue(newValue);
            }
        }
        return wrapValue(existing);
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V existing = delegate.get(key);
        if (existing != null) {
            V newValue = remappingFunction.apply(key, existing);
            markChanged();
            if (newValue != null) {
                delegate.put(key, newValue);
            } else {
                delegate.remove(key);
            }
            return wrapValue(newValue);
        }
        return null;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V newValue = remappingFunction.apply(key, delegate.get(key));
        markChanged();
        if (newValue != null) {
            delegate.put(key, newValue);
        } else {
            delegate.remove(key);
        }
        return wrapValue(newValue);
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        markChanged();
        V newValue = delegate.merge(key, value, remappingFunction);
        return wrapValue(newValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof TrackedMap<?, ?> other) {
            return delegate.equals(other.delegate);
        }
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    /**
     * Tracked collection for map values.
     */
    private static class TrackedValues<V> implements Collection<V> {
        private final Collection<V> delegate;
        private final Runnable onChange;

        TrackedValues(Collection<V> delegate, Runnable onChange) {
            this.delegate = delegate;
            this.onChange = onChange;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public java.util.Iterator<V> iterator() {
            return new java.util.Iterator<>() {
                private final java.util.Iterator<V> it = delegate.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                @SuppressWarnings("unchecked")
                public V next() {
                    return (V) ChangeTrackingProxy.wrap(it.next(), onChange);
                }

                @Override
                public void remove() {
                    onChange.run();
                    it.remove();
                }
            };
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(V v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            boolean removed = delegate.remove(o);
            if (removed) {
                onChange.run();
            }
            return removed;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean changed = delegate.removeAll(c);
            if (changed) {
                onChange.run();
            }
            return changed;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean changed = delegate.retainAll(c);
            if (changed) {
                onChange.run();
            }
            return changed;
        }

        @Override
        public void clear() {
            if (!delegate.isEmpty()) {
                onChange.run();
            }
            delegate.clear();
        }
    }

    /**
     * Tracked entry set.
     */
    private static class TrackedEntrySet<K, V> implements Set<Entry<K, V>> {
        private final Set<Entry<K, V>> delegate;
        private final Runnable onChange;

        TrackedEntrySet(Set<Entry<K, V>> delegate, Runnable onChange) {
            this.delegate = delegate;
            this.onChange = onChange;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public java.util.Iterator<Entry<K, V>> iterator() {
            return new java.util.Iterator<>() {
                private final java.util.Iterator<Entry<K, V>> it = delegate.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    Entry<K, V> entry = it.next();
                    return new TrackedEntry<>(entry, onChange);
                }

                @Override
                public void remove() {
                    onChange.run();
                    it.remove();
                }
            };
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(Entry<K, V> kvEntry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            boolean removed = delegate.remove(o);
            if (removed) {
                onChange.run();
            }
            return removed;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Entry<K, V>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean changed = delegate.retainAll(c);
            if (changed) {
                onChange.run();
            }
            return changed;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean changed = delegate.removeAll(c);
            if (changed) {
                onChange.run();
            }
            return changed;
        }

        @Override
        public void clear() {
            if (!delegate.isEmpty()) {
                onChange.run();
            }
            delegate.clear();
        }
    }

    /**
     * Tracked map entry.
     */
    private static class TrackedEntry<K, V> implements Entry<K, V> {
        private final Entry<K, V> delegate;
        private final Runnable onChange;

        TrackedEntry(Entry<K, V> delegate, Runnable onChange) {
            this.delegate = delegate;
            this.onChange = onChange;
        }

        @Override
        public K getKey() {
            return delegate.getKey();
        }

        @Override
        @SuppressWarnings("unchecked")
        public V getValue() {
            return (V) ChangeTrackingProxy.wrap(delegate.getValue(), onChange);
        }

        @Override
        public V setValue(V value) {
            onChange.run();
            return delegate.setValue(value);
        }

        @Override
        public boolean equals(Object o) {
            return delegate.equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }
}
