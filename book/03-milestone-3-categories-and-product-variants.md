# Milestone 3: Categories and product variants

Catalog writes now require an ADMIN bearer token. Set `ADMIN_TOKEN` to a development admin login token before running the write examples; see [the authentication API](../api-docs/05-authentication-api.md).

This chapter records the original Week 1 catalog change. A later V5 migration adds per-variant stock; see the final section for the current inventory contract. A product is now the catalog page; a variant is the specific item with a SKU and price. Products have one primary category. The existing product list still works, and existing rows retain their SKUs and prices through a Flyway migration.

Java 25 and Maven continue to run in Docker. This milestone does not add a frontend, cart, checkout, orders, authentication, or stock reservation.

## 1. The catalog model

Before this milestone, one `products` row held both the product description and the purchasable SKU and price. That works for a keyboard with one configuration, but a shirt can have several color and size combinations.

```text
Category 1 ──── many Products
Product  1 ──── many ProductVariants
Product  1 ──── one default ProductVariant
```

At V4, the tables had these responsibilities; V5 moves stock to variants as described in Section 10:

| Table | Owns |
| --- | --- |
| `categories` | Stable `slug` and display `name` |
| `products` | Name, description, category, currency, image, shared inventory quantity, product active status, timestamps, and `default_variant_id` |
| `product_variants` | Stable variant ID, product ID, globally unique SKU, price, variant active status, and option selections |

A product with no selectable options has one variant with `options: []`. A configurable product has one variant per combination, such as `Color=Red, Size=S` and `Color=Blue, Size=M`. Color and size are examples, not database columns; the same structure can represent other option names.

The variant ID is suitable for a future cart item to reference. Cart behavior is outside this milestone.

## 2. Preserve existing products with Flyway

[`V4__categories_and_variants.sql`](../src/main/resources/db/migration/V4__categories_and_variants.sql) runs after the existing three migrations. It:

1. Creates `categories` and inserts `uncategorized`.
2. Adds `products.category_id` and assigns every existing row to that category.
3. Creates `product_variants` with SKU, price, active state, options, and uniqueness constraints.
4. Copies **each existing product's actual SKU and price** into one optionless variant.
5. Sets each product's `default_variant_id` to its copied variant.
6. Removes the old product SKU and price columns.

The key data copy is:

```sql
INSERT INTO product_variants (product_id, sku, price, active, options_json, option_signature)
SELECT id, sku, price, active, '[]', '[]' FROM products;
```

The V4 migration does not manufacture replacement SKUs. It uses the values already present when V4 starts. Product IDs and their other fields stay in place. Assigning existing products to `uncategorized` lets `category_id` become required without guessing their real category.

PostgreSQL also enforces:

- unique category slugs and variant SKUs;
- one normalized option signature per product;
- foreign keys from products to categories and from variants to products;
- a composite foreign key that keeps a product's default variant inside that same product;
- nonnegative variant prices and an array-shaped options JSON value.

`products.default_variant_id` is nullable at the database level because creation inserts the product before its first variant, then connects them in the same transaction. The service always creates a default variant for a new product.

## 3. Keep the product API compatible

`GET /api/v1/products` still returns the Milestone 2 page envelope and product fields. A list item does not embed every variant, which keeps pages compact. `GET /api/v1/products/{id}` retains those fields and adds `category`, `defaultVariantId`, and `variants`.

The legacy-looking `sku` and `price` fields are projections of the **default variant**. They are not independently stored or edited on `products`. On a configurable product, the list shows that default variant's SKU and price. `sort=price,asc` or `sort=price,desc` sorts by the same default price, then by product ID to break ties. This is a representative price, not a minimum across all variants.

For example, a shirt with a default Red/S variant priced at 20.00 and a Blue/M variant priced at 24.00 appears in the list with `price: 20.00`. Its detail response exposes both variants and their own prices.

## 4. Normalize option combinations

Variant requests send options as an array:

```json
[
  {"name": "Color", "value": "Red"},
  {"name": "Size", "value": "M"}
]
```

The service trims and lowercases every name and value, rejects blank selections and repeated names, then sorts the entries by name. The stored options and the uniqueness signature therefore become:

```json
[
  {"name": "color", "value": "red"},
  {"name": "size", "value": "m"}
]
```

`Size=M, Color=Red` and ` color = red, size = m ` produce the same signature. A second variant for the same product with that signature returns `409`, even if it has a different SKU. PostgreSQL's `(product_id, option_signature)` constraint also protects against a racing request. SKU uniqueness is global and case-sensitive; SKU text is trimmed before storage.

`options` must be present on variant create or update. Use an empty array only for an optionless variant. The shorthand product create request with `sku` and `price` constructs that empty array itself.

## 5. Search and paginate products without duplicates

`GET /api/v1/products` adds an optional positive `categoryId`. It still accepts `q`, `page`, `size`, and `sort`. The category and search predicates combine with `AND`. Search checks the product name and **every** variant SKU, case-insensitively.

