# Run the ecommerce backend with Docker

Run these commands from the `ecom-backend/` directory. The project uses Java 25 and Maven **inside Docker**; the host needs Docker Engine, the Docker Compose plugin, and `curl` for the HTTP examples. The API listens on container port `8080`. The supplied Compose files publish it on host port `8081` by default.

Choose either the isolated Compose stack or the existing-database Compose stack for a run. Both files use the same Compose project name, so stop one before switching to the other.

The Dockerfile has two stages: `maven:3.9.11-eclipse-temurin-25` builds the JAR and runs `mvn --batch-mode verify`; `eclipse-temurin:25-jre` runs the packaged application. Flyway applies database migrations when the backend starts, and Hibernate validates the resulting schema.

## 1. Recommended: isolated PostgreSQL and backend with Compose

This path uses [`compose.yaml`](../compose.yaml). It creates a project network, a PostgreSQL 18 container, a backend container, and a persistent `postgres-data` volume. PostgreSQL is internal to the Compose network; only the backend HTTP port is published to the host.

```bash
# Run from ecom-backend/
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 backend
```

Compose waits for PostgreSQL's health check before starting the backend. During backend startup, look for Flyway migration and Spring Boot startup messages. Then call the API:

```bash
curl --fail-with-body 'http://localhost:8081/api/v1/products'
curl --fail-with-body 'http://localhost:8081/api/v1/categories'
curl --fail-with-body 'http://localhost:8081/api/v1/products/1'
```

To change the host port or local database credentials, copy the example environment file and edit it before starting the stack:

```bash
cp .env.example .env
```

| Variable | Default | Used for |
| --- | --- | --- |
| `APP_PORT` | `8081` | Host port mapped to backend port `8080` |
| `POSTGRES_DB` | `ecommerce` | Database created by the PostgreSQL container |
| `POSTGRES_USER` | `postgres` | PostgreSQL user |
| `POSTGRES_PASSWORD` | `postgres` | PostgreSQL password |
| `DB_URL` | `jdbc:postgresql://postgres:5432/ecommerce` | JDBC URL **inside** the Docker network |
| `DB_USERNAME` | `postgres` | Backend database user |
| `DB_PASSWORD` | `postgres` | Backend database password |

Keep `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` consistent with the PostgreSQL settings. In `DB_URL`, `postgres` is the Compose service DNS name, not `localhost`. Compose reads `.env` for variable substitution; the backend receives the explicit `environment` values in `compose.yaml`. The `.env` file is ignored by Git and the Docker build context.

### Routine Compose commands

```bash
# Show container status and published ports
docker compose ps

# Follow backend logs; Ctrl+C stops following, not the container
docker compose logs -f backend

# Inspect PostgreSQL startup or health-check logs
docker compose logs --tail=100 postgres

# Rebuild the backend image and recreate the backend container after code changes
docker compose up --build -d backend

# Restart without rebuilding
docker compose restart backend

# Stop and remove this stack's containers and network; keep database volume
docker compose down

# Start again with the existing database volume
docker compose up -d
```

`docker compose down` keeps the named PostgreSQL volume, so existing rows survive the next start. `docker compose down -v` also deletes that volume and its database contents; use it only when you intend to reset the isolated local database.

### Inspect the local database

```bash
# Open psql inside the running PostgreSQL service
docker compose exec postgres psql -U postgres -d ecommerce
```

At the `psql` prompt:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT p.id, p.name, v.id AS variant_id, v.sku, v.price, v.inventory_quantity
FROM products p
JOIN product_variants v ON v.id = p.default_variant_id
ORDER BY p.id;
```

Leave `psql` with `\q`. If you changed `POSTGRES_USER` or `POSTGRES_DB`, use those values in the `psql` command.

## 2. Use an already-running PostgreSQL container

[`compose.existing-db.yaml`](../compose.existing-db.yaml) starts **only** the backend. It expects an external Docker network named `graphql-slardar_default` with a PostgreSQL host resolvable as `postgres` on port `5432`, database `ecommerce`, and the credentials shown in the file.

```bash
docker network ls
docker network inspect graphql-slardar_default
docker compose -f compose.existing-db.yaml build
docker compose -f compose.existing-db.yaml up -d
docker compose -f compose.existing-db.yaml ps
docker compose -f compose.existing-db.yaml logs --tail=100 backend
curl --fail-with-body 'http://localhost:8081/api/v1/products'
```

If the external network has another name, set `DB_DOCKER_NETWORK` before `up`, for example:

```bash
DB_DOCKER_NETWORK=my-existing-network docker compose -f compose.existing-db.yaml up -d
```

`APP_PORT` also works here. The existing-database Compose file currently hardcodes `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; changing those values in `.env` alone does **not** change this file's connection. Edit `compose.existing-db.yaml` or use the isolated `compose.yaml` for different database settings.

Stop only this backend container and its Compose network attachment with:

```bash
docker compose -f compose.existing-db.yaml down
```

This does not stop or remove the external PostgreSQL container or its network.

For the authenticated cart API, register and log in, then use the returned bearer token with `/api/v1/cart`; see the [full cart flow](../api-docs/06-customer-cart-api.md). Flyway V8 adds customer cart ownership and stable cart item IDs when the updated backend starts.

