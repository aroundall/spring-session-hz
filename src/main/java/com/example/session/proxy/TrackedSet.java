package com.example.session.proxy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * A Set wrapper that tracks modifications and invokes a callback when changes occur.
 * Recursively wraps nested collections for change tracking.
 *
 * @param <E> the type of elements in this set
 */
public class TrackedSet<E> implements Set<E> {

    private final Set<E> delegate;
    private final Runnable onChange;

    public TrackedSet(Set<E> delegate, Runnable onChange) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.onChange = Objects.requireNonNull(onChange, "onChange must not be null");
    }

    /**
     * Returns the underlying delegate set.
     */
    public Set<E> getDelegate() {
        return delegate;
    }

    private void markChanged() {
        onChange.run();
    }

    @SuppressWarnings("unchecked")
    private E wrapElement(E element) {
        return (E) ChangeTrackingProxy.wrap(element, onChange);
    }

    @SuppressWarnings("unchecked")
    private E unwrapElement(E element) {
        return (E) ChangeTrackingProxy.unwrapDelegate(element);
    }

    private Collection<? extends E> unwrapAll(Collection<? extends E> collection) {
        if (collection.isEmpty()) {
            return collection;
        }
        java.util.List<E> unwrapped = new ArrayList<>(collection.size());
        for (E element : collection) {
            unwrapped.add(unwrapElement(element));
        }
        return unwrapped;
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
    public Iterator<E> iterator() {
        return new TrackedIterator<>(delegate.iterator(), onChange);
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
    public boolean add(E e) {
        boolean added = delegate.add(unwrapElement(e));
        if (added) {
            markChanged();
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = delegate.remove(o);
        if (removed) {
            markChanged();
        }
        return removed;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = delegate.addAll(unwrapAll(c));
        if (changed) {
            markChanged();
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = delegate.retainAll(c);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = delegate.removeAll(c);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    @Override
    public void clear() {
        if (!delegate.isEmpty()) {
            markChanged();
        }
        delegate.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof TrackedSet<?> other) {
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
     * Iterator wrapper that tracks remove operations.
     */
    private static class TrackedIterator<E> implements Iterator<E> {
        private final Iterator<E> delegate;
        private final Runnable onChange;

        TrackedIterator(Iterator<E> delegate, Runnable onChange) {
            this.delegate = delegate;
            this.onChange = onChange;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            E element = delegate.next();
            return (E) ChangeTrackingProxy.wrap(element, onChange);
        }

        @Override
        public void remove() {
            onChange.run();
            delegate.remove();
        }
    }
}