The repository query uses a correlated `EXISTS` condition rather than joining matching variant rows into the paged product result. Conceptually:

```sql
SELECT p.*
FROM products p
JOIN product_variants default_v ON default_v.id = p.default_variant_id
WHERE (:category_id IS NULL OR p.category_id = :category_id)
  AND (
      LOWER(p.name) LIKE :term
      OR EXISTS (
          SELECT 1
          FROM product_variants v
          WHERE v.product_id = p.id
            AND LOWER(v.sku) LIKE :term
      )
  )
ORDER BY default_v.price DESC, p.id ASC
LIMIT :size OFFSET :offset;
```

The actual SQL is generated by Hibernate. `EXISTS` is true once per product, even if several variants match, so `totalCount` counts products and page boundaries remain stable. `ProductRepository` fetches the category and default variant for list rows through an entity graph; it does not load all option lists for the page. Detail fetches one product and its variants in a separate ordered query.

`ProductQuery` still validates page size and sort fields. It also validates `categoryId`, and escapes `%`, `_`, and backslash so `q` remains a literal substring search rather than a user-provided SQL pattern.

## 6. Read and write endpoints

| Method | Path | Result |
| --- | --- | --- |
| `GET` | `/api/v1/categories` | Categories ordered by ID |
| `GET` | `/api/v1/categories/{id}` | Category or 404 |
| `POST` | `/api/v1/categories` | Create a category |
| `PUT` | `/api/v1/categories/{id}` | Change its display name; slug stays fixed |
| `GET` | `/api/v1/products` | Product page with optional category and search filters |
| `GET` | `/api/v1/products/{id}` | Product detail with category and variants |
| `POST` | `/api/v1/products` | Create product and its initial variant or variants |
| `PUT` | `/api/v1/products/{id}` | Update product fields and optionally default SKU/price |
| `POST` | `/api/v1/products/{id}/variants` | Add one variant |
| `PUT` | `/api/v1/products/{id}/variants/{variantId}` | Update one variant |

The previous code implemented product reads, despite the milestone prompt describing existing CRUD. These write endpoints were added in this milestone. Product create accepts either a `sku`/`price` pair for one simple variant or a `variants` array. For the array form, `defaultVariantIndex` selects the default by zero-based position; it defaults to `0`. Product update accepts product fields and may update the default variant by supplying **both** `sku` and `price`. Other variants use their own update endpoint.

The write services use transactions so partial product or variant creation rolls back. They check request values before saving; database constraints remain the final defense against concurrent duplicates.

| Situation | HTTP status | Error shape |
| --- | --- | --- |
| Invalid query or body, blank SKU, negative price, missing/duplicate option selection | `400` | `application/problem+json` |
| Unknown product, category, or variant | `404` | `application/problem+json` |
| Duplicate slug, SKU, or option combination | `409` | `application/problem+json` |

For example, adding `Color=RED, Size=s` when `Color=Red, Size=S` already exists returns:

```json
{
  "detail": "Option combination already exists for product",
  "instance": "/api/v1/products/5/variants",
  "status": 409,
  "title": "Catalog conflict",
  "type": "https://example.com/problems/catalog"
}
```

The older invalid-product-query and missing-product problem responses retain their existing titles and properties.

## 7. Try the API

Start the isolated stack from `ecom-backend/`:

```bash
docker compose up --build -d
```

These examples assume a fresh database. IDs and timestamps will differ elsewhere. Responses below focus on the fields relevant to this chapter; the real product response also includes description, currency, image URL, inventory quantity, active state, and timestamps.

### Create a category and a simple product

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/v1/categories' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"slug":"apparel","name":"Apparel"}'
```

```json
{"id": 2, "slug": "apparel", "name": "Apparel"}
```

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/v1/products' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Cap","description":"Cotton cap","currency":"USD","inventoryQuantity":10,"active":true,"categoryId":2,"sku":"CAP-001","price":15.00}'
```

```json
{
  "id": 4,
  "name": "Cap",
  "sku": "CAP-001",
  "price": 15.00,
  "category": {"id": 2, "slug": "apparel", "name": "Apparel"},
  "defaultVariantId": 4,
  "variants": [
    {"id": 4, "sku": "CAP-001", "price": 15.00, "active": true, "options": []}
  ]
}
```

