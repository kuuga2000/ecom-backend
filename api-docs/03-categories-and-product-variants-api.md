# Categories and product variants API

This is the Milestone 3 API reference, including the subsequent V5 variant-stock update. It covers category reads and writes, product creation and updates, variant creation and updates, and the additions to product search and detail. The original list and ID read examples remain in [Products API](products-api.md).

Base URL for the supplied Compose configuration: `http://localhost:8081`. Successful responses use `application/json`. Errors use `application/problem+json`. Request examples use JSON and assume a fresh database; generated IDs and timestamps vary. Sections illustrate independent requests: later examples assume category 2 is still named `Apparel`, even if you tried the rename example.

## Endpoint map

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/categories` | `200` | List categories in ID order |
| `GET` | `/api/categories/{id}` | `200` | Get one category |
| `POST` | `/api/categories` | `201` | Create a category |
| `PUT` | `/api/categories/{id}` | `200` | Rename a category |
| `GET` | `/api/products` | `200` | Search and page products, optionally by category |
| `GET` | `/api/products/{id}` | `200` | Get product detail with variants |
| `POST` | `/api/products` | `201` | Create product and its initial variants |
| `PUT` | `/api/products/{id}` | `200` | Update product fields and optionally the default SKU and price |
| `POST` | `/api/products/{id}/variants` | `201` | Add a variant to a product |
| `PUT` | `/api/products/{id}/variants/{variantId}` | `200` | Update a variant belonging to that product |

There are no category or variant delete endpoints in this milestone.

## Response objects and compatibility

A category is:

```json
{"id": 2, "slug": "apparel", "name": "Apparel"}
```

A variant is:

```json
{
  "id": 5,
  "sku": "TEE-RED-S",
  "price": 20.00,
  "active": true,
  "inventoryQuantity": 20,
  "options": [
    {"name": "color", "value": "red"},
    {"name": "size", "value": "s"}
  ]
}
```

Variant IDs are stable and are the IDs a future cart can reference. Each variant owns its SKU, price, active flag, option selections, and `inventoryQuantity`. Products no longer store stock.

The existing product-level `sku`, `price`, and `inventoryQuantity` response fields come from the product's **default variant**. They are not separate stored values. On a product with several variants, the list displays the default variant's SKU and price; `sort=price` orders by that price, then product ID for ties. A product detail response adds these fields to the original product fields:

```json
{
  "category": {"id": 2, "slug": "apparel", "name": "Apparel"},
  "defaultVariantId": 5,
  "variants": [{"id": 5, "sku": "TEE-RED-S", "price": 20.00, "active": true, "inventoryQuantity": 20, "options": []}]
}
```

The fragment above shows the added fields, not a standalone response. A full detail response appears below. The `GET /api/products` page and list item shapes remain the same as Milestone 2; the list does not embed categories or variants.

## Categories

### List and get

```bash
curl --fail-with-body 'http://localhost:8081/api/categories'
curl --fail-with-body 'http://localhost:8081/api/categories/2'
```

The list is a JSON array ordered by category ID. After creating `apparel`, the responses are:

```json
[
  {"id": 1, "slug": "uncategorized", "name": "Uncategorized"},
  {"id": 2, "slug": "apparel", "name": "Apparel"}
]
```

```json
{"id": 2, "slug": "apparel", "name": "Apparel"}
```

All existing products were assigned to `uncategorized` by the V4 migration. `GET /api/categories/{id}` returns `404` for an unknown ID.

### Create

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/categories' \
  -H 'Content-Type: application/json' \
  -d '{"slug":"apparel","name":"Apparel"}'
```

Status: `201 Created`. Body:

```json
{"id": 2, "slug": "apparel", "name": "Apparel"}
```

`slug` and `name` are required. The slug is trimmed, lowercased, at most 100 characters, and must match lowercase letters or digits separated by single hyphens, such as `home-office`. The display name is trimmed, nonblank, and at most 200 characters. An existing slug returns `409`.

### Rename

```bash
curl --fail-with-body -X PUT 'http://localhost:8081/api/categories/2' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Clothing"}'
```

Status: `200 OK`. Body:

```json
{"id": 2, "slug": "apparel", "name": "Clothing"}
```

The slug is immutable. A `slug` field may be omitted or supplied with its exact current value. Supplying a different slug returns `400`.

## Products

### List, search, filter, and sort

```http
GET /api/products?q=blue&categoryId=2&page=0&size=10&sort=price,asc
```

| Parameter | Default | Behavior |
| --- | --- | --- |
| `q` | no search | Trimmed, case-insensitive literal substring of product name or **any** variant SKU; blank means no search |
| `categoryId` | all categories | Positive integer; an unknown but valid ID returns an empty page |
| `page` | `0` | Zero-based integer, at least 0 |
| `size` | `20` | Integer from 1 through 100 |
| `sort` | `name,asc` | `name` or default-variant `price`; `asc` or `desc`; direction may be omitted |

Filters combine. The page count is the number of **distinct products**, even when two or more variants match `q`. Sorting uses product ID as a stable tie-breaker.

For a Tee with two Blue variants, this request returns the Tee once:

