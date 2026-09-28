# Products API

This document describes every endpoint currently implemented by the ecommerce backend after Week 1 categories and variants.

## General information

Local base URL:

```text
http://localhost:8081
```

Successful responses use:

```http
Content-Type: application/json
```

Structured error responses use:

```http
Content-Type: application/problem+json
```

All monetary amounts are JSON numbers with two decimal places in the database. Timestamps are ISO 8601 strings with a UTC offset.

## Product object

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | integer | No | Generated product ID |
| `name` | string | No | Product display name |
| `sku` | string | No | SKU of the default variant |
| `description` | string | No | Product description |
| `price` | number | No | Price of the default variant; never negative |
| `currency` | string | No | Three-letter currency code, currently `USD` |
| `imageUrl` | string | Yes | Product image URL |
| `inventoryQuantity` | integer | No | Available inventory; never negative |
| `active` | boolean | No | Whether the product is active |
| `createdAt` | string | No | ISO 8601 creation timestamp |
| `updatedAt` | string | No | ISO 8601 last-update timestamp |

Example:

```json
{
  "id": 1,
  "name": "Mechanical Keyboard",
  "sku": "KEY-MECH-001",
  "description": "A compact hot-swappable mechanical keyboard.",
  "price": 129.00,
  "currency": "USD",
  "imageUrl": "https://images.example.com/mechanical-keyboard.jpg",
  "inventoryQuantity": 24,
  "active": true,
  "createdAt": "2026-09-25T17:34:06.081512Z",
  "updatedAt": "2026-09-25T17:34:06.081512Z"
}
```

---

## List, search, paginate, and sort products

```http
GET /api/products
```

### Request payload

This endpoint has no request body. Its input payload consists of optional query parameters.

| Parameter | Type | Required | Default | Validation and behavior |
| --- | --- | --- | --- | --- |
| `q` | string | No | No filter | Case-insensitive substring search across `name` and every variant SKU; surrounding whitespace is removed |
| `page` | integer | No | `0` | Zero-based; must be `0` or greater |
| `size` | integer | No | `20` | Must be between `1` and `100` inclusive |
| `categoryId` | integer | No | No filter | Positive category ID |
| `sort` | string | No | `name,asc` | Format: `field,direction`; fields: `name`, `price`; directions: `asc`, `desc` |

The sort direction may be omitted, in which case it defaults to ascending:

```text
sort=price
```

The backend adds `id,asc` as a stable internal tie-breaker when products have equal names or prices.

### Success response

Status:

```http
200 OK
```

Response payload:

| Field | Type | Description |
| --- | --- | --- |
| `items` | array of Product | Products in the requested page |
| `page` | integer | Returned zero-based page number |
| `size` | integer | Requested page size |
| `totalCount` | integer | Distinct products matching `q` and `categoryId`, before pagination |

### Example: default product page

Request:

```bash
curl --fail-with-body 'http://localhost:8081/api/products'
```

Response:

```json
{
  "items": [
    {
      "id": 1,
      "name": "Mechanical Keyboard",
      "sku": "KEY-MECH-001",
      "description": "A compact hot-swappable mechanical keyboard.",
      "price": 129.00,
      "currency": "USD",
      "imageUrl": "https://images.example.com/mechanical-keyboard.jpg",
      "inventoryQuantity": 24,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    },
    {
      "id": 3,
      "name": "USB-C Hub",
      "sku": "HUB-USBC-001",
      "description": "A seven-port USB-C hub with HDMI and Ethernet.",
      "price": 79.50,
      "currency": "USD",
      "imageUrl": "https://images.example.com/usb-c-hub.jpg",
      "inventoryQuantity": 16,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    },
    {
      "id": 2,
      "name": "Wireless Mouse",
      "sku": "MOU-WLS-001",
      "description": "An ergonomic wireless mouse with USB-C charging.",
      "price": 59.90,
      "currency": "USD",
      "imageUrl": "https://images.example.com/wireless-mouse.jpg",
      "inventoryQuantity": 48,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 3
}
```

### Example: search by product name

Request:

```bash
curl --fail-with-body \
  'http://localhost:8081/api/products?q=mouse'
```

Response:

```json
{
  "items": [
    {
      "id": 2,
      "name": "Wireless Mouse",
      "sku": "MOU-WLS-001",
      "description": "An ergonomic wireless mouse with USB-C charging.",
      "price": 59.90,
      "currency": "USD",
      "imageUrl": "https://images.example.com/wireless-mouse.jpg",
      "inventoryQuantity": 48,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1
}
```

Search is case-insensitive. For example, `q=MOUSE` returns the same result.

### Example: search by SKU

Request:

```bash
curl --fail-with-body \
  'http://localhost:8081/api/products?q=hub-usbc'
```

Response:

```json
{
  "items": [
    {
      "id": 3,
      "name": "USB-C Hub",
      "sku": "HUB-USBC-001",
      "description": "A seven-port USB-C hub with HDMI and Ethernet.",
      "price": 79.50,
      "currency": "USD",
      "imageUrl": "https://images.example.com/usb-c-hub.jpg",
      "inventoryQuantity": 16,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1
}
```

