# Milestone 1: Build and run the product read API

This chapter records what we built, why each piece exists, and the exact commands used to operate it. The backend uses Java 25, Spring Boot 4.1.1, Maven, PostgreSQL, and Flyway. Java and Maven run inside Docker, so they are not installed on the host machine.

## 1. What this milestone delivers

The first milestone is deliberately small: read products from PostgreSQL over HTTP.

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/products` | Returns every product, ordered by ID |
| `GET` | `/api/products/{id}` | Returns one product |
| `GET` | `/api/products/{missingId}` | Returns `404 application/problem+json` |

The request flow is:

```text
HTTP request
    ↓
ProductController
    ↓
ProductService
    ↓
ProductRepository (Spring Data JPA)
    ↓
Product entity / PostgreSQL products table
    ↓
ProductResponse DTO
    ↓
JSON response
```

## 2. Project structure

```text
ecom-backend/
├── book/
│   └── 01-milestone-1-product-read-api.md
├── compose.yaml                    # Fully isolated backend + PostgreSQL
├── compose.existing-db.yaml        # Backend using the supplied PostgreSQL
├── Dockerfile                      # Java 25 build and runtime images
├── pom.xml                         # Maven project and dependencies
├── src/main/java/com/example/ecom/
│   ├── EcomBackendApplication.java
│   ├── api/
│   │   └── ApiExceptionHandler.java
│   └── product/
│       ├── Product.java
│       ├── ProductController.java
│       ├── ProductNotFoundException.java
│       ├── ProductRepository.java
│       ├── ProductResponse.java
│       └── ProductService.java
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/
│       ├── V1__create_products.sql
│       └── V2__seed_products.sql
└── src/test/java/com/example/ecom/product/
    └── ProductControllerTest.java
```

The code is organized by business feature. All product-related layers live together under `product/`. Cross-feature HTTP behavior, such as exception mapping, lives under `api/`.

## 3. Maven and Spring Boot setup

`pom.xml` defines Java 25 and Spring Boot 4.1.1. Spring Boot manages compatible versions for the other dependencies.

The main dependencies are:

- `spring-boot-starter-webmvc`: Spring MVC, JSON serialization, and embedded Tomcat.
- `spring-boot-starter-data-jpa`: Spring Data repositories, JPA, and Hibernate.
- `spring-boot-starter-flyway`: runs database migrations during application startup.
- `flyway-database-postgresql`: PostgreSQL support for Flyway.
- `postgresql`: the PostgreSQL JDBC driver.
- `spring-boot-starter-test`: JUnit, Mockito, MockMvc, and other test utilities.

`@SpringBootApplication` on `EcomBackendApplication` is the application entry point. It enables auto-configuration and scans `com.example.ecom` and its child packages for Spring-managed components.

## 4. Database migrations

Flyway owns the database schema. Hibernate is configured with:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

`validate` means Hibernate checks that the entity matches the schema, but it does not create or modify tables. Schema changes must be explicit Flyway migrations.

### Migration V1: create the table

`V1__create_products.sql` creates `public.products` with these columns:

| Column | Purpose |
| --- | --- |
| `id` | Generated product identity |
| `name` | Display name |
| `description` | Product copy |
| `price` | Decimal monetary amount |
| `currency` | Three-letter currency code |
| `image_url` | Product image location |
| `inventory_quantity` | Available stock |
| `active` | Whether the product is available in the catalog |
| `created_at` | Creation timestamp |
| `updated_at` | Last update timestamp |

### Migration V2: seed data

`V2__seed_products.sql` inserts:

1. Mechanical Keyboard
2. Wireless Mouse
3. USB-C Hub

Flyway also creates `public.flyway_schema_history`. It records which migrations have run and their checksums. Never edit an already-applied migration; add a new migration such as `V3__add_product_category.sql` instead.

## 5. Spring application layers

### Entity

`Product` is annotated with `@Entity` and maps Java fields to the `products` table. It is the persistence model and stays inside the backend.

### Repository

`ProductRepository` extends `JpaRepository<Product, Long>`. Spring Data creates its implementation at runtime, giving us operations such as `findAll` and `findById` without writing routine SQL.

### Service

`ProductService` owns the use cases. It reads from the repository, converts entities to response DTOs, and throws `ProductNotFoundException` when an ID is missing.

`@Transactional(readOnly = true)` defines a read-only transaction boundary for these operations.

### Response DTO

`ProductResponse` is a Java record representing the public JSON contract. Returning a DTO instead of the JPA entity prevents persistence details from accidentally becoming part of the API.

### Controller

`ProductController` maps `/api/products` requests to the service. `@RestController` tells Spring MVC to serialize returned DTOs as JSON.

### Error handling

`ApiExceptionHandler` is a `@RestControllerAdvice`. It translates `ProductNotFoundException` into a standard problem response:

```json
{
  "detail": "Product 999 was not found",
  "instance": "/api/products/999",
  "status": 404,
  "title": "Product not found",
  "type": "https://example.com/problems/product-not-found",
  "productId": 999
}
```

## 6. How Java runs in Docker

The `Dockerfile` has two stages:

1. `maven:3.9.11-eclipse-temurin-25` downloads dependencies, compiles with Java 25, runs tests, and packages the application.
2. `eclipse-temurin:25-jre` runs only the packaged application.

This keeps Java and Maven off the host. It also produces a smaller runtime image without Maven or source code.

Check the Java version inside the built backend image:

```bash
docker compose -f compose.existing-db.yaml run --rm --entrypoint java backend -version
```

## 7. Use the supplied PostgreSQL database

The supplied connection is:

```text
postgresql://postgres:postgres@postgres:5432/ecommerce
```

Spring JDBC uses this equivalent URL:

```text
jdbc:postgresql://postgres:5432/ecommerce
```

The hostname `postgres` is Docker DNS, not a normal host-machine DNS name. The backend must join the same Docker network as that PostgreSQL container. On this machine, that network is `graphql-slardar_default`; `compose.existing-db.yaml` performs that attachment.

From the `ecom-backend` directory, build the application:

```bash
docker compose -f compose.existing-db.yaml build
```

The Docker build runs `mvn verify`, so compilation and tests must succeed before the runtime image is produced.

Start the backend:

```bash
docker compose -f compose.existing-db.yaml up -d
```

Check its state:

```bash
docker compose -f compose.existing-db.yaml ps
```

Follow application logs:

```bash
docker compose -f compose.existing-db.yaml logs -f backend
```

Look for these messages to confirm database initialization:

```text
Database: jdbc:postgresql://postgres:5432/ecommerce
Successfully applied 2 migrations
Started EcomBackendApplication
```

Press `Ctrl+C` to stop following logs; the container continues running.

## 8. Verify the database

Open a temporary PostgreSQL client container on the database network:

```bash
docker run --rm \
  --network graphql-slardar_default \
  postgres:18-alpine \
  psql postgresql://postgres:postgres@postgres:5432/ecommerce