```bash
curl --fail-with-body \
  'http://localhost:8081/api/products?categoryId=2&q=blue&page=0&size=10&sort=price,asc'
```

```json
{
  "items": [
    {
      "id": 5,
      "name": "Tee",
      "sku": "TEE-RED-S",
      "description": "Cotton tee",
      "price": 20.00,
      "currency": "USD",
      "imageUrl": null,
      "inventoryQuantity": 20,
      "active": true,
      "createdAt": "2026-09-29T00:00:00Z",
      "updatedAt": "2026-09-29T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalCount": 1
}
```

The list shows `TEE-RED-S` because that is the default variant. The Blue SKUs still make the product match. Timestamps above are illustrative.

### Get detail

```bash
curl --fail-with-body 'http://localhost:8081/api/products/5'
```

```json
{
  "id": 5,
  "name": "Tee",
  "sku": "TEE-RED-S",
  "description": "Cotton tee",
  "price": 20.00,
  "currency": "USD",
  "imageUrl": null,
  "inventoryQuantity": 20,
  "active": true,
  "createdAt": "2026-09-29T00:00:00Z",
  "updatedAt": "2026-09-29T00:00:00Z",
  "category": {"id": 2, "slug": "apparel", "name": "Apparel"},
  "defaultVariantId": 5,
  "variants": [
    {"id": 5, "sku": "TEE-RED-S", "price": 20.00, "active": true,
     "inventoryQuantity": 20, "options": [{"name":"color","value":"red"},{"name":"size","value":"s"}]},
    {"id": 6, "sku": "TEE-RED-M", "price": 20.00, "active": true,
     "inventoryQuantity": 10, "options": [{"name":"color","value":"red"},{"name":"size","value":"m"}]},
    {"id": 7, "sku": "TEE-BLUE-S", "price": 22.00, "active": true,
     "inventoryQuantity": 8, "options": [{"name":"color","value":"blue"},{"name":"size","value":"s"}]},
    {"id": 8, "sku": "TEE-BLUE-M", "price": 22.00, "active": true,
     "inventoryQuantity": 5, "options": [{"name":"color","value":"blue"},{"name":"size","value":"m"}]}
  ]
}
```

An unknown product ID retains the existing `404` problem response with title `Product not found` and a `productId` property.

### Create a simple product

Send the product fields plus a single `sku` and `price`. The service creates one active, optionless default variant:

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/products' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Cap","description":"Cotton cap","currency":"USD","imageUrl":null,"inventoryQuantity":10,"active":true,"categoryId":2,"sku":"CAP-001","price":15.00}'
```

Status: `201 Created`. Body:

```json
{
  "id": 4,
  "name": "Cap",
  "sku": "CAP-001",
  "description": "Cotton cap",
  "price": 15.00,
  "currency": "USD",
  "imageUrl": null,
  "inventoryQuantity": 10,
  "active": true,
  "createdAt": "2026-09-29T00:00:00Z",
  "updatedAt": "2026-09-29T00:00:00Z",
  "category": {"id": 2, "slug": "apparel", "name": "Apparel"},
  "defaultVariantId": 4,
  "variants": [{"id": 4, "sku": "CAP-001", "price": 15.00, "active": true, "inventoryQuantity": 10, "options": []}]
}
```

### Create a product with four combinations

Send `variants` instead of top-level `sku` and `price`. `defaultVariantIndex` is a zero-based index into the request array and defaults to `0`.

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/products' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tee","description":"Cotton tee","currency":"USD","inventoryQuantity":20,"active":true,"categoryId":2,"defaultVariantIndex":0,"variants":[{"sku":"TEE-RED-S","price":20.00,"active":true,"inventoryQuantity":20,"options":[{"name":"Color","value":"Red"},{"name":"Size","value":"S"}]},{"sku":"TEE-RED-M","price":20.00,"active":true,"inventoryQuantity":10,"options":[{"name":"Color","value":"Red"},{"name":"Size","value":"M"}]},{"sku":"TEE-BLUE-S","price":22.00,"active":true,"inventoryQuantity":8,"options":[{"name":"Color","value":"Blue"},{"name":"Size","value":"S"}]},{"sku":"TEE-BLUE-M","price":22.00,"active":true,"inventoryQuantity":5,"options":[{"name":"Color","value":"Blue"},{"name":"Size","value":"M"}]}]}'
```

Status: `201 Created`. The response is the full product detail shape shown in **Get detail**, with generated IDs and normalized lowercase options.

Product create and update require `name` (1–200 trimmed characters), `description` (up to 2,000 characters), `currency` (three letters, stored uppercase), `active` (boolean), and `categoryId` (existing ID). `imageUrl` is optional and at most 1,000 characters. On create, supply either top-level `sku` and `price` or a nonempty `variants` array. Combining both forms returns `400`. Top-level `inventoryQuantity` is optional and sets the default variant stock; if the default variant also supplies `inventoryQuantity`, the values must match. Other variants specify their own stock. Any omitted quantity on a new variant starts at `0` for compatibility with earlier requests.

### Update product fields or the default SKU and price