## 3. Run with plain `docker` commands

The following path recreates the isolated stack without Compose. Container and network names are examples; these commands assume they are not already in use.

```bash
# Build from the project Dockerfile; the build runs Maven verification
docker build -t ecom-backend:local .

# Give the containers a private network and persistent database volume
docker network create ecom-local
docker volume create ecom-postgres-data

# Run PostgreSQL 18
docker run -d --name ecom-postgres --network ecom-local \
  -e POSTGRES_DB=ecommerce \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -v ecom-postgres-data:/var/lib/postgresql \
  postgres:18-alpine

# Wait until PostgreSQL accepts connections
docker exec ecom-postgres pg_isready -U postgres -d ecommerce

# Run the API after PostgreSQL is ready
docker run -d --name ecom-backend --network ecom-local \
  -p 8081:8080 \
  -e DB_URL=jdbc:postgresql://ecom-postgres:5432/ecommerce \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  ecom-backend:local
```

If `pg_isready` reports that PostgreSQL is not ready, run that check again before starting the backend. The JDBC hostname is the PostgreSQL **container name** on `ecom-local`; `localhost` inside the backend container would refer to the backend container itself.

Inspect and stop this plain-Docker setup:

```bash
docker ps
docker logs -f ecom-backend
docker logs --tail=100 ecom-postgres
curl --fail-with-body 'http://localhost:8081/api/v1/products'

docker stop ecom-backend ecom-postgres
docker rm ecom-backend ecom-postgres
docker network rm ecom-local
```

Those cleanup commands keep `ecom-postgres-data`. To intentionally discard its database, run `docker volume rm ecom-postgres-data` after removing the containers. To restart containers without removing them, use `docker start ecom-postgres`, wait for `pg_isready`, then use `docker start ecom-backend`.

## 4. Build and test commands

A normal Docker build runs Maven `verify` in the build stage:

```bash
docker build -t ecom-backend:local .
```

The repository's `CatalogDatabaseTest` runs only when `DB_URL` is set. Without it, the Docker build runs the controller and service tests and skips the two PostgreSQL-backed tests. To run **all** tests using the isolated Compose database:

```bash
docker compose up -d postgres

docker run --rm --network ecom-backend_default \
  -v "$PWD":/workspace -w /workspace \
  -e DB_URL=jdbc:postgresql://postgres:5432/ecommerce \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  maven:3.9.11-eclipse-temurin-25 mvn --batch-mode verify
```

`ecom-backend_default` is the default Compose network name when the project directory is named `ecom-backend`; change it if you use a custom Compose project name. The database tests create data inside test transactions and roll it back. On the verified Week 1 code, all eight tests passed with PostgreSQL; without `DB_URL`, six passed and two were skipped.

## 5. Useful diagnostics

| Goal | Command |
| --- | --- |
| Check Compose file and substituted values | `docker compose config` |
| List service names | `docker compose config --services` |
| Show backend logs | `docker compose logs --tail=100 backend` |
| Follow backend logs | `docker compose logs -f backend` |
| Show container state | `docker compose ps` or `docker ps -a` |
| Inspect backend port mapping | `docker compose port backend 8080` |
| Check PostgreSQL health | `docker compose exec postgres pg_isready -U postgres -d ecommerce` |
| See Flyway migration history | `docker compose exec postgres psql -U postgres -d ecommerce -c 'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank'` |
| Inspect the local database volume | `docker volume inspect ecom-backend_postgres-data` |

If the backend stops during startup, read `docker compose logs backend` first. Database connection errors usually mean PostgreSQL is unavailable, the container hostname is wrong for the selected network, or the credentials differ. A Flyway validation error means the migration history and files differ; check `flyway_schema_history` rather than modifying an already-applied migration.

For endpoint payloads and errors, use the [product read API](../api-docs/products-api.md), [categories and variants API](../api-docs/03-categories-and-product-variants-api.md), [cart API](../api-docs/04-carts-api.md), and [authentication API](../api-docs/05-authentication-api.md) references.

## Try the cart API

After starting the backend, create a cart and copy its UUID from the response:

```bash
curl --fail-with-body -X POST http://localhost:8081/api/v1/carts
curl --fail-with-body http://localhost:8081/api/v1/products/1
```

Use an ID from the product's `variants` array, including for a product with only one variant. For example, if the variant ID is `1`:

```bash
curl --fail-with-body -X POST http://localhost:8081/api/v1/carts/<uuid>/items \
  -H 'Content-Type: application/json' \
  -d '{"variantId":1,"quantity":1}'
curl --fail-with-body http://localhost:8081/api/v1/carts/<uuid>
```

The V6 migration creates `carts` and `cart_items` automatically on backend startup. It leaves product and variant stock unchanged.

## Authentication settings

Before starting either Compose configuration, create an untracked `.env` file with a base64 random `JWT_SECRET` and a nonblank `JWT_ISSUER`. For example, generate a key with `openssl rand -base64 32`, then copy it into `.env`. `JWT_EXPIRATION_SECONDS` defaults to `900`. Missing or malformed settings stop the backend at startup. See [the authentication guide](../api-docs/05-authentication-api.md) for registration, login, bearer requests, and development admin provisioning.
