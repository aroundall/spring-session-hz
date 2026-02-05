package com.example.session;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.SessionRepository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A SessionRepository implementation that stores sessions in Hazelcast.
 */
public class HazelcastSessionRepository implements SessionRepository<HazelcastSession> {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastSessionRepository.class);

    /**
     * Default session map name in Hazelcast.
     */
    public static final String DEFAULT_MAP_NAME = "spring:session:sessions";

    /**
     * Default session timeout: 30 minutes.
     */
    public static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final IMap<String, HazelcastSession> sessions;
    private final Duration sessionTimeout;

    /**
     * Creates a new HazelcastSessionRepository.
     *
     * @param hazelcastInstance the Hazelcast instance
     */
    public HazelcastSessionRepository(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, DEFAULT_MAP_NAME, DEFAULT_SESSION_TIMEOUT);
    }

    /**
     * Creates a new HazelcastSessionRepository with custom map name.
     *
     * @param hazelcastInstance the Hazelcast instance
     * @param mapName           the name of the IMap to use
     * @param sessionTimeout    the session timeout duration
     */
    public HazelcastSessionRepository(HazelcastInstance hazelcastInstance, String mapName, Duration sessionTimeout) {
        this.sessions = hazelcastInstance.getMap(mapName);
        this.sessionTimeout = sessionTimeout;
        logger.info("HazelcastSessionRepository initialized with map: {}, timeout: {}", mapName, sessionTimeout);
    }

    /**
     * Gets the underlying Hazelcast IMap.
     *
     * @return the sessions map
     */
    public IMap<String, HazelcastSession> getSessionsMap() {
        return this.sessions;
    }

    @Override
    public HazelcastSession createSession() {
        HazelcastSession session = HazelcastSession.create();
        session.setMaxInactiveInterval(this.sessionTimeout);
        logger.debug("Created new session: {}", session.getId());
        return session;
    }

    @Override
    public void save(HazelcastSession session) {
        String sessionId = session.getId();
        String originalId = session.getOriginalId();

        // Handle session ID change
        if (!sessionId.equals(originalId)) {
            logger.debug("Session ID changed from {} to {}", originalId, sessionId);
            this.sessions.remove(originalId);
            session.resetOriginalId();
        }

        // Calculate TTL in seconds
        long ttlSeconds = session.getMaxInactiveIntervalSeconds();

        // Save to Hazelcast with TTL
        if (ttlSeconds > 0) {
            this.sessions.set(sessionId, session, ttlSeconds, TimeUnit.SECONDS);
        } else {
            this.sessions.set(sessionId, session);
        }

        session.clearChanged();
        logger.debug("Saved session: {} with TTL: {}s", sessionId, ttlSeconds);
    }

    @Override
    public HazelcastSession findById(String id) {
        if (id == null) {
            return null;
        }

        HazelcastSession session = this.sessions.get(id);
        if (session == null) {
            logger.debug("Session not found: {}", id);
            return null;
        }

        if (session.isExpired()) {
            logger.debug("Session expired: {}", id);
            deleteById(id);
            return null;
        }

        // Set up change callback for the loaded session
        session.setOriginalId(session.getId());
        session.clearChanged();

        logger.debug("Found session: {}", id);
        return session;
    }

    @Override
    public void deleteById(String id) {
        if (id == null) {
            return;
        }

        HazelcastSession removed = this.sessions.remove(id);
        if (removed != null) {
            logger.debug("Deleted session: {}", id);
        }
    }
}