### Example: pagination and price sorting

Request:

```bash
curl --fail-with-body \
  'http://localhost:8081/api/products?page=0&size=2&sort=price,desc'
```

Response:

```json
{
  "items": [
    {
      "id": 1,
      "name": "Mechanical Keyboard",
      "sku": "KEY-MECH-001",
      "description": "A compact hot-swappable mechanical keyboard.",
      "price": 129.00,
      "currency": "USD",
      "imageUrl": "https://images.example.com/mechanical-keyboard.jpg",
      "inventoryQuantity": 24,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    },
    {
      "id": 3,
      "name": "USB-C Hub",
      "sku": "HUB-USBC-001",
      "description": "A seven-port USB-C hub with HDMI and Ethernet.",
      "price": 79.50,
      "currency": "USD",
      "imageUrl": "https://images.example.com/usb-c-hub.jpg",
      "inventoryQuantity": 16,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    }
  ],
  "page": 0,
  "size": 2,
  "totalCount": 3
}
```

Second-page request:

```bash
curl --fail-with-body \
  'http://localhost:8081/api/products?page=1&size=2&sort=price,desc'
```

Second-page response:

```json
{
  "items": [
    {
      "id": 2,
      "name": "Wireless Mouse",
      "sku": "MOU-WLS-001",
      "description": "An ergonomic wireless mouse with USB-C charging.",
      "price": 59.90,
      "currency": "USD",
      "imageUrl": "https://images.example.com/wireless-mouse.jpg",
      "inventoryQuantity": 48,
      "active": true,
      "createdAt": "2026-09-25T17:34:06.081512Z",
      "updatedAt": "2026-09-25T17:34:06.081512Z"
    }
  ],
  "page": 1,
  "size": 2,
  "totalCount": 3
}
```

### Empty page or no search matches

An empty result is successful and retains the page metadata.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalCount": 0
}
```

### Invalid query response

Status:

```http
400 Bad Request
```

Request:

```bash
curl --include \
  'http://localhost:8081/api/products?sort=createdAt,desc'
```

Response:

```json
{
  "detail": "sort field must be name or price",
  "instance": "/api/products",
  "status": 400,
  "title": "Invalid product query",
  "type": "https://example.com/problems/invalid-product-query",
  "parameter": "sort",
  "rejectedValue": "createdAt,desc"
}
```

Possible validation details include:

| Invalid input | `detail` |
| --- | --- |
| `page=-1` | `page must be zero or greater` |
| `page=abc` | `page must be an integer` |
| `size=0` or `size=101` | `size must be between 1 and 100` |
| `sort=createdAt,desc` | `sort field must be name or price` |
| `sort=price,sideways` | `sort direction must be asc or desc` |
| malformed `sort` | `sort must use the format field,direction` |

---

## Get a product by ID

```http
GET /api/products/{id}
```

### Request payload

This endpoint has no request body.

Path parameters:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer | Yes | Product ID |

### Success response

Status:

```http
200 OK
```

Request:

```bash
curl --fail-with-body 'http://localhost:8081/api/products/1'
```

Response:

```json
{
  "id": 1,
  "name": "Mechanical Keyboard",
  "sku": "KEY-MECH-001",
  "description": "A compact hot-swappable mechanical keyboard.",
  "price": 129.00,
  "currency": "USD",
  "imageUrl": "https://images.example.com/mechanical-keyboard.jpg",
  "inventoryQuantity": 24,
  "active": true,
  "createdAt": "2026-09-25T17:34:06.081512Z",
  "updatedAt": "2026-09-25T17:34:06.081512Z"
}
```

### Product-not-found response

Status:

```http
404 Not Found
```

Request:

```bash
curl --include 'http://localhost:8081/api/products/999'
```

Response:

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

## Endpoint summary

| Method | Path | Request body | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET` | `/api/products` | None | `200` product page | `400` invalid query parameter |
| `GET` | `/api/products/{id}` | None | `200` product | `404` product not found |



## Week 1 categories and variants

The parent `products` row owns catalog metadata, currency, and the existing product-level inventory count. `categories` has a stable, immutable slug. `product_variants` owns SKU, price, active status, and options. V4 copies every preexisting product SKU and price into one default variant, and assigns those products to the `uncategorized` category. Existing product IDs, SKUs, prices, and legacy list fields remain intact. The product-level `sku` and `price` response fields always come from `defaultVariantId`; they are never stored twice. Price sorting also uses the default variant price, including for products with several variants. List responses keep their original shape. Detail responses add `category`, `defaultVariantId`, and `variants`.

Option selections are arrays of `{ "name": "Color", "value": "Red" }`. Names and values are trimmed and lowercased, then sorted by name before storage and combination comparison. For example, `Color=Red, Size=M` equals `size=m, color=red`. Use `[]` only for a variant without options. A product cannot have two variants with the same normalized combination. SKU uniqueness is global and case-sensitive; SKU values are trimmed when written.

### Endpoints

