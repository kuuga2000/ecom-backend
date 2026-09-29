# Milestone 2: Product search, pagination, and sorting

Milestone 2 extends the product collection endpoint while preserving `GET /api/v1/products/{id}`. It adds case-insensitive name/SKU search, zero-based pagination, safe sorting, a consistent page response, structured parameter errors, and a Flyway migration for SKUs.

Java and Maven continue to run entirely in Docker.

## 1. API contract

`GET /api/v1/products` now accepts these optional query parameters:

| Parameter | Default | Rules |
| --- | --- | --- |
| `q` | no filter | Case-insensitive substring search across product name and SKU |
| `page` | `0` | Zero-based integer; must be at least `0` |
| `size` | `20` | Integer from `1` through `100` |
| `sort` | `name,asc` | Field is `name` or `price`; direction is `asc` or `desc` |

The response always has the same envelope:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalCount": 0
}
```

The existing item endpoint is unchanged apart from products now including their SKU:

```text
GET /api/v1/products/{id}
```

## 2. Files added or changed

```text
src/main/java/com/example/ecom/
├── api/
│   └── ApiExceptionHandler.java              # Added query-error mapping
└── product/
    ├── InvalidProductQueryException.java      # Invalid parameter details
    ├── Product.java                           # Added SKU mapping
    ├── ProductController.java                 # Reads query parameters
    ├── ProductPageResponse.java               # Page JSON envelope
    ├── ProductQuery.java                      # Parsing and validation
    ├── ProductRepository.java                 # Search query derivation
    ├── ProductResponse.java                   # Exposes SKU
    └── ProductService.java                    # Page/search orchestration
src/main/resources/db/migration/
└── V3__add_product_sku.sql
src/test/java/com/example/ecom/product/
├── ProductControllerTest.java
└── ProductServiceTest.java
```

## 3. Add SKU with Flyway

The original schema had no SKU, but the requested search must inspect name or SKU. Migration `V3__add_product_sku.sql` performs four steps:

1. Add a temporarily nullable `sku` column.
2. Backfill every existing row with a SKU.
3. Make `sku` non-null.
4. Add a unique index.

The migration uses this shape:

```sql
ALTER TABLE products ADD COLUMN sku VARCHAR(100);

UPDATE products
SET sku = CASE name
    WHEN 'Mechanical Keyboard' THEN 'KEY-MECH-001'
    WHEN 'Wireless Mouse' THEN 'MOU-WLS-001'
    WHEN 'USB-C Hub' THEN 'HUB-USBC-001'
    ELSE 'PRODUCT-' || id
END;

ALTER TABLE products ALTER COLUMN sku SET NOT NULL;
CREATE UNIQUE INDEX uq_products_sku ON products (sku);
```

Adding the constraint only after the backfill prevents existing rows from violating `NOT NULL`. The fallback based on ID also makes every backfilled SKU unique.

Flyway applied the migration to the supplied database:

```text
Current version of schema "public": 2
Migrating schema "public" to version "3 - add product sku"
Successfully applied 1 migration ... now at version v3
```

Verify the schema and data:

```bash
docker run --rm \
  --network graphql-slardar_default \
  postgres:18-alpine \
  psql postgresql://postgres:postgres@postgres:5432/ecommerce \
  -c 'SELECT id, sku, name FROM products ORDER BY id;'
```

Expected rows:

```text
1 | KEY-MECH-001 | Mechanical Keyboard
2 | MOU-WLS-001  | Wireless Mouse
3 | HUB-USBC-001 | USB-C Hub
```

## 4. Parse and validate query parameters

`ProductController` accepts parameter values as strings and passes them through `ProductQuery.from(...)`. Parsing them ourselves gives every invalid value the same problem-details response instead of relying on several different framework binding errors.

`ProductQuery` is responsible for:

- trimming `q` and treating a blank value as no filter;
- parsing `page` and `size` as integers;
- enforcing `page >= 0`;
- enforcing `1 <= size <= 100`;
- allowing only `name` and `price` sort fields;
- allowing only `asc` and `desc` directions;
- creating Spring Data's `PageRequest`.

An ID sort is appended after the requested sort:

```java
Sort.by(direction, field)
        .and(Sort.by(Sort.Direction.ASC, "id"));
