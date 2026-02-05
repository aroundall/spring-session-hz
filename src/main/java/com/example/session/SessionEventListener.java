package com.example.session;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.Session;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import java.util.UUID;

/**
 * Listens to Hazelcast IMap entry events and publishes corresponding Spring Session events.
 */
public class SessionEventListener implements
        EntryAddedListener<String, HazelcastSession>,
        EntryRemovedListener<String, HazelcastSession>,
        EntryEvictedListener<String, HazelcastSession>,
        EntryExpiredListener<String, HazelcastSession> {

    private static final Logger logger = LoggerFactory.getLogger(SessionEventListener.class);

    private final HazelcastSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private UUID listenerRegistrationId;

    /**
     * Creates a new SessionEventListener.
     *
     * @param sessionRepository the session repository
     * @param eventPublisher    the Spring application event publisher
     */
    public SessionEventListener(HazelcastSessionRepository sessionRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registers this listener with the Hazelcast IMap after bean initialization.
     */
    @PostConstruct
    public void init() {
        IMap<String, HazelcastSession> sessionsMap = sessionRepository.getSessionsMap();
        // Register listener with includeValue=true to receive session data in events
        this.listenerRegistrationId = sessionsMap.addEntryListener(this, true);
        logger.info("Registered Hazelcast session event listener");
    }

    /**
     * Removes this listener from the Hazelcast IMap before bean destruction.
     */
    @PreDestroy
    public void destroy() {
        if (this.listenerRegistrationId != null) {
            try {
                IMap<String, HazelcastSession> sessionsMap = sessionRepository.getSessionsMap();
                sessionsMap.removeEntryListener(this.listenerRegistrationId);
                logger.info("Unregistered Hazelcast session event listener");
            } catch (Exception e) {
                logger.warn("Failed to unregister session event listener", e);
            }
        }
    }

    @Override
    public void entryAdded(EntryEvent<String, HazelcastSession> event) {
        String sessionId = event.getKey();
        HazelcastSession session = event.getValue();
        logger.debug("Session created: {}", sessionId);
        publishSessionCreatedEvent(session);
    }

    @Override
    public void entryRemoved(EntryEvent<String, HazelcastSession> event) {
        String sessionId = event.getKey();
        HazelcastSession session = event.getOldValue();
        logger.debug("Session removed: {}", sessionId);
        publishSessionDeletedEvent(session != null ? session : createDummySession(sessionId));
    }

    @Override
    public void entryEvicted(EntryEvent<String, HazelcastSession> event) {
        String sessionId = event.getKey();
        HazelcastSession session = event.getOldValue();
        logger.debug("Session evicted: {}", sessionId);
        publishSessionExpiredEvent(session != null ? session : createDummySession(sessionId));
    }

    @Override
    public void entryExpired(EntryEvent<String, HazelcastSession> event) {
        String sessionId = event.getKey();
        HazelcastSession session = event.getOldValue();
        logger.debug("Session expired: {}", sessionId);
        publishSessionExpiredEvent(session != null ? session : createDummySession(sessionId));
    }

    /**
     * Creates a minimal session object for events when the actual session is not available.
     */
    private HazelcastSession createDummySession(String sessionId) {
        HazelcastSession session = new HazelcastSession();
        session.setId(sessionId);
        session.setOriginalId(sessionId);
        return session;
    }

    private void publishSessionCreatedEvent(Session session) {
        try {
            eventPublisher.publishEvent(new SessionCreatedEvent(this, session));
        } catch (Exception e) {
            logger.error("Failed to publish SessionCreatedEvent for session: {}", session.getId(), e);
        }
    }

    private void publishSessionDeletedEvent(Session session) {
        try {
            eventPublisher.publishEvent(new SessionDeletedEvent(this, session));
        } catch (Exception e) {
            logger.error("Failed to publish SessionDeletedEvent for session: {}", session.getId(), e);
        }
    }

    private void publishSessionExpiredEvent(Session session) {
        try {
            eventPublisher.publishEvent(new SessionExpiredEvent(this, session));
        } catch (Exception e) {
            logger.error("Failed to publish SessionExpiredEvent for session: {}", session.getId(), e);
        }
    }
}