`PUT` requires the product fields again. Omit `sku` and `price` to keep the default variant SKU and price unchanged. Supply **both** to update them. Top-level `inventoryQuantity`, when supplied, updates only the default variant stock; omit it to keep that stock unchanged. Send other variant changes to the variant endpoint; a `variants` or `defaultVariantIndex` field in `PUT` returns `400`.

```bash
curl --fail-with-body -X PUT 'http://localhost:8081/api/products/4' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Canvas Cap","description":"Cotton canvas cap","currency":"USD","inventoryQuantity":8,"active":true,"categoryId":2,"sku":"CAP-CANVAS-001","price":17.50}'
```

Status: `200 OK`. The response is full product detail; its top-level `sku`, `price`, and `inventoryQuantity` now read from the updated default variant:

```json
{"id": 4, "name": "Canvas Cap", "sku": "CAP-CANVAS-001", "price": 17.50, "inventoryQuantity": 8,
 "defaultVariantId": 4,
 "variants": [{"id": 4, "sku": "CAP-CANVAS-001", "price": 17.50, "active": true, "inventoryQuantity": 8, "options": []}]}
```

This is an excerpt; the response also includes all other product fields and `category`.

## Variant writes

Every variant request has:

| Field | Required | Rule |
| --- | --- | --- |
| `sku` | Yes | Nonblank, trimmed, at most 100 characters; unique across all products; case-sensitive |
| `price` | Yes | Nonnegative number with at most 2 decimal places and at most 12 digits of precision |
| `active` | Yes | Boolean |
| `inventoryQuantity` | No | Nonnegative integer; new variants default to `0`, updates keep existing stock when omitted |
| `options` | Yes | Array of `{name, value}` selections; `[]` for an optionless variant |

Each option name and value must be nonblank. Names are at most 100 characters; values at most 200. Both are trimmed and lowercased, and entries are sorted by name in responses. Repeating an option name after normalization returns `400`. Repeating an entire normalized combination within the same product returns `409`, even with a new SKU.

### Add a variant

```bash
curl --fail-with-body -X POST 'http://localhost:8081/api/products/5/variants' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"TEE-GREEN-L","price":23.00,"active":true,"inventoryQuantity":4,"options":[{"name":"Color","value":"Green"},{"name":"Size","value":"L"}]}'
```

Status: `201 Created`. The response is the **full updated product detail**, not a variant-only object. Its `variants` array now includes:

```json
{"id": 9, "sku": "TEE-GREEN-L", "price": 23.00, "active": true, "inventoryQuantity": 4,
 "options": [{"name":"color","value":"green"},{"name":"size","value":"l"}]}
```

The existing default variant does not change when another variant is added.

### Update a variant

The `variantId` must belong to the product ID in the path. Send a complete variant request:

```bash
curl --fail-with-body -X PUT 'http://localhost:8081/api/products/5/variants/9' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"TEE-GREEN-L","price":24.00,"active":false,"inventoryQuantity":2,"options":[{"name":"Color","value":"Green"},{"name":"Size","value":"L"}]}'
```

Status: `200 OK`. The response is full updated product detail, including:

```json
{"id": 9, "sku": "TEE-GREEN-L", "price": 24.00, "active": false, "inventoryQuantity": 2,
 "options": [{"name":"color","value":"green"},{"name":"size","value":"l"}]}
```

## Errors

All examples below use `application/problem+json`. The `instance` value is the request path. Constraint races may return a generic `409` detail instead of the more specific application validation detail.

| Request problem | Status | Typical `detail` |
| --- | --- | --- |
| `categoryId=abc`, invalid page/size/sort | `400` | Query-specific message; includes `parameter` and `rejectedValue` |
| Blank SKU, negative price or inventory, missing options, duplicate option key, malformed JSON | `400` | Catalog validation message |
| Missing product | `404` | `Product {id} was not found` |
| Missing category or variant | `404` | `Category {id} was not found` or `Variant {id} was not found` |
| Duplicate category slug | `409` | `Category slug already exists` |
| Duplicate SKU | `409` | `SKU already exists` |
| Duplicate option combination | `409` | `Option combination already exists for product` |

Example duplicate option combination:

```bash
curl --include -X POST 'http://localhost:8081/api/products/5/variants' \
  -H 'Content-Type: application/json' \
  -d '{"sku":"TEE-RED-S-OTHER","price":21.00,"active":true,"options":[{"name":" size ","value":"s"},{"name":"COLOR","value":"RED"}]}'
```

```json
{
  "detail": "Option combination already exists for product",
  "instance": "/api/products/5/variants",
  "status": 409,
  "title": "Catalog conflict",
  "type": "https://example.com/problems/catalog"
}
```

Example unknown category on product creation:

```json
{
  "detail": "Category 999 was not found",
  "instance": "/api/products",
  "status": 404,
  "title": "Resource not found",
  "type": "https://example.com/problems/catalog"
}
```

## Current limit

V5 moves the previous product quantity to its default variant and starts any other existing variants at `0`, because their historical distribution is unknown. New and updated variants have independent quantities. Stock reservation, cart, checkout, orders, and authentication remain outside this work.