```

This makes page boundaries deterministic when multiple products have the same name or price. Without a tie-breaker, PostgreSQL may return equal-valued rows in a different order between requests.

## 5. Spring Data repository search

`ProductRepository` declares:

```java
Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(
        String name,
        String sku,
        Pageable pageable
);
```

Spring Data parses this method name:

- `NameContainingIgnoreCase` becomes a case-insensitive substring predicate on `name`.
- `Or` combines the two predicates.
- `SkuContainingIgnoreCase` applies the same behavior to `sku`.
- `Pageable` contributes ordering, offset, and limit.
- `Page<Product>` asks Spring Data to also calculate the total matching row count.

No repository implementation class is required; Spring Data creates it at startup.

## 6. How pagination works

`PageRequest.of(page, size, sort)` implements Spring Data's `Pageable` interface.

For this request:

```text
GET /api/v1/products?q=key&page=1&size=10&sort=price,desc
```

the conceptual offset is:

```text
offset = page × size = 1 × 10 = 10
```

Because the repository returns `Page<Product>`, Spring Data normally executes two SQL queries.

The data query is equivalent to:

```sql
SELECT p.*
FROM products p
WHERE UPPER(p.name) LIKE UPPER('%key%')
   OR UPPER(p.sku) LIKE UPPER('%key%')
ORDER BY p.price DESC, p.id ASC
OFFSET 10 ROWS
FETCH FIRST 10 ROWS ONLY;
```

The count query is equivalent to:

```sql
SELECT COUNT(p.id)
FROM products p
WHERE UPPER(p.name) LIKE UPPER('%key%')
   OR UPPER(p.sku) LIKE UPPER('%key%');
