package com.example.session;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class ChangeTrackingSessionRepository implements FindByIndexNameSessionRepository<Session>, DisposableBean {

    private final FindByIndexNameSessionRepository<Session> delegate;
    private final DisposableBean destroyDelegate;

    ChangeTrackingSessionRepository(FindByIndexNameSessionRepository<Session> delegate, DisposableBean destroyDelegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.destroyDelegate = Objects.requireNonNull(destroyDelegate, "destroyDelegate must not be null");
    }

    @Override
    public Session createSession() {
        Session session = delegate.createSession();
        return new ChangeTrackingSession(session);
    }

    @Override
    public void save(Session session) {
        delegate.save(unwrap(session));
    }

    @Override
    public Session findById(String id) {
        Session session = delegate.findById(id);
        if (session == null) {
            return null;
        }
        return new ChangeTrackingSession(session);
    }

    @Override
    public void deleteById(String id) {
        delegate.deleteById(id);
    }

    @Override
    public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        Map<String, Session> found = delegate.findByIndexNameAndIndexValue(indexName, indexValue);
        if (found == null || found.isEmpty()) {
            return Map.of();
        }

        Map<String, Session> wrapped = new HashMap<>(found.size());
        for (Map.Entry<String, Session> entry : found.entrySet()) {
            wrapped.put(entry.getKey(), new ChangeTrackingSession(entry.getValue()));
        }
        return wrapped;
    }

    @Override
    public void destroy() throws Exception {
        destroyDelegate.destroy();
    }

    private static Session unwrap(Session session) {
        if (session instanceof ChangeTrackingSession tracked) {
            return tracked.getDelegate();
        }
        return session;
    }
}

