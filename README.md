# Facility Work-Order Service

Spring Boot REST API where facility staff report maintenance work orders for assets (HVAC unit, lift, pump).
A manager assigns a technician, who moves the order through a strict status flow.

![CI](https://github.com/Shaurya55555/facility-workorder-service/actions/workflows/ci.yml/badge.svg)

**Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, PostgreSQL 16, Flyway, Spring Security + JWT (jjwt),
Bean Validation, springdoc OpenAPI, JUnit 5, Mockito, Testcontainers, Docker, GitHub Actions.

**Live demo:** https://facility-workorder-service.onrender.com (Swagger UI at `/swagger-ui.html`, demo login `manager1` / `tech1` with password `Password123!`). Free tier: the first request after idle can take ~50 s.

## Status flow and roles

```
OPEN --assign--> ASSIGNED --> IN_PROGRESS --> DONE
```

Any other move (skipping a step, going backwards, leaving DONE) is rejected with `409 Conflict`.

| Action | ADMIN | MANAGER | TECHNICIAN |
|---|:-:|:-:|:-:|
| Log in, list/read assets and work orders | yes | yes | yes |
| Report (create) a work order | yes | yes | yes |
| Create / edit assets, edit work orders | yes | yes | no |
| **Assign** a technician | yes | yes | **no** |
| Move status (`ASSIGNED -> IN_PROGRESS -> DONE`) | yes | yes | **only on orders assigned to them** |
| Delete assets / work orders, create users | yes | no | no |
| List users (to pick a technician) | yes | yes | no |

## Run it

```bash
# 1. everything in containers (Postgres + app, demo users seeded)
docker compose up --build

# 2. or locally: start a Postgres, then
export DB_URL=jdbc:postgresql://localhost:5432/workorders DB_USER=workorders DB_PASSWORD=workorders
SEED_DEMO_USERS=true mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html (use **Authorize** with a token from `/api/auth/login`)
- Tiny Vue page listing work orders: http://localhost:8080/
- Seeded users: `admin / Admin#12345`, and with `SEED_DEMO_USERS=true` also `manager1`, `tech1`, `tech2`
  (password `Password123!`). Change `ADMIN_PASSWORD` and `JWT_SECRET` for anything real.

## curl walkthrough

```bash
BASE=http://localhost:8080

login() { curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"Password123!\"}" | jq -r .accessToken; }
MANAGER=$(login manager1); TECH=$(login tech1)
JSON='Content-Type: application/json'

# manager registers an asset, then anyone can report a fault against it
curl -s -X POST $BASE/api/assets -H "Authorization: Bearer $MANAGER" -H "$JSON" \
  -d '{"name":"Rooftop HVAC 3","assetType":"HVAC","location":"Block B roof"}'
curl -s -X POST $BASE/api/work-orders -H "Authorization: Bearer $MANAGER" -H "$JSON" \
  -d '{"title":"Compressor tripping","description":"Trips every ~20 min","assetId":1,"priority":"HIGH"}'

# manager picks a technician and assigns (OPEN -> ASSIGNED)
curl -s "$BASE/api/users?role=TECHNICIAN" -H "Authorization: Bearer $MANAGER"
curl -s -X POST $BASE/api/work-orders/1/assign -H "Authorization: Bearer $MANAGER" -H "$JSON" \
  -d '{"technicianId":3}'

# the technician works the order
curl -s -X POST $BASE/api/work-orders/1/status -H "Authorization: Bearer $TECH" -H "$JSON" -d '{"status":"IN_PROGRESS"}'
curl -s -X POST $BASE/api/work-orders/1/status -H "Authorization: Bearer $TECH" -H "$JSON" -d '{"status":"DONE"}'

# filter + paginate + sort
curl -s "$BASE/api/work-orders?status=ASSIGNED&priority=HIGH&assetId=1&page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer $MANAGER"
```

What the guard rails look like (real responses):

```text
# skipping a step
POST /api/work-orders/17/status {"status":"DONE"}   (order is ASSIGNED)
409 {"status":409,"error":"Conflict","message":"Invalid status transition: ASSIGNED -> DONE", ...}

# a technician trying to assign
POST /api/work-orders/17/assign                       (as tech1)
403 {"status":403,"error":"Forbidden","message":"Access Denied", ...}

# no / bad token
401 {"status":401,"error":"Unauthorized","message":"Authentication required: send a valid 'Authorization: Bearer <token>' header", ...}

# validation
400 {"status":400,"message":"Validation failed","violations":[{"field":"title","message":"must not be blank"}]}
```

## API summary

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | public, returns a JWT |
| GET/POST | `/api/assets` | paginated list, create (manager/admin) |
| GET/PUT/DELETE | `/api/assets/{id}` | delete is admin only, `409` if the asset still has work orders |
| GET | `/api/work-orders` | filters `status`, `assetId`, `priority`; `page`, `size`, `sort` |
| POST | `/api/work-orders` | report a work order |
| GET/PUT/DELETE | `/api/work-orders/{id}` | PUT manager/admin (not once DONE), DELETE admin |
| POST | `/api/work-orders/{id}/assign` | manager/admin, body `{"technicianId": n}` |
| POST | `/api/work-orders/{id}/status` | body `{"status": "IN_PROGRESS"}` |
| POST/GET | `/api/users` | admin creates, manager/admin list (`?role=TECHNICIAN`) |

## Design notes

- **JWT filter** (`JwtAuthenticationFilter`): a `OncePerRequestFilter` placed before `UsernamePasswordAuthenticationFilter`.
  It reads `Authorization: Bearer ...`, verifies signature and expiry in `JwtService`, and puts an authenticated
  principal (`id`, `username`, `role` taken from the token claims, no DB lookup) into the `SecurityContext`.
  A missing/invalid token just leaves the context empty; the authorization rules then produce a JSON `401`.
- **Role rules** are enforced in two layers: `@PreAuthorize` on controllers for coarse role checks, and in
  `WorkOrderService` for the data-dependent rule "a technician may only change orders assigned to them".
- **Status flow** lives in the `WorkOrderStatus` enum (each state lists its only legal successors); the entity and
  service call `requireTransitionTo`, which throws `InvalidTransitionException` mapped to `409`.
  The DB also has a check constraint (`status = 'OPEN'` iff nobody is assigned).
- **Transactions**: write methods are `@Transactional` (load, checks and update commit or roll back together;
  Hibernate dirty checking flushes the change); reads use `readOnly = true`. `@Version` gives optimistic locking so
  two concurrent status changes cannot both succeed (`409`).
- **Errors**: one `@RestControllerAdvice` (`GlobalExceptionHandler`) turns every exception, including Spring MVC's own,
  into the same `ApiError` JSON shape. Security-layer 401/403 use the same shape.
- **Schema** is owned by Flyway (`src/main/resources/db/migration`); Hibernate runs with `ddl-auto: validate`.
- **List queries** use JPA Specifications for the optional filters and an entity graph to avoid N+1 selects.

## Tests

```bash
mvn test      # unit tests only (surefire): state machine, Mockito service tests, JWT, filter
mvn verify    # unit + integration tests (failsafe) + packaging
```

- **Unit tests** (`*Test`): the status state machine (every from/to pair), `WorkOrderService` with mocked
  repositories (assign rules, ownership, invalid transitions), `JwtService` (round trip, expiry with a fake clock,
  tampered/forged tokens), `JwtAuthenticationFilter`.
- **Integration tests** (`*IT`): full Spring context + real security chain + Flyway against a real PostgreSQL.
  `AbstractIntegrationTest` starts a `postgres:16-alpine` Testcontainers container once per JVM and injects its
  JDBC URL with `@DynamicPropertySource`. Needs a Docker daemon (GitHub Actions runners have one).
  Without Docker, set `TEST_DB_URL` (and `TEST_DB_USER` / `TEST_DB_PASSWORD`) to use an existing empty Postgres.

CI (`.github/workflows/ci.yml`) runs `mvn -B verify` on every push and PR, then builds the Docker image.

## Project layout

```
src/main/java/com/shaurya/workorders
  auth/        login endpoint
  security/    JwtService, JwtAuthenticationFilter, SecurityConfig
  user/ asset/ workorder/   entity, repository, DTOs, controller (+ service for work orders)
  error/       exceptions + GlobalExceptionHandler
  config/      properties, seeding, OpenAPI
src/main/resources/db/migration/V1__init_schema.sql
```
