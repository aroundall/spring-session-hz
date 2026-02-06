# Spring Session Hazelcast Starter - Requirements

## 1. Project Goal

Build a Spring Boot Starter library for distributed HTTP session management, using Hazelcast as the session store. Other microservices can enable distributed session management by simply adding this library as a Maven dependency, without any additional code or configuration.

## 2. Technology Constraints

- Spring Boot 3.x
- Hazelcast, Client mode
- Use `spring-session-hazelcast` for session storage (serialization is Hazelcast-native by default, and can be swapped via Hazelcast serialization configuration if needed)
- Maven build, packaged as a JAR dependency

## 3. Session Management

- Cookie name: **`USESSIONID`**
- Session timeout: fixed **30 minutes**
- When a request carries a session ID, the library loads the corresponding session from Hazelcast; if the session does not exist or has expired, treat it as no session.
- When session data is modified, it is written back to Hazelcast.

## 4. Hazelcast Connection

- Connect to Hazelcast via environment variables:
  - `HZ_URL` - server address (default: `localhost:5701`)
  - `HZ_USERNAME` - username (optional)
  - `HZ_PASSWORD` - password (optional)

## 5. Collection Modification Tracking

- When session attributes are collections (`List`, `Map`, `Set`), modifications to those collections (including nested collections) should be automatically detected and trigger a session save, without the application needing to call `setAttribute` again.

## 6. Session Events

- Support session lifecycle events: **created**, **expired**, **deleted**.
- Consuming applications can subscribe to these events via Spring's event mechanism.

## 7. Auto-Configuration

- When the library is on the classpath of a Spring Boot web application, all session management functionality is activated automatically.
- Consuming applications can override any component if needed.

## 8. Integration Tests

- Use an embedded Hazelcast server for test isolation.
- Build a sample Spring Boot app with Spring Security containing:
  - `POST /logon` (public): accepts username/password, creates a session.
  - `GET /do-trans` (requires session): returns session-based data.
- Use REST Assured as the HTTP test client.
- Test cases:
  1. Login and receive session cookie
  2. Access protected endpoint with valid session
  3. 401 when accessing protected endpoint without session
  4. 401 when accessing protected endpoint with invalid session ID
  5. Session data persists across multiple requests
  6. Multiple concurrent sessions work independently