```

Exact aliases, escaping, and pagination syntax are generated by Hibernate and can differ slightly, but the semantics are the same.

The first query supplies `items`. The second supplies `totalCount`. `page` and `size` come from the resulting Spring Data `Page`.

A `Slice` would avoid the count query, but it could only report whether another slice exists. The API contract requires a total count, so `Page` is appropriate here.

The current search uses `%term%`. A normal B-tree index cannot efficiently accelerate arbitrary leading-wildcard searches. That is acceptable for the learning-sized catalog. A larger PostgreSQL catalog could add `pg_trgm` GIN indexes or use a dedicated search system after measuring real queries.

## 7. Service and response mapping

`ProductService.findAll(ProductQuery)` selects one of two repository paths:

```text
q has text  → search name or SKU with Pageable
q is absent → find all with Pageable
```

`ProductPageResponse.from(Page<Product>)` converts database entities into API DTOs and exposes only:

```json
{
  "items": ["ProductResponse objects"],
  "page": 0,
  "size": 20,
  "totalCount": 3
}
```

The API does not serialize Spring Data's `PageImpl` directly. A dedicated response record keeps the public JSON stable if Spring Data's internal representation changes.

## 8. Invalid parameter responses

Invalid parameters throw `InvalidProductQueryException`. `ApiExceptionHandler` maps it to `400 application/problem+json`.

For example:

```bash
curl --include 'http://localhost:8081/api/v1/products?sort=createdAt,desc'
```

Response:

```http
HTTP/1.1 400
Content-Type: application/problem+json
```

```json
{
  "detail": "sort field must be name or price",
  "instance": "/api/v1/products",
  "status": 400,
  "title": "Invalid product query",
  "type": "https://example.com/problems/invalid-product-query",
  "parameter": "sort",
  "rejectedValue": "createdAt,desc"
}
```

Other rejected examples:

```bash
curl --include 'http://localhost:8081/api/v1/products?page=-1'
curl --include 'http://localhost:8081/api/v1/products?page=abc'
curl --include 'http://localhost:8081/api/v1/products?size=0'
curl --include 'http://localhost:8081/api/v1/products?size=101'
curl --include 'http://localhost:8081/api/v1/products?sort=price,sideways'
```

## 9. Build and run Milestone 2

From `ecom-backend/`, build using Java 25 and run all tests:

```bash
docker compose -f compose.existing-db.yaml build
```

Start or recreate the backend on the supplied database network:

```bash
docker compose -f compose.existing-db.yaml up -d
```

Check startup and Flyway:

```bash
docker compose -f compose.existing-db.yaml logs --tail=100 backend
```

Check container state:

```bash
docker compose -f compose.existing-db.yaml ps
```

## 10. Example requests and responses

### Default page

```bash
curl --fail-with-body 'http://localhost:8081/api/v1/products'
```

The default is page `0`, size `20`, sorted by name ascending and then ID ascending:

```json
{
  "items": [
    {"id": 1, "name": "Mechanical Keyboard", "sku": "KEY-MECH-001", "price": 129.00},
    {"id": 3, "name": "USB-C Hub", "sku": "HUB-USBC-001", "price": 79.50},
    {"id": 2, "name": "Wireless Mouse", "sku": "MOU-WLS-001", "price": 59.90}
  ],
  "page": 0,
  "size": 20,
  "totalCount": 3
}
```

The actual product objects also include description, currency, image URL, inventory, active state, and timestamps.

### Search by name

```bash
curl --fail-with-body 'http://localhost:8081/api/v1/products?q=mouse'
```

```json
{
  "items": [
    {"id": 2, "name": "Wireless Mouse", "sku": "MOU-WLS-001", "price": 59.90}
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1
}
```

Search is case-insensitive, so `q=MOUSE` produces the same match.

### Search by SKU

```bash
curl --fail-with-body 'http://localhost:8081/api/v1/products?q=hub-usbc'
```

```json
{
  "items": [
    {"id": 3, "name": "USB-C Hub", "sku": "HUB-USBC-001", "price": 79.50}
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1
}
```

### Page and sort by price descending

```bash
curl --fail-with-body \
  'http://localhost:8081/api/v1/products?page=0&size=2&sort=price,desc'
```

```json
{
  "items": [
    {"id": 1, "name": "Mechanical Keyboard", "sku": "KEY-MECH-001", "price": 129.00},
    {"id": 3, "name": "USB-C Hub", "sku": "HUB-USBC-001", "price": 79.50}
  ],
  "page": 0,
  "size": 2,
  "totalCount": 3
}
```

Request the next page:

```bash
curl --fail-with-body \
  'http://localhost:8081/api/v1/products?page=1&size=2&sort=price,desc'
```

### Existing ID endpoint

```bash
curl --fail-with-body 'http://localhost:8081/api/v1/products/1'
```

```json
{
  "id": 1,
  "name": "Mechanical Keyboard",
  "sku": "KEY-MECH-001",
  "price": 129.00,
  "currency": "USD",
  "inventoryQuantity": 24,
  "active": true
}
```

The actual response also includes description, image URL, and timestamps.

## 11. Tests

The Docker build completed with:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

`ProductControllerTest` covers:

- consistent page-envelope JSON;
- normalized search and requested page/size;
- price-descending sort and stable ID tie-breaker;
- unsupported sort fields;
- negative, nonnumeric, and oversized pagination values;
- the existing ID lookup;
- the existing 404 response.

`ProductServiceTest` covers:

- selecting the case-insensitive name/SKU repository search;
- forwarding `Pageable` to Spring Data;
- mapping Spring Data's page metadata;
- using the unfiltered repository path for a blank search.

Run the suite again without installing Java:

```bash
docker compose -f compose.existing-db.yaml build
```

## 12. Stop here

Milestone 2 is complete. No frontend was created.

A sensible next backend milestone is product administration: request DTO validation, create/update/archive operations, SKU conflict handling, and PostgreSQL-backed integration tests.
