package com.example.session.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the Hazelcast Session library.
 *
 * This test:
 * 1. Starts an embedded Hazelcast server (via TestHazelcastConfig)
 * 2. Starts a Spring Boot application using our session library
 * 3. Tests login and session-based access using REST Assured
 */
@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "hazelcast.test.port=5799"  // Use non-default port to avoid conflicts
        }
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }

    @Test
    @Order(1)
    void shouldLoginSuccessfullyAndReceiveSessionCookie() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "username": "testuser",
                            "password": "testpass"
                        }
                        """)
        .when()
                .post("/logon")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("success"))
                .body("username", equalTo("testuser"))
                .body("sessionId", notNullValue())
                .cookie("USESSIONID", notNullValue());
    }

    @Test
    @Order(2)
    void shouldAccessDoTransWithValidSession() {
        // First, login to get a session
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "username": "john_doe",
                            "password": "secret123"
                        }
                        """)
        .when()
                .post("/logon")
        .then()
                .statusCode(200)
                .extract()
                .response();

        String sessionCookie = loginResponse.getCookie("USESSIONID");
        System.out.println("Session cookie: " + sessionCookie);

        // Now access /do-trans with the session cookie
        given()
                .cookie("USESSIONID", sessionCookie)
        .when()
                .get("/do-trans")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("success"))
                .body("message", containsString("john_doe"))
                .body("sessionId", notNullValue());
    }

    @Test
    @Order(3)
    void shouldReturn401WhenAccessingDoTransWithoutSession() {
        given()
        .when()
                .get("/do-trans")
        .then()
                .statusCode(401)
                .body("error", equalTo("Unauthorized"));
    }

    @Test
    @Order(4)
    void shouldReturn401WhenAccessingDoTransWithInvalidSession() {
        given()
                .cookie("USESSIONID", "invalid-session-id-12345")
        .when()
                .get("/do-trans")
        .then()
                .statusCode(401)
                .body("error", equalTo("Unauthorized"));
    }

    @Test
    @Order(5)
    void shouldPersistSessionDataAcrossRequests() {
        // Login
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "username": "persistent_user",
                            "password": "pass"
                        }
                        """)
        .when()
                .post("/logon")
        .then()
                .statusCode(200)
                .extract()
                .response();

        String sessionCookie = loginResponse.getCookie("USESSIONID");
        String firstSessionId = loginResponse.jsonPath().getString("sessionId");

        // First request to /do-trans
        Response firstTransResponse = given()
                .cookie("USESSIONID", sessionCookie)
        .when()
                .get("/do-trans")
        .then()
                .statusCode(200)
                .extract()
                .response();

        String sessionIdFromFirstTrans = firstTransResponse.jsonPath().getString("sessionId");

        // Second request to /do-trans - session should be the same
        Response secondTransResponse = given()
                .cookie("USESSIONID", sessionCookie)
        .when()
                .get("/do-trans")
        .then()
                .statusCode(200)
                .body("message", containsString("persistent_user"))
                .extract()
                .response();

        String sessionIdFromSecondTrans = secondTransResponse.jsonPath().getString("sessionId");

        // Verify session IDs are consistent
        assert firstSessionId.equals(sessionIdFromFirstTrans) :
                "Session ID mismatch between login and first transaction";
        assert firstSessionId.equals(sessionIdFromSecondTrans) :
                "Session ID mismatch between transactions";

        System.out.println("Session persisted successfully across requests. Session ID: " + firstSessionId);
    }

    @Test
    @Order(6)
    void shouldAllowMultipleConcurrentSessions() {
        // Login user 1
        Response user1Login = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "username": "user1",
                            "password": "pass1"
                        }
                        """)
        .when()
                .post("/logon")
        .then()
                .statusCode(200)
                .extract()
                .response();

        String user1Session = user1Login.getCookie("USESSIONID");

        // Login user 2
        Response user2Login = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "username": "user2",
                            "password": "pass2"
                        }
                        """)
        .when()
                .post("/logon")
        .then()
                .statusCode(200)
                .extract()
                .response();

        String user2Session = user2Login.getCookie("USESSIONID");

        // Verify sessions are different
        assert !user1Session.equals(user2Session) : "Sessions should be different for different users";

        // Both users should be able to access their own sessions
        given()
                .cookie("USESSIONID", user1Session)
        .when()
                .get("/do-trans")
        .then()
                .statusCode(200)
                .body("message", containsString("user1"));

        given()
                .cookie("USESSIONID", user2Session)
        .when()
                .get("/do-trans")
        .then()
                .statusCode(200)
                .body("message", containsString("user2"));

        System.out.println("Multiple concurrent sessions work correctly");
    }
}
