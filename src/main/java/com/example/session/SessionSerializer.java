package com.example.session;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;

/**
 * Hazelcast StreamSerializer for HazelcastSession using Jackson.
 * Enables polymorphic type handling to preserve object types during serialization.
 */
public class SessionSerializer implements StreamSerializer<HazelcastSession> {

    private static final int TYPE_ID = 1;

    private final ObjectMapper objectMapper;

    public SessionSerializer() {
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates and configures the ObjectMapper with type information enabled.
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 time module
        mapper.registerModule(new JavaTimeModule());

        // Configure serialization
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Configure deserialization
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Enable polymorphic type handling
        // This writes @class information for non-final types
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }

    /**
     * Returns the ObjectMapper used for serialization.
     * Can be used to customize serialization settings.
     *
     * @return the ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void write(ObjectDataOutput out, HazelcastSession session) throws IOException {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(session);
            out.writeByteArray(bytes);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to serialize session: " + session.getId(), e);
        }
    }

    @Override
    public HazelcastSession read(ObjectDataInput in) throws IOException {
        try {
            byte[] bytes = in.readByteArray();
            return objectMapper.readValue(bytes, HazelcastSession.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to deserialize session", e);
        }
    }

    @Override
    public void destroy() {
        // No resources to clean up
    }
}