### Create Red/Blue and S/M variants

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/v1/products' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tee","description":"Cotton tee","currency":"USD","inventoryQuantity":20,"active":true,"categoryId":2,"variants":[{"sku":"TEE-RED-S","price":20.00,"active":true,"options":[{"name":"Color","value":"Red"},{"name":"Size","value":"S"}]},{"sku":"TEE-RED-M","price":20.00,"active":true,"options":[{"name":"Color","value":"Red"},{"name":"Size","value":"M"}]},{"sku":"TEE-BLUE-S","price":22.00,"active":true,"options":[{"name":"Color","value":"Blue"},{"name":"Size","value":"S"}]},{"sku":"TEE-BLUE-M","price":22.00,"active":true,"options":[{"name":"Color","value":"Blue"},{"name":"Size","value":"M"}]}]}'
```

The default is the first variant because `defaultVariantIndex` was omitted. The detail response includes:

```json
{
  "id": 5,
  "name": "Tee",
  "sku": "TEE-RED-S",
  "price": 20.00,
  "category": {"id": 2, "slug": "apparel", "name": "Apparel"},
  "defaultVariantId": 5,
  "variants": [
    {"id": 5, "sku": "TEE-RED-S", "price": 20.00, "active": true, "options": [{"name":"color","value":"red"},{"name":"size","value":"s"}]},
    {"id": 6, "sku": "TEE-RED-M", "price": 20.00, "active": true, "options": [{"name":"color","value":"red"},{"name":"size","value":"m"}]},
    {"id": 7, "sku": "TEE-BLUE-S", "price": 22.00, "active": true, "options": [{"name":"color","value":"blue"},{"name":"size","value":"s"}]},
    {"id": 8, "sku": "TEE-BLUE-M", "price": 22.00, "active": true, "options": [{"name":"color","value":"blue"},{"name":"size","value":"m"}]}
  ]
}
```

### Filter by category and search a variant SKU

```bash
curl --fail-with-body \
  'http://localhost:8081/api/v1/products?categoryId=2&q=blue&page=0&size=10&sort=price,asc'
```

The product occurs once even though two Blue SKUs match:

```json
{
  "items": [{"id": 5, "name": "Tee", "sku": "TEE-RED-S", "price": 20.00}],
  "page": 0,
  "size": 10,
  "totalCount": 1
}
```

The actual list item includes the other Milestone 2 product fields. Its SKU and price describe the default Red/S variant, while the query matched a Blue variant. Fetch `/api/v1/products/5` to see all choices.

## 8. Tests and verification

The tests live in `src/test/java/com/example/ecom/product/`:

- `ProductControllerTest` checks category/search/page parsing and preservation of the existing query and missing-product error shapes.
- `ProductServiceTest` checks page count and default-price sorting behavior, plus duplicate option-key and combination validation.
- `CatalogDatabaseTest` starts Spring with PostgreSQL, applies/validates Flyway, checks migrated SKU and price values, and exercises category filtering, variant SKU search, distinct counts, duplicate combinations, and SKU uniqueness. It runs only when `DB_URL` is set.

A Docker image build runs the tests that do not require a database:

```bash
docker build -t ecom-backend-m3-test .
```

For the full suite, start the isolated PostgreSQL service and run Maven in a Java 25 container on the Compose network:

```bash
docker compose up -d postgres
docker run --rm --network ecom-backend_default \
  -v "$PWD":/workspace -w /workspace \
  -e DB_URL=jdbc:postgresql://postgres:5432/ecommerce \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  maven:3.9.11-eclipse-temurin-25 mvn --batch-mode -q verify
```

For the implementation verification, the same Maven command ran against a fresh temporary PostgreSQL database. The results were:

```text
ProductServiceTest:   3 passed
ProductControllerTest: 3 passed
CatalogDatabaseTest:  2 passed
Total:                8 passed, 0 failures, 0 errors, 0 skipped
```

Live HTTP checks also confirmed migrated seed responses, category creation, a simple product, a two-variant product, category-filtered search, search by a nondefault variant SKU, and `409` for a duplicate normalized option combination.

## 9. Original Week 1 boundary

Inventory quantity still belongs to the product, so all variants share that count. This milestone does not introduce per-variant stock or reservation. It also leaves category trees and later commerce flows for future work.


## 10. Follow-up: stock per variant (V5)

After the original Week 1 implementation, stock moved from the product to each variant. [`V5__move_inventory_to_variants.sql`](../src/main/resources/db/migration/V5__move_inventory_to_variants.sql) adds a nonnegative `product_variants.inventory_quantity`, copies each product's old quantity to its default variant, initializes any other existing variants to `0`, and drops `products.inventory_quantity`. The migration cannot infer how a previously shared count was divided among nondefault variants, so those counts must be entered explicitly when known.

Every variant detail now includes `inventoryQuantity`. The existing top-level product response field remains for compatibility and reads the **default variant's** quantity, just as top-level `sku` and `price` do. It is not the sum of every variant's stock. Product list rows therefore remain efficient: the already-fetched default variant supplies SKU, price, and stock.

A new variant may specify its own nonnegative `inventoryQuantity` in `POST /api/v1/products/{id}/variants`; omission starts it at `0` for older clients. In `PUT /api/v1/products/{id}/variants/{variantId}`, omission preserves its current count. A product create or update request may still send top-level `inventoryQuantity` to set the default variant's stock. On create, if both the top-level value and default variant value are supplied, they must agree. The API guide has current examples for all four Red/Blue and S/M stock counts.

The PostgreSQL-backed tests verify V4-to-V5 migration, preservation of the seeded stock counts, independent stock updates, and the absence of the old product stock column. This feature tracks quantities; it does not reserve or decrement stock for carts or orders.
