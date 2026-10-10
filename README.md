# One23 — Backend

**One23** helps people who get off at the **same metro station** and go to the **same office/destination** find each other, so **3 people can share one regular (offline) auto-rickshaw** and split the fare.

The app only **matches people**. It does **not** book the auto, set the fare or take payments. The group finds an auto near the station, agrees the fare with the driver, and each person pays their share directly.

Built with **Spring Boot 4 (Java 21)**, **PostgreSQL**, **Spring Security + JWT**, **WebSocket (STOMP)** and **Flyway**.

---

## 1. Folder structure

```
src/main/java/com/one23/one23/
├── One23Application.java          Starts the app. @EnableAsync (email) + @EnableScheduling (cleanup)
│
├── controller/                    HTTP / WebSocket entry points. Check the request, pick the status code
│   ├── AuthController             POST /api/auth/signup, /api/auth/login
│   ├── RideController             join, my rides, cancel, leave group, group lobby, start, end
│   └── ChatController             chat history (REST) + chat & live location (WebSocket)
│
├── service/                       Business rules
│   ├── MatchingService            Forms groups of 3. Holds the lock for WAITING rides
│   ├── RideService                Ride/group rules: membership, leave group, start/end
│   ├── ChatService                Save / load chat messages
│   ├── EmailService               Welcome email after signup (background, never blocks signup)
│   └── CleanupScheduler           Every minute: deletes WAITING/CANCELLED rides older than 15 min
│
├── model/                         Database tables (JPA entities)
│   ├── User                       users
│   ├── RideRequest                ride_request (one row = one person's ride)
│   ├── ChatMessage                chat_messages
│   └── RideStatus                 WAITING / MATCHED / IN_PROGRESS / COMPLETED / CANCELLED
│
├── repository/                    Spring Data JPA interfaces (database queries)
├── dto/                           Request/response objects (what the API accepts and returns)
├── security/                      JwtService, JwtAuthenticationFilter, WebSocketAuthInterceptor, LoginRateLimiter
└── config/                        SecurityConfig, CorsConfig, WebSocketConfig, GlobalExceptionHandler

src/main/resources/
├── application.properties         Settings (secrets come from environment variables / .env)
├── application-prod.properties    Production overrides (SPRING_PROFILES_ACTIVE=prod)
└── db/migration/                  Flyway SQL files: V1 tables, V2 indexes
```

**Flow of a request:** `Controller → Service → Repository → PostgreSQL`

---

## 2. How matching works

1. A logged-in user sends `POST /api/join` with `pickupHub` (metro station) and `destination`.
2. A new ride is saved with status **WAITING**. (A user can have only one open ride at a time.)
3. `MatchingService.addAndMatch()` looks at all WAITING rides, **oldest first**.
4. Two rides are on the **same route** if pickup hub and destination are equal after normalizing:
   trim, collapse extra spaces, ignore case. `"  Manyata   Tech Park"` = `"manyata tech park"`.
   (Only for comparing. The text saved in the database is not changed.)
5. The group is built **around the person who just joined**: **joiner + the 2 riders who have waited longest** on that route, one ride per user.
6. When there are 3, all 3 rides become **MATCHED** and get the same `groupId` (a UUID).
7. A `{"type":"REFRESH"}` message is sent on `/topic/match`, so clients reload their rides. No names are sent.

If fewer than 3 people are waiting, the joiner just stays **WAITING**. The group size is always exactly 3. There is no "go with 2" option.

### Concurrency (many people joining at the same time)

- `addAndMatch()` and `cancelIfStillWaiting()` are `synchronized`. Only **one request at a time** can change WAITING rides, so two requests can never put the same rider into two groups.
- `addAndMatch()` has **no `@Transactional`** on purpose. With it, Spring would commit *after* the method returns, which is after the lock is released, and another request could still read the old WAITING rows. Without it, `saveAll()` commits by itself **inside** the lock.
- Cancelling a WAITING ride uses one SQL statement, `UPDATE ... SET status='CANCELLED' WHERE id=? AND status='WAITING'`, inside the same lock. A ride that was matched a moment ago can't be cancelled as "waiting". The API then returns **409**.
- `synchronized` only works **inside one running backend instance**. That is fine for this project (one instance). See *Known limitations*.

---

## 3. Ride lifecycle

```
             POST /api/join
                  │
                  ▼
   ┌──────────► WAITING ─────────────► CANCELLED      (PATCH /api/rides/{id}/cancel)
   │              │                    (also deleted by cleanup after 15 min if still waiting)
   │              │ 3 riders on the same route
   │              ▼
   │           MATCHED ────────────────► CANCELLED    (the person who leaves the group)
   │              │
   └──────────────┤  someone leaves → the other 2 go back to WAITING
                  │  (wait timer reset, matching runs again)
                  │
                  │ PATCH /api/rides/{groupId}/start
                  ▼
             IN_PROGRESS
                  │ PATCH /api/rides/{groupId}/end
                  ▼
              COMPLETED   (chat history stays readable for members)
```

---

## 4. REST API

All endpoints except `/api/auth/**` need the header `Authorization: Bearer <token>`.
Errors are returned as JSON: `{ "message": "..." }` (validation errors also include `"fieldErrors"`).

### Auth

| Method | URL | Body | Result |
|---|---|---|---|
| POST | `/api/auth/signup` | `{fullName, email, password}` (password ≥ 8 chars) | 200 · 400 if the email is already registered or the input is invalid. Sends a welcome email in the background |
| POST | `/api/auth/login` | `{email, password}` | 200 `{token, email, fullName}` · **401** wrong email or password · 429 too many attempts |

