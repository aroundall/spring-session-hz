package com.example.session.proxy;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

/**
 * A List wrapper that tracks modifications and invokes a callback when changes occur.
 * Recursively wraps nested collections for change tracking.
 *
 * @param <E> the type of elements in this list
 */
public class TrackedList<E> implements List<E> {

    private final List<E> delegate;
    private final Runnable onChange;

    public TrackedList(List<E> delegate, Runnable onChange) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.onChange = Objects.requireNonNull(onChange, "onChange must not be null");
    }

    /**
     * Returns the underlying delegate list.
     */
    public List<E> getDelegate() {
        return delegate;
    }

    private void markChanged() {
        onChange.run();
    }

    @SuppressWarnings("unchecked")
    private E wrapElement(E element) {
        return (E) ChangeTrackingProxy.wrap(element, onChange);
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
        markChanged();
        return delegate.add(e);
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
        if (c.isEmpty()) {
            return false;
        }
        markChanged();
        return delegate.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        if (c.isEmpty()) {
            return false;
        }
        markChanged();
        return delegate.addAll(index, c);
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
    public boolean retainAll(Collection<?> c) {
        boolean changed = delegate.retainAll(c);
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
    public E get(int index) {
        E element = delegate.get(index);
        return wrapElement(element);
    }

    @Override
    public E set(int index, E element) {
        markChanged();
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, E element) {
        markChanged();
        delegate.add(index, element);
    }

    @Override
    public E remove(int index) {
        markChanged();
        return delegate.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return delegate.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return new TrackedListIterator<>(delegate.listIterator(), onChange);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new TrackedListIterator<>(delegate.listIterator(index), onChange);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return new TrackedList<>(delegate.subList(fromIndex, toIndex), onChange);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof TrackedList<?> other) {
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

    /**
     * ListIterator wrapper that tracks modifications.
     */
    private static class TrackedListIterator<E> implements ListIterator<E> {
        private final ListIterator<E> delegate;
        private final Runnable onChange;

        TrackedListIterator(ListIterator<E> delegate, Runnable onChange) {
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
        public boolean hasPrevious() {
            return delegate.hasPrevious();
        }

        @Override
        @SuppressWarnings("unchecked")
        public E previous() {
            E element = delegate.previous();
            return (E) ChangeTrackingProxy.wrap(element, onChange);
        }

        @Override
        public int nextIndex() {
            return delegate.nextIndex();
        }

        @Override
        public int previousIndex() {
            return delegate.previousIndex();
        }

        @Override
        public void remove() {
            onChange.run();
            delegate.remove();
        }

        @Override
        public void set(E e) {
            onChange.run();
            delegate.set(e);
        }

        @Override
        public void add(E e) {
            onChange.run();
            delegate.add(e);
        }
    }
}
