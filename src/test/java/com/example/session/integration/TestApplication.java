package com.example.session.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test Spring Boot application for integration testing.
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.session",
        "com.example.session.integration"
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
