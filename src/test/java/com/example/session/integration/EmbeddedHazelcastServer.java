package com.example.session.integration;

import com.example.session.HazelcastSession;
import com.example.session.SessionSerializer;
import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Hazelcast server for integration testing.
 * Starts a local Hazelcast member that the client can connect to.
 */
public class EmbeddedHazelcastServer {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedHazelcastServer.class);

    private HazelcastInstance hazelcastInstance;

    /**
     * Starts the embedded Hazelcast server.
     *
     * @param port the port to listen on
     */
    public void start(int port) {
        Config config = new Config();
        config.setClusterName("dev");

        // Configure network
        config.getNetworkConfig().setPort(port);
        config.getNetworkConfig().setPortAutoIncrement(false);

        // Disable multicast, use TCP/IP only
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);

        // Disable phone home
        config.setProperty("hazelcast.phone.home.enabled", "false");

        // Faster startup for testing
        config.setProperty("hazelcast.wait.seconds.before.join", "0");
        config.setProperty("hazelcast.max.wait.seconds.before.join", "0");

        // Register custom serializer for HazelcastSession (same as client)
        SerializerConfig serializerConfig = new SerializerConfig()
                .setImplementation(new SessionSerializer())
                .setTypeClass(HazelcastSession.class);
        config.getSerializationConfig().addSerializerConfig(serializerConfig);

        logger.info("Starting embedded Hazelcast server on port {}", port);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        logger.info("Embedded Hazelcast server started successfully");
    }

    /**
     * Stops the embedded Hazelcast server.
     */
    public void stop() {
        if (hazelcastInstance != null) {
            logger.info("Stopping embedded Hazelcast server");
            hazelcastInstance.shutdown();
            hazelcastInstance = null;
            logger.info("Embedded Hazelcast server stopped");
        }
    }

    /**
     * Gets the Hazelcast instance.
     *
     * @return the Hazelcast instance
     */
    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }
}
