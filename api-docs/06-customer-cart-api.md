# Milestone 5: authenticated customer cart

`/api/cart` is the persistent cart for the customer identified by a validated Milestone 4 bearer token. Register and log in as described in [authentication](05-authentication-api.md), then set `TOKEN` to the returned `accessToken`. Every endpoint below requires `Authorization: Bearer $TOKEN`. The server takes the owner ID from the token, never from the request body.

The older `/api/carts/{uuid}` anonymous API remains available for compatibility with earlier milestones. It is separate from the customer cart and its UUID does not transfer items into `/api/cart`.

## Full flow

Choose a complete variant ID from `GET /api/products/{id}`. Each ID identifies one exact option combination, such as `Red / M`; product IDs, SKUs, and partial options are not accepted in cart writes.

```bash
# Before the first add, this read does not create a database row.
curl --fail-with-body http://localhost:8081/api/cart \
  -H "Authorization: Bearer $TOKEN"
```

Status `200`; example response:

```json
{"id":null,"currency":null,"items":[],"totalQuantity":0,"estimatedSubtotal":0.00}
```

```bash
curl --fail-with-body -X POST http://localhost:8081/api/cart/items \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"variantId":21,"quantity":2}'
```

Status `201`; example response (IDs and catalog data will differ):

```json
{
  "id":"9dbbd9e3-a893-4712-89c2-e1373cb3587c",
  "currency":"USD",
  "items":[{
    "id":101,
    "variantId":21,
    "sku":"SHIRT-RED-M",
    "productName":"Shirt",
    "options":[{"name":"color","value":"red"},{"name":"size","value":"m"}],
    "quantity":2,
    "unitPrice":10.00,
    "currency":"USD",
    "lineSubtotal":20.00,
    "purchasable":true,
    "unavailableReason":null
  }],
  "totalQuantity":2,
  "estimatedSubtotal":20.00
}
```

Adding a second variant creates a second line. Repeating `POST` with variant `21` **adds** the supplied quantity to its existing line; this POST is intentionally not idempotent. The new total must pass the line limit and stock check.

```bash
# Use the cart item ID (101), not the variant ID, in the path.
curl --fail-with-body -X PATCH http://localhost:8081/api/cart/items/101 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"quantity":3}'

curl --fail-with-body http://localhost:8081/api/cart \
  -H "Authorization: Bearer $TOKEN"

curl -i -X DELETE http://localhost:8081/api/cart/items/101 \
  -H "Authorization: Bearer $TOKEN"
```

`PATCH` sets the exact quantity and returns `200` with the full updated cart. A successful `DELETE` returns `204 No Content` and an empty body. Removing the last line clears the cart currency but keeps the customer's cart row for later use.

## Validation and calculated fields

- `variantId` must be a positive integer for an existing active variant of an active product. Request bodies accept only the documented fields. Quantity must be an integer from `1` to `99` per line. Decimal quantities and client-supplied prices or descriptions are rejected with `400`.
- An unknown variant or a missing cart item returns `404`. Items owned by another customer also return `404`. Inactive products or variants, inadequate stock, mixed cart currency, and an add that would exceed 99 units return `409`.
- Every item retains its exact `variantId` and quantity. Price, SKU, product name, selected options, active flags, and stock are read from the current catalog when the cart is rendered. No display or price snapshots are stored in `cart_items`.
- `lineSubtotal` is current `unitPrice × quantity`, including on an unavailable line for display. `estimatedSubtotal` sums only lines with `purchasable: true`. `totalQuantity` includes all lines, including unavailable ones. This keeps an inactive or understocked item visible with `unavailableReason` so the customer can remove it. The cart never reserves or decrements stock.
- Catalog currency is a three-letter uppercase code. The first line establishes the cart currency; a line with another currency cannot be added. If a product's currency later changes, its existing line stays visible but is marked unavailable until the currency mismatch is resolved or the line is removed. Money uses decimal values and `BigDecimal`, never floating point.
- Catalog price and availability can change after any cart read. Checkout must validate both again before creating an order.

A missing, invalid, or expired bearer token returns `401`. Error bodies follow the project's `application/problem+json` convention.

## Storage and concurrency

Flyway V8 adds `carts.customer_id` with a foreign key to `customers` and a unique constraint, so a customer has at most one cart. Existing anonymous cart rows have a null `customer_id` and remain readable. `cart_items` gets a stable numeric `id` for item endpoints, retains its cart and variant foreign keys, and has a unique `(cart_id, variant_id)` constraint plus a cart lookup index. The variant foreign key prevents a catalog delete from leaving an unreadable cart line.

The first add uses PostgreSQL `INSERT ... ON CONFLICT` to handle simultaneous cart creation. Each mutation locks the customer's cart row with `FOR UPDATE`; this serializes additions and quantity changes across app instances. The unique constraints prevent duplicate carts and duplicate variant lines. Cart reads fetch lines and their catalog values in one join query, with no per-item product query.
