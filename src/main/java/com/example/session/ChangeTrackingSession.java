package com.example.session;

import com.example.session.proxy.ChangeTrackingProxy;
import org.springframework.session.Session;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

final class ChangeTrackingSession implements Session {

    private final Session delegate;

    ChangeTrackingSession(Session delegate) {
        this.delegate = delegate;
    }

    Session getDelegate() {
        return delegate;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String changeSessionId() {
        return delegate.changeSessionId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attributeName) {
        T value = delegate.getAttribute(attributeName);
        if (value == null) {
            return null;
        }

        Object storedValue = ChangeTrackingProxy.unwrapDelegate(value);
        Runnable onChange = () -> delegate.setAttribute(attributeName, storedValue);
        return (T) ChangeTrackingProxy.wrap(storedValue, onChange);
    }

    @Override
    public Set<String> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
        delegate.setAttribute(attributeName, ChangeTrackingProxy.unwrapDelegate(attributeValue));
    }

    @Override
    public void removeAttribute(String attributeName) {
        delegate.removeAttribute(attributeName);
    }

    @Override
    public Instant getCreationTime() {
        return delegate.getCreationTime();
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
        delegate.setLastAccessedTime(lastAccessedTime);
    }

    @Override
    public Instant getLastAccessedTime() {
        return delegate.getLastAccessedTime();
    }

    @Override
    public void setMaxInactiveInterval(Duration interval) {
        delegate.setMaxInactiveInterval(interval);
    }

    @Override
    public Duration getMaxInactiveInterval() {
        return delegate.getMaxInactiveInterval();
    }

    @Override
    public boolean isExpired() {
        return delegate.isExpired();
    }
}
