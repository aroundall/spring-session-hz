# Implementation Details

## Project Structure

```
spring-session-hz/
├── pom.xml
├── src/main/java/com/example/session/
│   ├── HazelcastSessionAutoConfiguration.java
│   ├── HazelcastSession.java
│   ├── HazelcastSessionRepository.java
│   ├── SessionSerializer.java
│   ├── SessionEventListener.java
│   └── proxy/
│       ├── ChangeTrackingProxy.java
│       ├── TrackedList.java
│       ├── TrackedMap.java
│       └── TrackedSet.java
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

| Dependency | Scope | Purpose |
|---|---|---|
| spring-boot-starter | provided | Core Spring Boot |
| spring-boot-starter-web | provided | Web support |
| spring-boot-autoconfigure | provided | Auto-configuration |
| jakarta.servlet-api | provided | Servlet API |
| spring-session-core | compile | Spring Session interface (`SessionRepository`, events, `SessionRepositoryFilter`) |
| hazelcast 5.3.6 | compile | Distributed map storage |
| jackson-databind | compile | JSON serialization |
| jackson-datatype-jsr310 | compile | Java 8 date/time JSON support |
| spring-boot-starter-test | test | JUnit 5, Mockito |
| spring-boot-starter-security | test | Spring Security for test app |
| rest-assured 5.4.0 | test | HTTP test client |

Spring Boot version: 3.2.2, Java: 17.

## Core Components

### 1. HazelcastSession

Implements `org.springframework.session.Session`. Internal state:

| Field | Type | JSON serialized | Notes |
|---|---|---|---|
| id | String | yes | UUID |
| originalId | String | yes | Tracks ID changes for save logic |
| attributes | Map\<String, Object\> | yes | Session attribute storage |
| creationTime | long | yes, as `creationTimeMillis` | Epoch millis |
| lastAccessedTime | long | yes, as `lastAccessedTimeMillis` | Epoch millis |
| maxInactiveIntervalSeconds | int | yes | Default 1800 (30 min) |
| changed | boolean | no (`@JsonIgnore`, `transient`) | Dirty flag |
| changeCallback | Runnable | no (`@JsonIgnore`, `transient`) | Invoked on mutations |

Key design decisions:

- **Time fields stored as `long` (epoch millis)** instead of `Instant`, to simplify JSON serialization. The `Session` interface methods (`getCreationTime()`, `getLastAccessedTime()`, etc.) are annotated `@JsonIgnore` since they return `Instant`/`Duration`/`Set` types computed from the underlying long/map fields. Separate getter/setter pairs with `Millis`/`Seconds` suffixes expose the raw values to Jackson.
- **`getAttribute()` wraps returned values** with `ChangeTrackingProxy.wrap()` so that collection mutations are detected.
- **`setAttribute()` unwraps proxy objects** via `ChangeTrackingProxy.unwrap()` before storing into the attributes map, so only plain collections are persisted.
- **`markChanged()`** sets the `changed` flag and invokes `changeCallback` (if set).
- **`originalId`** enables the repository to detect session ID changes (via `changeSessionId()`) and remove the old entry during save.

### 2. SessionSerializer

Implements Hazelcast `StreamSerializer<HazelcastSession>` (type ID = 1).

Uses a custom `ObjectMapper` with:

- `JavaTimeModule` registered
- `WRITE_DATES_AS_TIMESTAMPS = true`
- `FAIL_ON_EMPTY_BEANS = false`
- `FAIL_ON_UNKNOWN_PROPERTIES = false`
- **Polymorphic typing enabled**: `DefaultTyping.NON_FINAL` with `JsonTypeInfo.As.PROPERTY`. This adds `"@class"` to JSON for non-final types, so attribute values retain their concrete type through serialization.

Serialization: `objectMapper.writeValueAsBytes(session)` -> `out.writeByteArray(bytes)`.
Deserialization: `in.readByteArray()` -> `objectMapper.readValue(bytes, HazelcastSession.class)`.

### 3. HazelcastSessionRepository

Implements `SessionRepository<HazelcastSession>`. Stores sessions in Hazelcast `IMap<String, HazelcastSession>`.

- Map name: `spring:session:sessions`
- **`createSession()`**: generates UUID, sets maxInactiveInterval to 30 min.
- **`save(session)`**: if session ID changed (id != originalId), removes old entry first. Writes to IMap with `set(id, session, ttlSeconds, SECONDS)` so Hazelcast auto-expires entries.
- **`findById(id)`**: reads from IMap, checks `isExpired()` (deletes and returns null if expired), resets `originalId` and `changed` flag.
- **`deleteById(id)`**: removes from IMap.

### 4. SessionEventListener

Implements four Hazelcast entry listener interfaces: `EntryAddedListener`, `EntryRemovedListener`, `EntryEvictedListener`, `EntryExpiredListener`.

- Registered on the IMap via `@PostConstruct` with `includeValue=true`.
- Unregistered via `@PreDestroy`.
- Maps events to Spring Session events:
  - `entryAdded` -> `SessionCreatedEvent`
  - `entryRemoved` -> `SessionDeletedEvent`
  - `entryEvicted` / `entryExpired` -> `SessionExpiredEvent`
- When the old/new value is null in the event (possible in eviction), creates a dummy `HazelcastSession` with just the session ID.

### 5. Change Tracking Proxies (proxy/)

**ChangeTrackingProxy** (factory class):

- `wrap(obj, onChange)`: if `obj` is a `List`/`Map`/`Set` (and not already wrapped), returns a `TrackedList`/`TrackedMap`/`TrackedSet`. Otherwise returns `obj` as-is.
- `unwrap(obj)`: recursively extracts the delegate from tracked collections, producing new `ArrayList`/`HashMap`/`HashSet` with unwrapped contents.

**TrackedList\<E\>** implements `List<E>` via delegation:

- All mutation methods (`add`, `remove`, `set`, `clear`, `addAll`, `removeAll`, `retainAll`) call `onChange.run()`.
- `get(index)` wraps the returned element recursively.
- `iterator()` returns `TrackedIterator` (wraps elements on `next()`, tracks `remove()`).
- `listIterator()` returns `TrackedListIterator` (wraps elements, tracks `remove`/`set`/`add`).
- `subList()` returns a new `TrackedList` wrapping the delegate's sublist.

**TrackedMap\<K, V\>** implements `Map<K, V>` via delegation:

- All mutation methods (`put`, `remove`, `clear`, `putAll`, `replace`, `compute*`, `merge`) call `onChange.run()`.
- `get(key)` and `getOrDefault()` wrap values recursively.
- `keySet()` returns `TrackedSet`, `values()` returns inner `TrackedValues`, `entrySet()` returns inner `TrackedEntrySet`.
- `TrackedEntrySet` iterator returns `TrackedEntry` where `getValue()` wraps the value and `setValue()` triggers onChange.

**TrackedSet\<E\>** implements `Set<E>` via delegation:

- Same pattern as TrackedList: mutation methods trigger onChange, iterator wraps elements.

### 6. HazelcastSessionAutoConfiguration

`@AutoConfiguration` class, activated when:

- `HazelcastInstance`, `Session`, `SessionRepositoryFilter` are on classpath (`@ConditionalOnClass`)
- Application is a web application (`@ConditionalOnWebApplication`)

Annotated with `@EnableSpringHttpSession` to activate Spring Session's `SessionRepositoryFilter`.

Beans defined (all with `@ConditionalOnMissingBean` for overrideability):

| Bean | Purpose |
|---|---|
| `cookieSerializer` | `DefaultCookieSerializer` with name=`USESSIONID`, path=`/`, httpOnly=true |
| `sessionSerializer` | `SessionSerializer` instance |
| `hazelcastInstance` | Hazelcast Client, reads `HZ_URL`/`HZ_USERNAME`/`HZ_PASSWORD` from env vars, registers `SessionSerializer`, cluster name `dev` |
| `sessionRepository` | `HazelcastSessionRepository` backed by the Hazelcast instance |
| `sessionEventListener` | `SessionEventListener` wired to the repository and `ApplicationEventPublisher` |

Auto-configuration registered in:
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Integration Test Design

### Test Infrastructure

**TestApplication**: `@SpringBootApplication` scanning `com.example.session` and `com.example.session.integration`.

**TestHazelcastConfig**: spins up an **embedded Hazelcast server** (member) and a **Hazelcast client** as Spring beans. The client is `@Primary` so it's used by the session repository. Server uses a non-default port (5799) to avoid conflicts. Both server and client register `SessionSerializer`.

**TestSecurityConfig**: Spring Security filter chain for the test app:

- `/logon` is `permitAll`, all other endpoints require authentication.
- Custom `SessionAuthenticationFilter` (extends `OncePerRequestFilter`, inserted before `BasicAuthenticationFilter`): reads `username` from HttpSession, if present, populates `SecurityContextHolder` with `UsernamePasswordAuthenticationToken`.
- **`RequestAttributeSecurityContextRepository`** instead of the default `HttpSessionSecurityContextRepository`: prevents Spring Security from writing `SPRING_SECURITY_CONTEXT` into the session. This avoids Jackson deserialization failures on Spring Security objects (e.g., `UsernamePasswordAuthenticationToken` has no default constructor).
- **`sessionFixation().none()`**: disables session fixation protection. Without this, `SessionManagementFilter` would change the session ID on every request (since our custom filter sets auth each time without persisting SecurityContext), causing the client's cookie to become stale.
- **`NullRequestCache`**: prevents `DefaultSavedRequest` (which has no default constructor) from being saved to the session.

**TestController**:

- `POST /logon`: accepts `{"username", "password"}`, stores `username` and `loginTime` in session, returns JSON with status and sessionId.
- `GET /do-trans`: reads `username` and `loginTime` from session, returns JSON greeting.

### Test Cases (SessionIntegrationTest)

6 tests using REST Assured, ordered:

1. **shouldLoginSuccessfullyAndReceiveSessionCookie** - POST /logon, verify 200 + `USESSIONID` cookie.
2. **shouldAccessDoTransWithValidSession** - Login then GET /do-trans with cookie, verify 200.
3. **shouldReturn401WhenAccessingDoTransWithoutSession** - GET /do-trans without cookie, verify 401.
4. **shouldReturn401WhenAccessingDoTransWithInvalidSession** - GET /do-trans with fake cookie, verify 401.
5. **shouldPersistSessionDataAcrossRequests** - Login, GET /do-trans twice with same cookie, verify 200 both times and same session ID throughout.
6. **shouldAllowMultipleConcurrentSessions** - Login two users, verify each can access /do-trans with their own session and see their own username.

## Known Pitfalls / Lessons Learned

1. **Spring Security objects must not enter the session.** Jackson cannot deserialize `SecurityContextImpl`, `UsernamePasswordAuthenticationToken`, `SimpleGrantedAuthority`, `DefaultSavedRequest`, etc. (no default constructors, complex graphs). Solution: use `RequestAttributeSecurityContextRepository` + `NullRequestCache` + custom session-based auth filter.

2. **`requireExplicitSave(true)` alone is not enough.** Even with this setting, `SessionManagementFilter` can explicitly save the SecurityContext when it detects a new authentication. Must pair with `RequestAttributeSecurityContextRepository` to fully prevent session writes.

3. **Session fixation protection causes session ID rotation.** When auth is set in SecurityContextHolder without persisting, every request looks like a "new" authentication to `SessionManagementFilter`, triggering `ChangeSessionIdAuthenticationStrategy`. Fix: `sessionFixation().none()`.

4. **Hazelcast `StreamSerializer` uses `ObjectDataInput`/`ObjectDataOutput`**, not `InputStream`/`OutputStream`. This changed in Hazelcast 5.x.

5. **Jackson `@JsonIgnore` needed on computed getters.** `getAttributeNames()`, `getCreationTime()`, `getLastAccessedTime()`, `getMaxInactiveInterval()`, `isExpired()` are derived from other fields. Without `@JsonIgnore`, Jackson treats them as "setterless properties" and fails during deserialization.

6. **Spring Session event constructors require `Session` objects**, not session ID strings. Must pass `event.getValue()` or `event.getOldValue()` from Hazelcast entry events.