| Method | Path | Use |
| --- | --- | --- |
| GET | `/api/categories` | List categories, ordered by ID |
| GET | `/api/categories/{id}` | Get one category |
| POST | `/api/categories` | Create with `slug` and `name` |
| PUT | `/api/categories/{id}` | Update display `name`; slug is immutable |
| GET | `/api/products?categoryId=2&q=tee&page=0&size=20&sort=price,asc` | Filter and search distinct products |
| POST | `/api/products` | Create metadata and one legacy `sku`/`price` pair, or a `variants` array |
| PUT | `/api/products/{id}` | Update metadata and optionally the default variant's `sku` and `price` |
| POST | `/api/products/{id}/variants` | Add a variant |
| PUT | `/api/products/{id}/variants/{variantId}` | Update a variant |

Product create and update require `name`, `description`, `currency`, `inventoryQuantity`, `active`, and `categoryId`; `imageUrl` is optional. Create accepts either `sku` plus `price` for one optionless default variant, or `variants` and an optional zero-based `defaultVariantIndex` (defaults to 0). A variant requires `sku`, `price`, `active`, and `options`. Product update changes metadata and may update the existing default variant via `sku` plus `price`; update variants via their own endpoint. Slugs are lowercase letters, numbers, and hyphens. Unknown product, category, or variant IDs return 404. Invalid bodies return 400; existing SKU, slug, or option combination returns 409. Errors use `application/problem+json`.

### Simple product

```bash
curl -X POST http://localhost:8081/api/categories -H 'Content-Type: application/json' \
  -d '{"slug":"apparel","name":"Apparel"}'
curl -X POST http://localhost:8081/api/products -H 'Content-Type: application/json' \
  -d '{"name":"Cap","description":"Cotton cap","currency":"USD","inventoryQuantity":10,"active":true,"categoryId":2,"sku":"CAP-001","price":15.00}'
```

```json
{"id":4,"name":"Cap","sku":"CAP-001","description":"Cotton cap","price":15.00,"currency":"USD","imageUrl":null,"inventoryQuantity":10,"active":true,"createdAt":"2026-09-29T00:00:00Z","updatedAt":"2026-09-29T00:00:00Z","category":{"id":2,"slug":"apparel","name":"Apparel"},"defaultVariantId":4,"variants":[{"id":4,"sku":"CAP-001","price":15.00,"active":true,"options":[]}]}
```

### Red/Blue and S/M variants

```bash
curl -X POST http://localhost:8081/api/products -H 'Content-Type: application/json' \
  -d '{"name":"Tee","description":"Cotton tee","currency":"USD","inventoryQuantity":20,"active":true,"categoryId":2,"defaultVariantIndex":0,"variants":[{"sku":"TEE-RED-S","price":20.00,"active":true,"options":[{"name":"Color","value":"Red"},{"name":"Size","value":"S"}]},{"sku":"TEE-RED-M","price":20.00,"active":true,"options":[{"name":"Color","value":"Red"},{"name":"Size","value":"M"}]},{"sku":"TEE-BLUE-S","price":22.00,"active":true,"options":[{"name":"Color","value":"Blue"},{"name":"Size","value":"S"}]},{"sku":"TEE-BLUE-M","price":22.00,"active":true,"options":[{"name":"Color","value":"Blue"},{"name":"Size","value":"M"}]}]}'
```

```json
{"id":5,"name":"Tee","sku":"TEE-RED-S","description":"Cotton tee","price":20.00,"currency":"USD","imageUrl":null,"inventoryQuantity":20,"active":true,"createdAt":"2026-09-29T00:00:00Z","updatedAt":"2026-09-29T00:00:00Z","category":{"id":2,"slug":"apparel","name":"Apparel"},"defaultVariantId":5,"variants":[{"id":5,"sku":"TEE-RED-S","price":20.00,"active":true,"options":[{"name":"color","value":"red"},{"name":"size","value":"s"}]},{"id":6,"sku":"TEE-RED-M","price":20.00,"active":true,"options":[{"name":"color","value":"red"},{"name":"size","value":"m"}]},{"id":7,"sku":"TEE-BLUE-S","price":22.00,"active":true,"options":[{"name":"color","value":"blue"},{"name":"size","value":"s"}]},{"id":8,"sku":"TEE-BLUE-M","price":22.00,"active":true,"options":[{"name":"color","value":"blue"},{"name":"size","value":"m"}]}]}
```

### Category-filtered search

```bash
curl 'http://localhost:8081/api/products?categoryId=2&q=blue&page=0&size=10&sort=price,asc'
```

```json
{"items":[{"id":5,"name":"Tee","sku":"TEE-RED-S","description":"Cotton tee","price":20.00,"currency":"USD","imageUrl":null,"inventoryQuantity":20,"active":true,"createdAt":"2026-09-29T00:00:00Z","updatedAt":"2026-09-29T00:00:00Z"}],"page":0,"size":10,"totalCount":1}
```

The example IDs and timestamps above assume a fresh database and are illustrative. The legacy product-level `inventoryQuantity` is still shared by all variants; per-variant inventory is outside Week 1.
