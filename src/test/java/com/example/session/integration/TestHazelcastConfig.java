package com.example.session.integration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.session.hazelcast.config.annotation.SpringSessionHazelcastInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

/**
 * Test configuration that creates both an embedded Hazelcast server and a client.
 * The server is managed as a Spring bean to ensure proper lifecycle management.
 */
@Configuration
public class TestHazelcastConfig {

    private static final Logger logger = LoggerFactory.getLogger(TestHazelcastConfig.class);

    private final String clusterName = "test-" + UUID.randomUUID();

    private Integer hazelcastPort;

    private HazelcastInstance embeddedServer;

    /**
     * Creates an embedded Hazelcast server for testing.
     * This bean is created first and destroyed last.
     */
    @Bean(name = "embeddedHazelcastServer")
    public HazelcastInstance embeddedHazelcastServer() {
        if (hazelcastPort == null) {
            hazelcastPort = findAvailablePort();
        }

        Config config = new Config();
        config.setClusterName(clusterName);
        config.setInstanceName("test-server");

        // Configure network
        config.getNetworkConfig().setPort(hazelcastPort);
        config.getNetworkConfig().setPortAutoIncrement(false);

        // Disable multicast, use TCP/IP only
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig()
                .setEnabled(true)
                .addMember("127.0.0.1");

        // Disable phone home
        config.setProperty("hazelcast.phone.home.enabled", "false");

        // Faster startup for testing
        config.setProperty("hazelcast.wait.seconds.before.join", "0");
        config.setProperty("hazelcast.max.wait.seconds.before.join", "0");

        logger.info("Starting embedded Hazelcast server on port {}", hazelcastPort);
        embeddedServer = Hazelcast.newHazelcastInstance(config);
        logger.info("Embedded Hazelcast server started successfully");

        return embeddedServer;
    }

    /**
     * Creates a Hazelcast client that connects to the embedded server.
     * This is the bean used by the session repository.
     */
    @Bean(name = "hazelcastInstance", destroyMethod = "shutdown")
    @Primary
    @SpringSessionHazelcastInstance
    public HazelcastInstance hazelcastClient(
            HazelcastInstance embeddedHazelcastServer) {

        if (hazelcastPort == null) {
            throw new IllegalStateException("Hazelcast port not initialized");
        }

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(clusterName);
        clientConfig.setInstanceName("test-client");

        // Configure network - connect to local embedded server
        clientConfig.getNetworkConfig().addAddress("127.0.0.1:" + hazelcastPort);

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

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to find an available port for Hazelcast", e);
        }
    }
}