```

At the `psql` prompt, run:

```sql
SELECT current_database(), current_user;
\dt public.*
SELECT id, name, price, currency FROM public.products ORDER BY id;
SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;
```

Exit with:

```text
\q
```

The tables should appear under `ecommerce → Schemas → public → Tables` in pgAdmin. Refresh the `Tables` node if it was already open.

## 9. Verify the HTTP API

The container listens on port `8080`; Docker publishes it as host port `8081` because another application already uses host port `8080`.

List products:

```bash
curl --fail-with-body http://localhost:8081/api/products
```

Get product 1:

```bash
curl --fail-with-body http://localhost:8081/api/products/1
```

Check the 404 response:

```bash
curl --include http://localhost:8081/api/products/999
```

To use another host port:

```bash
APP_PORT=8090 docker compose -f compose.existing-db.yaml up -d
curl http://localhost:8090/api/products
```

## 10. Run tests without installing Java

The normal build already runs the tests:

```bash
docker compose -f compose.existing-db.yaml build
```

For a direct test-only run with the source mounted into a temporary Maven/Java 25 container:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-25 \
  mvn test
```

The endpoint tests cover:

- list response status, JSON shape, and important product fields;
- lookup by ID;
- missing-product status and problem-details body.

## 11. Rebuild after changing code

After editing Java code or migrations:

```bash
docker compose -f compose.existing-db.yaml up --build -d
docker compose -f compose.existing-db.yaml logs -f backend
```

Docker caches dependency layers, so later builds are faster unless `pom.xml` changes.

## 12. Stop and restart

Stop and remove only the backend container and its Compose network attachment:

```bash
docker compose -f compose.existing-db.yaml down
```

This does not stop or delete the supplied PostgreSQL container or its data.

Start the backend again:

```bash
docker compose -f compose.existing-db.yaml up -d
```

Restart it without rebuilding:

```bash
docker compose -f compose.existing-db.yaml restart backend
```

## 13. Optional isolated database

`compose.yaml` provides a separate PostgreSQL container for experiments that must not touch the supplied database:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Its database is also named `ecommerce`, but it is a different PostgreSQL instance. Its data is stored in the Docker volume:

```text
ecom-backend_postgres-data
```

Stop the isolated stack while keeping its data:

```bash
docker compose down
```

Deleting the volume permanently deletes that isolated database:

```bash
docker compose down -v
```

Only use `-v` when an intentional full database reset is desired.

## 14. Useful troubleshooting commands

List running containers:

```bash
docker ps
```

List Docker networks and confirm the database network exists:

```bash
docker network ls
docker network inspect graphql-slardar_default
```

Show the backend's resolved Compose configuration:

```bash
docker compose -f compose.existing-db.yaml config
```

Show recent backend logs:

```bash
docker compose -f compose.existing-db.yaml logs --tail=100 backend
```

If the backend reports `UnknownHostException: postgres`, it is not attached to the database network. Confirm `DB_DOCKER_NETWORK` matches the network containing PostgreSQL:

```bash
DB_DOCKER_NETWORK=graphql-slardar_default \
  docker compose -f compose.existing-db.yaml up -d
```

If port `8081` is already in use, select another one with `APP_PORT`.

If Flyway reports a checksum mismatch, do not delete its history row or edit the old migration. Restore the applied migration and create a new versioned migration for the next change.

## 15. Next milestone

Milestone 2 should extend the catalog while preserving the layers introduced here:

1. Add create, update, and archive product endpoints.
2. Validate request DTOs with Jakarta Bean Validation.
3. Add pagination, sorting, and catalog filters.
4. Add PostgreSQL integration tests.
5. Define a consistent validation-error response.
