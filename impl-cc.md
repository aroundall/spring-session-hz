# Implementation Details (Spring Session Hazelcast)

This project is built on **Spring Session’s official Hazelcast module** (`spring-session-hazelcast`). This library is a **Spring Boot starter** that:

- Creates/configures a Hazelcast **client** from environment variables
- Enables Spring Session HTTP sessions automatically
- Sets cookie name to `USESSIONID`
- Forces a fixed 30-minute session timeout
- Configures `SaveMode.ALWAYS` so collection mutations are persisted without requiring an extra `setAttribute`

## Project Structure

```
spring-session-hz/
├── pom.xml
├── src/main/java/com/example/session/
│   └── HazelcastSessionAutoConfiguration.java
├── src/main/resources/META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── src/test/java/com/example/session/integration/
    ├── TestApplication.java
    ├── TestController.java
    ├── TestHazelcastConfig.java
    ├── TestSecurityConfig.java
    └── SessionIntegrationTest.java
```

## Dependencies (pom.xml)

Key dependencies:

- `spring-session-core`
- `spring-session-hazelcast`
- `hazelcast`

Test dependencies:

- `spring-boot-starter-test`
- `spring-boot-starter-security`
- `rest-assured`

## Core Components

### 1. HazelcastSessionAutoConfiguration

File: `src/main/java/com/example/session/HazelcastSessionAutoConfiguration.java`

Activated when:

- Servlet web application (`@ConditionalOnWebApplication(type = SERVLET)`)
- Hazelcast + Spring Session + HazelcastIndexedSessionRepository on classpath (`@ConditionalOnClass`)

Beans:

- `CookieSerializer`: sets cookie name to `USESSIONID`
- `HazelcastInstance` (client): built from `HZ_URL` / `HZ_USERNAME` / `HZ_PASSWORD`
  - Annotated with `@SpringSessionHazelcastInstance` so it won’t conflict with other Hazelcast usage
  - Created only if no existing `@SpringSessionHazelcastInstance` is provided
- `HazelcastIndexedSessionRepository`:
  - Injects Spring `ApplicationEventPublisher` so Spring Session events are published
  - Sets default timeout to **30 minutes**
  - Sets `SaveMode.ALWAYS`

### 2. spring-session-hazelcast repository + events

Spring Session’s `HazelcastIndexedSessionRepository`:

- Stores sessions in a Hazelcast `IMap` (default: `spring:session:sessions`)
- Uses Hazelcast entry listeners internally to detect session lifecycle changes
- Publishes Spring Session events (`SessionCreatedEvent`, `SessionDeletedEvent`, `SessionExpiredEvent`) when an `ApplicationEventPublisher` is set

### 3. Serialization

`spring-session-hazelcast` ships a `HazelcastSessionSerializer` (`StreamSerializer<MapSession>`; typeId `1453`) which is Hazelcast-native and delegates attribute value serialization to Hazelcast (`ObjectDataOutput#writeObject`).

To swap serialization later, configure Hazelcast serialization on both **client** and **member** sides.

## Integration Test Design

### TestHazelcastConfig

`src/test/java/com/example/session/integration/TestHazelcastConfig.java` starts:

- an embedded Hazelcast **member** (server)
- a Hazelcast **client** annotated with `@SpringSessionHazelcastInstance` (used by the starter/repository)

The test setup uses a **random port** and a **random cluster name** per run to avoid port conflicts and cross-test joining.

### SessionIntegrationTest

`src/test/java/com/example/session/integration/SessionIntegrationTest.java` verifies:

1. Login returns `USESSIONID` cookie
2. Access with valid session succeeds
3. Access without session returns 401
4. Access with invalid session id returns 401
5. Session persists across multiple requests
6. Multiple sessions are isolated

