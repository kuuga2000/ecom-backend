# Ecommerce backend

Milestones 1 through 5 provide a Java 25 / Spring Boot 4.1.1 ecommerce REST API backed by PostgreSQL. Java and Maven run in Docker; no host JDK installation is needed.

Step-by-step walkthroughs:

- [`book/01-milestone-1-product-read-api.md`](book/01-milestone-1-product-read-api.md)
- [`book/02-milestone-2-product-search-and-pagination.md`](book/02-milestone-2-product-search-and-pagination.md)
- [`book/03-milestone-3-categories-and-product-variants.md`](book/03-milestone-3-categories-and-product-variants.md)
- [`book/04-variant-selection-and-carts.md`](book/04-variant-selection-and-carts.md)
- [`book/05-milestone-4-customer-authentication.md`](book/05-milestone-4-customer-authentication.md)
- [`book/06-milestone-5-customer-cart.md`](book/06-milestone-5-customer-cart.md)

API references: [product reads](api-docs/products-api.md), [categories, variants, and writes](api-docs/03-categories-and-product-variants-api.md), [carts](api-docs/04-carts-api.md), [authentication](api-docs/05-authentication-api.md), [customer carts](api-docs/06-customer-cart-api.md), and the [complete API v1 route inventory](api-docs/07-api-versioning.md).

Docker commands and troubleshooting: [Docker guide](docker-docs/README.md).

All frontend-facing endpoints use the `/api/v1` prefix. The `v1` value identifies the API contract, not a milestone.

## Proposed backend shape

The code is organized by feature rather than by global technical layer:

```text
src/main/java/com/example/ecom/
├── EcomBackendApplication.java
├── api/                         # Cross-feature HTTP concerns
├── auth/                        # Customer accounts and security
├── cart/                        # Anonymous and customer cart APIs
└── product/                     # Catalog API
src/main/resources/
├── application.yaml
└── db/migration/                # Versioned Flyway SQL
```

The backend now has categories, products, product variants, anonymous carts, customer accounts, and authenticated customer carts. Products store catalog copy, currency, active state, and audit timestamps; variants store SKU, price, inventory quantity, options, and variant active state. Product-level `inventoryQuantity` in API responses is read from the default variant. Likely later tables are `addresses`, `orders`, `order_items`, and `payments`.

Initial endpoints:

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/v1/products` | Searchable, sortable page of products |
| `GET` | `/api/v1/products/{id}` | One product with category and variants, or an RFC 9457-style `404` problem response |
| `GET` | `/api/v1/categories` | All categories |
| `GET` | `/api/v1/categories/{id}` | One category |

The collection endpoint accepts `q`, `categoryId`, `page`, `size`, and `sort`. Defaults are `page=0`, `size=20`, and `sort=name,asc`.

Proposed milestones:

1. Product read API (implemented).
2. Product search, pagination, and sorting (implemented).
3. Categories and product variants, with create/update operations (implemented).
4. Customer authentication and authorization (implemented).
5. Authenticated persistent carts (implemented); checkout and stock reservation remain planned.
6. Orders and payment-provider integration.
7. Observability, security hardening, integration tests, and deployment configuration.
8. React storefront after the backend contract is ready.

## Run it with the supplied existing database

Prerequisites: Docker and Docker Compose only.

The supplied database is available as `postgres` on Docker network `graphql-slardar_default`. Set `JWT_SECRET` (base64 random key) and `JWT_ISSUER` in an untracked `.env` file first; see the [authentication guide](api-docs/05-authentication-api.md). Build the Java 25 image (the build runs the tests), then start the API on that network:

```bash
docker compose -f compose.existing-db.yaml build
docker compose -f compose.existing-db.yaml up -d
docker compose -f compose.existing-db.yaml ps
```

The backend uses the requested connection internally:

```bash
postgresql://postgres:postgres@postgres:5432/ecommerce
```

`postgres` is Docker's service DNS name and resolves because the backend joins the database's existing Docker network. Override `DB_DOCKER_NETWORK` if that network is renamed.

To use another PostgreSQL database with the isolated `compose.yaml`, create `.env` from `.env.example` and change these values before starting Compose. The `compose.existing-db.yaml` file hardcodes its database connection; edit that file for a different external database:

```bash
DB_URL=jdbc:postgresql://postgres:5432/ecommerce
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

Check the endpoints from another terminal:

```bash
curl --fail-with-body http://localhost:8081/api/v1/products
curl --fail-with-body 'http://localhost:8081/api/v1/products?q=mouse'
curl --fail-with-body 'http://localhost:8081/api/v1/products?page=0&size=2&sort=price,desc'
curl --fail-with-body http://localhost:8081/api/v1/products/1
curl --include http://localhost:8081/api/v1/products/999
```

Port `8081` avoids colliding with another service already using this machine's port `8080`; set `APP_PORT` to change it. Stop the backend:

```bash
docker compose -f compose.existing-db.yaml down
```

## Optional isolated local database

The base `compose.yaml` still provides a project-owned PostgreSQL service for isolated local development. Its data lives in the named Docker volume `ecom-backend_postgres-data`:

```bash
docker compose up --build -d
```

## Spring Boot concepts in this milestone

- `@SpringBootApplication` is the composition root. It enables auto-configuration and component scanning beneath `com.example.ecom`.
- Spring sees `@RestController`, `@Service`, and `@RestControllerAdvice` classes and manages them as beans. Constructor injection makes dependencies explicit and easy to test.
- Spring MVC maps HTTP requests to controller methods and serializes Java records such as `ProductResponse` to JSON.
- Spring Data JPA generates the `ProductRepository` implementation at runtime; Hibernate maps `Product` to a row. The API returns a DTO so its contract is not coupled directly to the persistence model.
- `@Transactional(readOnly = true)` gives service reads a transaction boundary and tells the persistence provider no writes are expected.
- Flyway runs ordered migrations at startup. Hibernate uses `ddl-auto: validate`, so migration SQL—not ORM schema generation—owns the database.
- Externalized `${...}` configuration keeps credentials out of source control and lets the same artifact connect to local PostgreSQL or a hosted provider.
- `ProblemDetail` produces the standard `application/problem+json` error shape, while the controller advice maps domain exceptions to HTTP concerns.
