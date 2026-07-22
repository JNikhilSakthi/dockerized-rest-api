# Dockerized REST API

A Spring Boot product catalog service, packaged and shipped entirely through Docker — a two-stage build, a non-root runtime image, an Actuator-backed `HEALTHCHECK`, and a full `docker compose` stack (app + MySQL + Adminer) that comes up with one command.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Docker](https://img.shields.io/badge/Docker-multi--stage-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Testcontainers](https://img.shields.io/badge/Testcontainers-1.20.1-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

**Learning Track:** `springboot-docker-demo` (Project 13 of 17)
**Real-World Service Name:** `dockerized-rest-api`

---

## 1. Project Overview

This project answers a question every Spring Boot developer eventually hits: *"it works on my machine — now what?"* The application itself is a small but complete catalog API (categories that own products), but the actual subject under study is **Docker**: how to build a production-shaped image, how to wire a multi-container stack together, and how to make that stack reproducible for anyone who clones the repo.

**Problem solved.** Without containerization, running this app requires: installing a matching JDK, installing MySQL, creating the right schema/user, setting environment variables by hand, and hoping the developer's OS doesn't leak into the behavior of the app. Docker collapses all of that into `docker compose up`. The `Dockerfile` guarantees the exact same JRE and OS base image runs in every environment; `docker-compose.yml` guarantees the exact same database version and network topology.

**Why Docker specifically (and not just a JAR + install guide).**
- **Immutable artifacts** — the image built once is the exact image that runs in every environment; no "works on my machine."
- **Isolation** — the app's JVM, its dependencies, and MySQL each run in their own filesystem/process namespace, never colliding with what's already installed on the host.
- **Reproducible multi-service topology** — `docker-compose.yml` declares the app, the database, and an admin UI, wires them onto one private network, and starts them in the correct dependency order (`condition: service_healthy`), all from source control.
- **Small, secure runtime footprint** — the two-stage build discards the ~600MB of Maven/JDK tooling used to compile, shipping only a JRE-Alpine image running as a non-root user.

**Where this pattern is used in real companies.** This is the default deployment unit for virtually every modern backend team: CI pipelines build the same multi-stage Dockerfile to produce the artifact that's promoted through dev → staging → prod; Kubernetes, ECS, and Cloud Run all schedule containers built exactly this way; `docker-compose.yml` is the de facto standard for local developer environments and integration-test fixtures (mirrored here in the Testcontainers-based integration test, which boots the *same* `mysql:8.0` image used in compose). The HEALTHCHECK pattern backed by Spring Boot Actuator's liveness probe is the same mechanism Kubernetes uses for liveness/readiness probes.

---

## 2. Architecture

### High-Level Design (HLD)

```
                        ┌─────────────────────────────────────────┐
                        │        docker-compose bridge network     │
                        │              "catalog-net"                │
                        │                                           │
   Host machine         │   ┌────────────┐   ┌────────────────┐    │
  ───────────────       │   │  adminer   │   │   catalog-app   │    │
  localhost:8081  ───────┼──▶│  :8080     │   │   :8080         │    │
                        │   │ (DB UI)    │   └───────┬─────────┘    │
  localhost:8080  ───────┼──────────────────────────▶│              │
                        │                            │ JDBC          │
                        │                    ┌───────▼─────────┐    │
  localhost:3306  ───────┼───────────────────▶│  catalog-mysql   │    │
                        │                    │  mysql:8.0        │    │
                        │                    │  volume:mysql_data│    │
                        │                    └───────────────────┘    │
                        └─────────────────────────────────────────┘
```

- `app` depends on `mysql` reaching a **healthy** state (`mysqladmin ping`) before it starts — not merely "container started," but "database actually accepting connections."
- `adminer` gives a browser-based DB admin UI (`http://localhost:8081`) pointed at the `mysql` service by container name.
- `mysql_data` is a named volume, so catalog data survives `docker compose down` (but not `down -v`).

### Low-Level Design (LLD) — request flow & domain model

```
HTTP request
   │
   ▼
@RestController (CategoryController / ProductController)
   │  - deserializes JSON -> request DTO (record)
   │  - @Valid triggers Jakarta Bean Validation
   ▼
Service interface (CategoryService / ProductService)
   │  - @Transactional boundary (readOnly=true for reads)
   ▼
ServiceImpl (CategoryServiceImpl / ProductServiceImpl)
   │  - business rules: uniqueness checks, existence checks
   ▼
Repository (Spring Data JPA: CategoryRepository / ProductRepository)
   │
   ▼
MySQL 8.0 (categories, products tables)
   │
   ▼
Entity ──▶ Mapper (CategoryMapper / ProductMapper) ──▶ Response DTO (record) ──▶ JSON
```

Errors thrown anywhere in that pipeline (ResourceNotFoundException, DuplicateResourceException,
DataIntegrityViolationException, MethodArgumentNotValidException) are caught by a single
`@RestControllerAdvice` (`GlobalExceptionHandler`) and turned into a uniform `ErrorResponse` JSON body.

### Domain model (DB design)

```
┌────────────────────┐        1        many  ┌───────────────────────────┐
│      categories      │──────────────────────▶│          products           │
├────────────────────┤                        ├───────────────────────────┤
│ id           PK      │                        │ id             PK          │
│ name         UNIQUE   │                        │ name                       │
│ description           │                        │ sku            UNIQUE      │
└────────────────────┘                        │ description                │
                                                │ price                      │
       CascadeType.ALL:                        │ quantity                   │
   deleting a Category                          │ status  (ACTIVE/            │
   deletes its Products                          │  DISCONTINUED/             │
                                                │  OUT_OF_STOCK)             │
                                                │ category_id    FK          │
                                                │ created_at                 │
                                                │ updated_at                 │
                                                └───────────────────────────┘
```

- `Category (1) -to- Product (many)`, FK `products.category_id` (`@ManyToOne(optional = false)`).
- `categories.name` and `products.sku` are both unique — `sku` is a deliberate **business key** distinct from the surrogate `id`, mirroring how real inventory/ERP systems key products.
- `Category.products` cascades `ALL` to its products: deleting a category removes its products in the same operation (mirrored by `mysqld`'s own FK constraint honoring the cascade Hibernate issues).

### Folder structure

```
dockerized-rest-api/
├── Dockerfile                      # two-stage build: maven builder -> jre-alpine runtime
├── docker-compose.yml              # app + mysql + adminer stack
├── .env.example                    # documented, overridable compose variables
├── .dockerignore
├── .gitignore
├── pom.xml
├── src/main/java/com/medha/dockerizedrestapi/
│   ├── DockerizedRestApiApplication.java
│   ├── domain/            Category, Product, ProductStatus
│   ├── repository/        CategoryRepository, ProductRepository
│   ├── dto/                CategoryRequest/Response, ProductRequest/Response
│   ├── exception/          ResourceNotFoundException, DuplicateResourceException,
│   │                       ErrorResponse, GlobalExceptionHandler
│   ├── mapper/             CategoryMapper, ProductMapper
│   ├── service/             CategoryService, ProductService (+ impl/)
│   └── controller/          CategoryController, ProductController
├── src/main/resources/
│   ├── application.yml
│   └── data.sql
└── src/test/java/com/medha/dockerizedrestapi/
    ├── service/             CategoryServiceImplTest, ProductServiceImplTest (Mockito)
    ├── controller/          ProductControllerTest (@WebMvcTest + MockMvc)
    └── integration/         ProductApiIntegrationTest (Testcontainers + real MySQL)
```

---

## 3. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Language / runtime | Java 21 | LTS, required by Spring Boot 3.3.x |
| Framework | Spring Boot 3.3.4 (Web, Data JPA, Validation, Actuator) | standard roadmap stack |
| Database | MySQL 8.0 | real relational engine, same image in compose and Testcontainers |
| DB driver | `mysql-connector-j` | official MySQL JDBC driver |
| Boilerplate reduction | Lombok (`@Getter`/`@Setter`/etc.) | keeps entities terse; excluded from the final runtime jar |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | declarative request validation on DTOs |
| Ops/observability | Spring Boot Actuator | backs the container `HEALTHCHECK` and compose `service_healthy` gate |
| Containerization | Docker (multi-stage build) | reproducible, minimal, non-root runtime image |
| Orchestration (local) | Docker Compose | app + MySQL + Adminer as one declared stack |
| DB admin UI | Adminer 4.8.1 | zero-config browser UI for inspecting MySQL during development |
| Unit testing | JUnit 5 + Mockito + AssertJ | service-layer logic in isolation |
| Web-layer testing | `@WebMvcTest` + MockMvc | controller/HTTP-contract tests without a real server or DB |
| Integration testing | Testcontainers (`mysql:8.0`) | full Spring context against a real, disposable MySQL instance |
| Build tool | Maven (`maven-compiler-plugin`, `spring-boot-maven-plugin`) | compiles and packages the executable jar used by the Dockerfile |

---

## 4. Configuration Explained

### `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: dockerized-rest-api

  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:catalog_db}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${DB_USERNAME:catalog_user}
    password: ${DB_PASSWORD:catalog_pass}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      connection-timeout: 20000
      maximum-pool-size: 10
      pool-name: catalog-hikari-pool
```
- **`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`** are all resolved from environment variables with `localhost`/`3306`/`catalog_db`/`catalog_user`/`catalog_pass` as defaults. This is the crux of "the same jar/image runs everywhere": run it bare on a laptop and it talks to a locally-installed MySQL on `localhost`; run it inside `docker-compose.yml`, and the `app` service's `environment:` block overrides `DB_HOST` to `mysql` (the compose service name resolved via Docker's embedded DNS) — no code or config-file change needed between the two modes.
- `useSSL=false&allowPublicKeyRetrieval=true` — avoids SSL handshake/cert friction for local/demo MySQL; `serverTimezone=UTC` avoids the JDBC driver failing to auto-detect timezone inside minimal container images.
- HikariCP pool tuned modestly (`maximum-pool-size: 10`) since this is a demo service, with a named pool (`catalog-hikari-pool`) so its metrics/logs are identifiable if multiple pools ever exist in the same JVM.

```yaml
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    open-in-view: false
    defer-datasource-initialization: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect

  sql:
    init:
      mode: always
      encoding: UTF-8
```
- **`ddl-auto: update`** — Hibernate creates/updates `categories`/`products` from the `@Entity` mappings on startup. Fine for a learning project/demo; a real production service would use versioned migrations (Flyway/Liquibase) instead.
- **`open-in-view: false`** — disables the Open Session In View anti-pattern; forces all lazy-loading (e.g. `Category.products`) to happen deliberately inside the `@Transactional` service layer, not accidentally during view/JSON rendering.
- **`defer-datasource-initialization: true`** combined with **`spring.sql.init.mode: always`** — makes Spring run `data.sql` *after* Hibernate has created the schema (instead of before, which is the default and would fail since the tables wouldn't exist yet), and re-runs it on every startup rather than only once.
- **`dialect: MySQLDialect`** pinned explicitly for clarity/portability even though Boot can usually infer it from the driver.

```yaml
server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
```
- **`server.shutdown: graceful`** — in-flight requests are allowed to finish before the JVM exits, important when `docker compose down` or a container orchestrator sends `SIGTERM`.
- **`management.endpoint.health.probes.enabled`** plus the explicit `readiness-state`/`liveness-state` flags expose `/actuator/health/liveness` and `/actuator/health/readiness` — the former is exactly what the Dockerfile's `HEALTHCHECK` and the compose `app` healthcheck poll.
- **`show-details: when-authorized`** avoids leaking internal health details (DB connection info, disk space) to anonymous callers.

### `src/main/resources/data.sql`
Seeds 3 categories (`Electronics`, `Books`, `Home & Kitchen`) and 4 products (`ELEC-001`, `ELEC-002`, `BOOK-001`, `HOME-001`), using `INSERT IGNORE` so re-running it on every startup (`spring.sql.init.mode=always`) never produces duplicate rows or errors — it silently no-ops once the unique constraints on `categories.name`/`products.sku` are already satisfied.

---

## 5. Project Structure Explained

| Path | Purpose |
|---|---|
| `Dockerfile` | Two-stage build producing the runtime image (see §7). |
| `docker-compose.yml` | Declares the `mysql` + `app` + `adminer` stack, network, and volume. |
| `.dockerignore` | Keeps `target/`, `.git/`, IDE files, and `.env` out of the build context sent to the Docker daemon. |
| `.env.example` | Documents every overridable compose variable (DB credentials, ports, `JAVA_OPTS`); copy to `.env` to customize. |
| `.gitignore` | Standard Java/IDE ignores, plus `.env` so real secrets never get committed. |
| `pom.xml` | Maven build: Spring Boot 3.3.4 parent, Java 21, Testcontainers BOM, explicit Lombok annotation-processor path (see design notes below). |
| `domain/Category.java`, `domain/Product.java`, `domain/ProductStatus.java` | JPA entities and the product lifecycle enum. |
| `repository/*` | Spring Data JPA repositories with derived-query finders (`findByNameIgnoreCase`, `existsBySkuIgnoreCase`, `findByCategoryId`). |
| `dto/*` | Immutable Java `record` request/response DTOs with Jakarta Bean Validation annotations. |
| `exception/*` | Custom exceptions, the uniform `ErrorResponse` shape, and the single `@RestControllerAdvice`. |
| `mapper/*` | Hand-rolled entity ↔ DTO mappers (no MapStruct/ModelMapper dependency — intentional, per the roadmap's "understand every layer" philosophy). |
| `service/*`, `service/impl/*` | Interface + implementation per the roadmap's standard layering, `@Transactional` with `readOnly=true` on reads. |
| `controller/*` | `@RestController`s exposing `/api/v1/categories` and `/api/v1/products`. |
| `application.yml`, `data.sql` | Runtime configuration and idempotent seed data. |
| `src/test/...` | Mockito service tests, `@WebMvcTest` controller tests, Testcontainers integration test. |

---

## 6. Getting Started

### Prerequisites
- Docker Desktop (or Docker Engine + Compose plugin) — that's it. You do **not** need a local JDK, Maven, or MySQL install; the Dockerfile's build stage handles compilation inside the container.

### Run the whole stack

```bash
# 1. Clone the repo
git clone https://github.com/JNikhilSakthi/dockerized-rest-api.git
cd dockerized-rest-api

# 2. (optional) customize ports/credentials
cp .env.example .env

# 3. Build the app image and start app + mysql + adminer
docker compose up --build

# App:      http://localhost:8080
# Adminer:  http://localhost:8081  (System: MySQL, Server: mysql, user/pass from .env)
# MySQL:    localhost:3306 (if you need a native client)
```

Wait for `catalog-app` to report `healthy` (`docker compose ps`) — this means MySQL was already healthy and the app's liveness probe is passing.

```bash
# Stop the stack (keeps data in the named volume)
docker compose down

# Stop and wipe the MySQL volume too
docker compose down -v

# Rebuild just the app image after a code change
docker compose up --build app

# Tail logs
docker compose logs -f app
```

### Run without Docker (for comparison)
With a local JDK 21 + Maven + MySQL running on `localhost:3306` with a `catalog_db` database and `catalog_user`/`catalog_pass` credentials:
```bash
./mvnw spring-boot:run
```
This works unchanged because `application.yml`'s `${DB_HOST:localhost}`-style defaults fall back to `localhost` when no environment variables are set.

---

## 7. API Documentation

Base path: `/api/v1`

### Categories — `CategoryController`

| Method | Path | Description | Success |
|---|---|---|---|
| POST | `/api/v1/categories` | Create a category | 201 Created |
| GET | `/api/v1/categories/{id}` | Get one category by id | 200 OK |
| GET | `/api/v1/categories` | List all categories | 200 OK |
| PUT | `/api/v1/categories/{id}` | Update a category | 200 OK |
| DELETE | `/api/v1/categories/{id}` | Delete a category (cascades to its products) | 204 No Content |

**Create category — request**
```http
POST /api/v1/categories
Content-Type: application/json

{
  "name": "Toys",
  "description": "Toys and games"
}
```
**Response `201 Created`** (`Location: /api/v1/categories/4`)
```json
{
  "id": 4,
  "name": "Toys",
  "description": "Toys and games",
  "productCount": 0
}
```

### Products — `ProductController`

| Method | Path | Description | Success |
|---|---|---|---|
| POST | `/api/v1/products` | Create a product | 201 Created |
| GET | `/api/v1/products/{id}` | Get one product by id | 200 OK |
| GET | `/api/v1/products?categoryId={id}` | List products, optionally filtered by category | 200 OK |
| PUT | `/api/v1/products/{id}` | Update a product | 200 OK |
| DELETE | `/api/v1/products/{id}` | Delete a product | 204 No Content |

**Create product — request**
```http
POST /api/v1/products
Content-Type: application/json

{
  "name": "Building Blocks",
  "sku": "TOY-100",
  "description": "120-piece wooden block set",
  "price": 24.99,
  "quantity": 200,
  "categoryId": 4
}
```
**Response `201 Created`** (`Location: /api/v1/products/5`)
```json
{
  "id": 5,
  "name": "Building Blocks",
  "sku": "TOY-100",
  "description": "120-piece wooden block set",
  "price": 24.99,
  "quantity": 200,
  "status": "ACTIVE",
  "categoryId": 4,
  "categoryName": "Toys",
  "createdAt": "2026-07-22T10:15:30Z",
  "updatedAt": "2026-07-22T10:15:30Z"
}
```

**Error responses** (all shaped by `ErrorResponse` via `GlobalExceptionHandler`)

`404` — unknown id (`ResourceNotFoundException`):
```json
{
  "timestamp": "2026-07-22T10:20:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id 999",
  "path": "/api/v1/products/999"
}
```

`409` — duplicate SKU/name (`DuplicateResourceException` or a DB-level `DataIntegrityViolationException`):
```json
{
  "timestamp": "2026-07-22T10:21:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "A product with sku 'ELEC-001' already exists",
  "path": "/api/v1/products"
}
```

`400` — validation failure (`MethodArgumentNotValidException`), field-level detail:
```json
{
  "timestamp": "2026-07-22T10:22:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/products",
  "validationErrors": {
    "price": "price must not be negative"
  }
}
```

---

## 8. Testing

Three layers of tests, matching the three trust boundaries in the architecture:

```bash
# Unit + web-layer tests (no Docker/DB needed)
./mvnw test

# Everything, including the Testcontainers integration test (requires a working Docker daemon)
./mvnw verify
```

- **`CategoryServiceImplTest` / `ProductServiceImplTest`** (Mockito, `@ExtendWith(MockitoExtension.class)`) — exercise `CategoryServiceImpl`/`ProductServiceImpl` with the repositories mocked, covering: successful create, duplicate-name/duplicate-SKU rejection, not-found on get/update/delete, rename collision detection, and category-filtered vs. unfiltered product listing.
- **`ProductControllerTest`** (`@WebMvcTest(ProductController.class)` + MockMvc, `ProductService` mocked via `@MockBean`) — verifies the actual HTTP contract: `201` with a populated body on valid create, `400` with a `validationErrors.price` entry on a negative price, `409` with the duplicate-SKU message on a thrown `DuplicateResourceException`, `404` on a thrown `ResourceNotFoundException`, and `204` on delete.
- **`ProductApiIntegrationTest`** (`@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers `MySQLContainer("mysql:8.0")`) — boots the *full* Spring context against a real, disposable MySQL 8.0 container (the same image `docker-compose.yml` uses), overriding datasource properties via `@DynamicPropertySource` and disabling the demo seed (`spring.sql.init.mode=never`) for test isolation. It creates a category, creates a product linked to it, and asserts the product is retrievable and the category's `productCount` reflects the link — proving the full stack (controller → service → JPA → real MySQL) end to end.

> Note from the build: all 19 unit/MockMvc tests pass. The Testcontainers integration test needs a Docker socket the test JVM can launch containers against; it does not run in Docker-in-Docker-restricted sandboxes, but works normally on a developer machine or CI runner with Docker available.

---

## 9. Docker

### `Dockerfile` — two-stage build

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests && cp target/dockerized-rest-api.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness | grep -q '"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -jar app.jar"]
```

- **Stage 1 (`build`)** uses the full `maven:3.9.9-eclipse-temurin-21` image (JDK + Maven) purely to compile. Copying `pom.xml` first and running `dependency:go-offline` before copying `src/` lets Docker cache the downloaded-dependencies layer — code-only changes skip re-downloading the entire Maven repo.
- **Stage 2 (`runtime`)** starts fresh from `eclipse-temurin:21-jre-alpine` — a JRE only, Alpine-based, so no Maven, no JDK compiler, no source tree ends up in the shipped image. Only `app.jar` is copied across the stage boundary via `COPY --from=build`.
- **Non-root user** (`spring:spring`) — the container runs as an unprivileged user rather than image-default root, limiting the blast radius if the app process is ever compromised.
- **`HEALTHCHECK`** polls Spring Boot Actuator's `/actuator/health/liveness` endpoint with `wget` (present in the Alpine base) — this is the same liveness signal a Kubernetes `livenessProbe` would use, and it's what `docker-compose.yml`'s `depends_on: condition: service_healthy` gate reads for the `app` service itself.
- **`-XX:MaxRAMPercentage=75.0`** bounds JVM heap to 75% of the container's memory limit (rather than the JVM guessing from host-level memory), and `$JAVA_OPTS` is left as an escape hatch for ad-hoc flags at `docker run`/compose time.

### `docker-compose.yml` — the stack

- **`mysql`** (`mysql:8.0`) — credentials/database name come from `${DB_NAME}`/`${DB_USERNAME}`/`${DB_PASSWORD}`/`${MYSQL_ROOT_PASSWORD}` (all overridable via `.env`, all defaulted so the stack runs out of the box); data persists in the named volume `mysql_data`; a `healthcheck` runs `mysqladmin ping` so dependents can wait for real readiness, not just "container started."
- **`app`** — built from the local `Dockerfile`; `environment:` hard-codes `DB_HOST=mysql`/`DB_PORT=3306` (the compose service name, resolved by Docker's internal DNS) while still allowing `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`/`JAVA_OPTS` to be overridden; `depends_on: mysql: condition: service_healthy` means the app container won't even start until MySQL is accepting connections; its own `healthcheck` mirrors the Dockerfile's `HEALTHCHECK`.
- **`adminer`** (`adminer:4.8.1`) — a zero-config web UI for browsing the MySQL data, pre-pointed at the `mysql` service via `ADMINER_DEFAULT_SERVER`; also gated on `mysql` being healthy.
- **`networks.catalog-net`** (bridge driver) — all three services share one private network, so `app`/`adminer` can resolve `mysql` by service name without publishing MySQL's port to each other explicitly (the host-side `ports:` mapping is only for the developer's own convenience).
- **`.env.example`** documents every variable compose reads (`MYSQL_ROOT_PASSWORD`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_EXPOSED_PORT`, `APP_PORT`, `JAVA_OPTS`, `ADMINER_PORT`) with safe local defaults, so a first run needs no `.env` file at all — copying it is purely for customization.

---

## 10. Interview Preparation

**Q: Why a multi-stage Dockerfile instead of one `FROM` with a JDK?**
A single-stage image would ship the full Maven distribution, the JDK compiler, and the entire source tree in the final image — larger, slower to pull/start, and a bigger attack surface. Multi-stage builds let the *build environment* and the *runtime environment* be different images entirely; only the final artifact (`app.jar`) crosses the boundary via `COPY --from=build`.

**Q: Why JRE instead of JDK in the runtime stage?**
The running app never compiles anything — it only needs a Java Runtime Environment. Shipping a JDK (which bundles compiler tooling) in production is unnecessary weight and unnecessary capability inside the container.

**Q: Why run as a non-root user?**
Defense in depth. If an attacker achieves code execution inside the container, a non-root user without a shell/package-manager-relevant permissions limits what they can do — they can't install packages, can't bind privileged ports, and (with typical container runtime configuration) can't easily escalate to affect the host.

**Q: What does `depends_on: condition: service_healthy` actually guarantee — and what doesn't it guarantee?**
It guarantees Docker won't start the `app` container until `mysql`'s `HEALTHCHECK` (an actual `mysqladmin ping`) reports healthy — stronger than plain `depends_on`, which only waits for the container process to start, not for the database to be ready to accept connections. It does *not* guarantee the app's own connection pool won't hit a transient error on the very first request if MySQL becomes momentarily unavailable after being marked healthy — Hikari's retry/connection-timeout settings still matter.

**Q: Why does `application.yml` default to `localhost` but compose overrides it to `mysql`?**
Because `mysql` is only a resolvable hostname *inside* the Docker network Compose creates — it's the service name, resolved via Docker's embedded DNS. Outside Docker (running the jar directly on a laptop with a local MySQL install), that hostname doesn't exist, so the default falls back to `localhost`. This is the whole point of externalizing config via environment variables instead of hardcoding either value.

**Q: Why does the Testcontainers integration test also matter here, given Docker is already the deployment mechanism?**
It closes the loop: instead of testing against H2 or a hand-mocked database (which can silently diverge from real MySQL behavior — case sensitivity, SQL dialect quirks, constraint enforcement), the integration test launches the *exact same* `mysql:8.0` image used in `docker-compose.yml`, inside the test JVM's own Docker daemon, proving the app behaves identically in CI/test as it will in the real container stack.

**Common mistakes with this pattern:**
- Forgetting `.dockerignore`, so the entire `target/`, `.git/`, or IDE folder gets sent to the Docker build context (slow builds, potential secret leakage).
- Copying the full `src/` before `pom.xml` in the Dockerfile, which busts Docker's dependency-download cache layer on every single code change.
- Using `ddl-auto: update` (fine for a demo, as done here) in a real production system instead of versioned migrations — schema drift and unreviewable, silent DDL changes are a common outage cause.
- Not setting a memory bound like `-XX:MaxRAMPercentage` — the JVM can otherwise size its heap based on the host's total memory rather than the container's cgroup limit, leading to OOM-killed containers.
- Skipping a `HEALTHCHECK`/liveness probe entirely — without it, an orchestrator (or `depends_on`) has no way to distinguish "container process running" from "application actually able to serve traffic."

**Production considerations beyond this demo:**
- Replace `ddl-auto: update` + `data.sql` with Flyway/Liquibase migrations for auditable, reversible schema change management.
- Externalize secrets (DB password, etc.) via a secrets manager or orchestrator-native secret mounts rather than plain environment variables in `.env`.
- Add resource limits (`mem_limit`/`cpus` or Kubernetes `resources.limits`) so the `-XX:MaxRAMPercentage` bound has an actual ceiling to be a percentage *of*.
- Consider a distroless or `-jre-alpine` variant hardened further (no shell) for maximum attack-surface reduction once `wget`-based healthchecks are replaced with an orchestrator-native probe.

**Performance notes:**
- `-XX:MaxRAMPercentage=75.0` avoids both under-utilizing available container memory and OOM-killing the container from an oversized default heap guess.
- `open-in-view: false` avoids holding a DB connection open for the full duration of view rendering — connections return to the Hikari pool as soon as the transactional service method returns, improving pool throughput under load.
- The Maven dependency-download layer being cached (via the `pom.xml`-first `COPY` in the Dockerfile) makes iterative `docker compose up --build` cycles during development far faster than a naive single-`COPY` Dockerfile.

---

## License

MIT — see [LICENSE](./LICENSE).
