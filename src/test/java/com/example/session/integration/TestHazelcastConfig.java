package com.example.session.integration;

import com.example.session.HazelcastSession;
import com.example.session.SessionSerializer;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that creates both an embedded Hazelcast server and a client.
 * The server is managed as a Spring bean to ensure proper lifecycle management.
 */
@Configuration
public class TestHazelcastConfig {

    private static final Logger logger = LoggerFactory.getLogger(TestHazelcastConfig.class);

    @Value("${hazelcast.test.port:5701}")
    private int hazelcastPort;

    private HazelcastInstance embeddedServer;

    /**
     * Creates an embedded Hazelcast server for testing.
     * This bean is created first and destroyed last.
     */
    @Bean(name = "embeddedHazelcastServer")
    public HazelcastInstance embeddedHazelcastServer(SessionSerializer sessionSerializer) {
        Config config = new Config();
        config.setClusterName("dev");
        config.setInstanceName("test-server");

        // Configure network
        config.getNetworkConfig().setPort(hazelcastPort);
        config.getNetworkConfig().setPortAutoIncrement(false);

        // Disable multicast, use TCP/IP only
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);

        // Disable phone home
        config.setProperty("hazelcast.phone.home.enabled", "false");

        // Faster startup for testing
        config.setProperty("hazelcast.wait.seconds.before.join", "0");
        config.setProperty("hazelcast.max.wait.seconds.before.join", "0");

        // Register custom serializer for HazelcastSession
        SerializerConfig serializerConfig = new SerializerConfig()
                .setImplementation(sessionSerializer)
                .setTypeClass(HazelcastSession.class);
        config.getSerializationConfig().addSerializerConfig(serializerConfig);

        logger.info("Starting embedded Hazelcast server on port {}", hazelcastPort);
        embeddedServer = Hazelcast.newHazelcastInstance(config);
        logger.info("Embedded Hazelcast server started successfully");

        return embeddedServer;
    }

    /**
     * Creates a Hazelcast client that connects to the embedded server.
     * This is the bean used by the session repository.
     */
    @Bean(name = "hazelcastInstance")
    @Primary
    public HazelcastInstance hazelcastClient(
            HazelcastInstance embeddedHazelcastServer,
            SessionSerializer sessionSerializer) {

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName("dev");
        clientConfig.setInstanceName("test-client");

        // Configure network - connect to local embedded server
        clientConfig.getNetworkConfig().addAddress("localhost:" + hazelcastPort);

        // Register custom serializer for HazelcastSession
        SerializerConfig serializerConfig = new SerializerConfig()
                .setImplementation(sessionSerializer)
                .setTypeClass(HazelcastSession.class);
        clientConfig.getSerializationConfig().addSerializerConfig(serializerConfig);

        // Faster connection for testing
        clientConfig.getConnectionStrategyConfig()
                .getConnectionRetryConfig()
                .setClusterConnectTimeoutMillis(10000);

        logger.info("Creating test Hazelcast client connecting to localhost:{}", hazelcastPort);
        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    @PreDestroy
    public void shutdown() {
        if (embeddedServer != null) {
            logger.info("Shutting down embedded Hazelcast server");
            embeddedServer.shutdown();
        }
    }
}
