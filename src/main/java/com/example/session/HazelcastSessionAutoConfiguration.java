package com.example.session;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Auto-configuration for Hazelcast-based HTTP session management.
 *
 * <p>This configuration is automatically activated when:
 * <ul>
 *   <li>Hazelcast is on the classpath</li>
 *   <li>Spring Session is on the classpath</li>
 *   <li>The application is a web application</li>
 * </ul>
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code HZ_URL} - Hazelcast server address (e.g., "localhost:5701")</li>
 *   <li>{@code HZ_USERNAME} - Hazelcast username for authentication</li>
 *   <li>{@code HZ_PASSWORD} - Hazelcast password for authentication</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass({HazelcastInstance.class, Session.class, SessionRepositoryFilter.class})
@EnableSpringHttpSession
public class HazelcastSessionAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastSessionAutoConfiguration.class);

    /**
     * Session cookie name.
     */
    public static final String SESSION_COOKIE_NAME = "USESSIONID";

    /**
     * Default Hazelcast cluster name.
     */
    public static final String DEFAULT_CLUSTER_NAME = "dev";

    /**
     * Default Hazelcast address.
     */
    public static final String DEFAULT_HZ_URL = "localhost:5701";

    /**
     * Creates and configures the CookieSerializer with custom session cookie name.
     *
     * @return the cookie serializer
     */
    @Bean
    @ConditionalOnMissingBean(CookieSerializer.class)
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(false); // Set to true in production with HTTPS
        logger.info("Configured session cookie name: {}", SESSION_COOKIE_NAME);
        return serializer;
    }

    /**
     * Creates the SessionSerializer for Hazelcast.
     *
     * @return the session serializer
     */
    @Bean
    @ConditionalOnMissingBean(SessionSerializer.class)
    public SessionSerializer sessionSerializer() {
        return new SessionSerializer();
    }

    /**
     * Creates a Hazelcast client instance configured from environment variables.
     *
     * @param sessionSerializer the session serializer
     * @return the Hazelcast instance
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(HazelcastInstance.class)
    public HazelcastInstance hazelcastInstance(SessionSerializer sessionSerializer) {
        String hzUrl = getEnvOrDefault("HZ_URL", DEFAULT_HZ_URL);
        String hzUsername = System.getenv("HZ_USERNAME");
        String hzPassword = System.getenv("HZ_PASSWORD");

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(DEFAULT_CLUSTER_NAME);

        // Configure network
        clientConfig.getNetworkConfig().addAddress(hzUrl);

        // Configure authentication if credentials are provided
        if (hzUsername != null && hzPassword != null) {
            clientConfig.getSecurityConfig()
                    .setUsernamePasswordIdentityConfig(hzUsername, hzPassword);
            logger.info("Hazelcast authentication configured for user: {}", hzUsername);
        }

        // Register custom serializer for HazelcastSession
        SerializerConfig serializerConfig = new SerializerConfig()
                .setImplementation(sessionSerializer)
                .setTypeClass(HazelcastSession.class);
        clientConfig.getSerializationConfig().addSerializerConfig(serializerConfig);

        logger.info("Creating Hazelcast client connecting to: {}", hzUrl);
        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    /**
     * Creates the session repository backed by Hazelcast.
     *
     * @param hazelcastInstance the Hazelcast instance
     * @return the session repository
     */
    @Bean
    @ConditionalOnMissingBean(SessionRepository.class)
    public HazelcastSessionRepository sessionRepository(HazelcastInstance hazelcastInstance) {
        return new HazelcastSessionRepository(hazelcastInstance);
    }

    /**
     * Creates the session event listener.
     *
     * @param sessionRepository the session repository
     * @param eventPublisher    the application event publisher
     * @return the session event listener
     */
    @Bean
    public SessionEventListener sessionEventListener(HazelcastSessionRepository sessionRepository,
                                                      ApplicationEventPublisher eventPublisher) {
        return new SessionEventListener(sessionRepository, eventPublisher);
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