- Emails are compared **case-insensitively** and trimmed. New users are saved in lower case. Older users saved with capitals can still log in.
- Passwords are stored with **BCrypt**. The same message is returned for "unknown email" and "wrong password", so attackers can't find out which emails exist.
- Login is limited to 5 attempts per IP and per email every 60 seconds (configurable).
- The JWT is valid for 24 hours.

### Rides and groups

| Method | URL | What it does | Errors |
|---|---|---|---|
| POST | `/api/join` | Body `{pickupHub, destination}` (`location` is accepted as another name for `pickupHub`). Returns the joiner's ride, or the 3 rides of the new group | 409 already has an open ride |
| GET | `/api/my-rides` | All rides of the logged-in user | |
| PATCH | `/api/rides/{id}/cancel` | Cancel your own **WAITING** ride | 404 · 403 not yours · 409 not waiting / just matched |
| PATCH | `/api/rides/{groupId}/cancel-group` | Leave a **MATCHED** group (group is dissolved) | 404 · 403 not a member · 409 already started |
| GET | `/api/groups/{groupId}` | Group lobby: hub, destination, status, members (name, email, status) | 404 · 403 not a member |
| PATCH | `/api/rides/{groupId}/start` | MATCHED → IN_PROGRESS for the whole group | 404 · 403 · 409 |
| PATCH | `/api/rides/{groupId}/end` | IN_PROGRESS → COMPLETED for the whole group | 404 · 403 · 409 |
| GET | `/api/chat/{groupId}` | Chat history (oldest first), members only | 403 |

---

## 5. WebSocket (live updates)

- Endpoint: **`/ws`** (SockJS + STOMP).
- Send the token in the STOMP **CONNECT** header: `Authorization: Bearer <token>`.

| Type | Destination | Payload / meaning |
|---|---|---|
| send | `/app/chat/{groupId}` | `{ "message": "..." }`. The server adds the sender's name and saves it |
| send | `/app/location/{groupId}` | `{ "latitude": 12.9, "longitude": 77.6 }` |
| subscribe | `/topic/match` | `{type: "REFRESH"}` when any group is formed (public, no private data) |
| subscribe | `/topic/group/{groupId}` | `STARTED`, `COMPLETED`, `DISSOLVED` events |
| subscribe | `/topic/chat/{groupId}` | New chat messages |
| subscribe | `/topic/location/{groupId}` | Live locations `{latitude, longitude, userName}` |

Only members of a group (MATCHED, IN_PROGRESS or COMPLETED) can subscribe to its `/topic/group`, `/topic/chat` and `/topic/location`. Wildcard subscriptions like `/topic/**` are rejected.

---

## 6. Database

PostgreSQL. Flyway creates and updates the schema (`db/migration`). Hibernate only **validates** that it matches the entities (`ddl-auto=validate`).

| Table | Main columns |
|---|---|
| `users` | id, full_name, email (unique), password (BCrypt hash), created_at |
| `ride_request` | id, user_id, name, pickup_hub, destination, status, group_id, created_at |
| `chat_messages` | id, group_id, sender_name, message, timestamp |

V2 adds indexes on the columns used in queries (status, group_id, user_id, created_at, chat group + time).

---

## 7. Run locally

Requirements: **Java 21** and a PostgreSQL database.

```bash
cp .env.example .env        # then fill in DB URL/user/password and JWT_SECRET (≥ 32 chars)
./mvnw spring-boot:run      # starts on http://localhost:8080
./mvnw test                 # runs the tests
```

- `.env` is git-ignored. Never commit it. In production, set the same names as environment variables and set `SPRING_PROFILES_ACTIVE=prod`.
- Email settings are optional. If they're missing, signup still works and only the welcome email fails (a warning is logged).
- `One23ApplicationTests` starts the whole app, so it needs a reachable database. The other tests are plain unit tests with Mockito.
- Deployment: GitHub Actions builds the JAR and deploys it to Azure Web App on every push to `main`.

---

## 8. Interview talking points

1. **Problem:** last-mile travel from the metro to the office. 3 people share 1 auto and pay about a third each. The app matches people. It doesn't provide vehicles or payments.
2. **Layered design:** Controller (HTTP) → Service (rules) → Repository (database). DTOs mean a client can't set `status`, `groupId` or `user` itself.
3. **Matching algorithm:** same normalized hub + destination, first come first served, the joiner is always in the group they get back, exactly 3 different users.
4. **Concurrency bug I fixed:** `synchronized` + `@Transactional` looks safe but isn't, because the commit happens after the lock is released. I removed `@Transactional` so the save commits inside the lock. Cancel uses a conditional `UPDATE ... WHERE status='WAITING'` under the same lock. A unit test sends 30 joins at the same time and checks every group has exactly 3 riders.
5. **Security:** BCrypt passwords, stateless JWT, 401 vs 403 (not logged in vs not allowed), the same error for wrong email or password, login rate limiting, members-only WebSocket topics, no stack traces in error responses.
6. **Real-time:** STOMP over WebSocket for chat, live location and group events. `/topic/match` only says "refresh", so no private data is broadcast.
7. **Database:** Flyway versioned migrations, indexes for the frequent queries, a scheduled cleanup of stale WAITING rides.
8. **Known limitations** (good to mention honestly, see below).

---

## 9. Known limitations

- The `synchronized` lock works within **one** server instance. To run several instances you would need a database-level lock (e.g. `SELECT ... FOR UPDATE`) or a version column for optimistic locking.
- Two members leaving or starting the same group at exactly the same moment are not locked against each other.
- If a user double-clicks "join", two WAITING rides can be created. Matching never puts the same user twice in one group, and the extra ride can be cancelled or is cleaned up after 15 minutes.
- Destination matching is plain text (after normalizing). Different gates or buildings of the same tech park count as different destinations unless users type the same text.
- The login rate limiter is in memory, so it resets when the app restarts.
