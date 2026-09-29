# Products API

This document covers the product read API and its Milestone 2 response examples. For the current category, variant, and product write contract, see [Categories and product variants API](03-categories-and-product-variants-api.md).

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

## Product list item

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `id` | integer | No | Generated product ID |
| `name` | string | No | Product display name |
| `sku` | string | No | SKU of the default variant |
| `description` | string | No | Product description |
| `price` | number | No | Price of the default variant; never negative |
| `currency` | string | No | Three-letter currency code, currently `USD` |
| `imageUrl` | string | Yes | Product image URL |
| `inventoryQuantity` | integer | No | Stock of the default variant; never negative |
| `active` | boolean | No | Whether the product is active |
| `createdAt` | string | No | ISO 8601 creation timestamp |
| `updatedAt` | string | No | ISO 8601 last-update timestamp |

Example list item (product detail also includes `category`, `defaultVariantId`, and `variants`):

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
GET /api/v1/products
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

Search counts distinct products even if multiple variant SKUs match. Price sorting uses the default variant price. An unknown positive `categoryId` returns an empty page.

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
curl --fail-with-body 'http://localhost:8081/api/v1/products'
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
  'http://localhost:8081/api/v1/products?q=mouse'
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
  'http://localhost:8081/api/v1/products?q=hub-usbc'
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
  'http://localhost:8081/api/v1/products?page=0&size=2&sort=price,desc'
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
  'http://localhost:8081/api/v1/products?page=1&size=2&sort=price,desc'
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
  'http://localhost:8081/api/v1/products?sort=createdAt,desc'
```

Response:

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
GET /api/v1/products/{id}
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
curl --fail-with-body 'http://localhost:8081/api/v1/products/1'
```

Response excerpt (the actual detail response also includes `category`, `defaultVariantId`, and `variants`):

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
curl --include 'http://localhost:8081/api/v1/products/999'
```

Response:

```json
{
  "detail": "Product 999 was not found",
  "instance": "/api/v1/products/999",
  "status": 404,
  "title": "Product not found",
  "type": "https://example.com/problems/product-not-found",
  "productId": 999
}
```

## Product read endpoint summary

| Method | Path | Request body | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/products` | None | `200` product page | `400` invalid query parameter |
| `GET` | `/api/v1/products/{id}` | None | `200` product detail with category and variants | `404` product not found |
