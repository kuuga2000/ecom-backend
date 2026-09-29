# Carts and variant selection

This documents the earlier anonymous `/api/carts/{uuid}` API. For a persistent cart owned by the signed-in customer, use the [Milestone 5 customer cart API](06-customer-cart-api.md).

A **variant is one purchasable combination** of option values. A product can have one option (for example, color), two (color and size), three (diameter, size, and color), four, or more. The `options` array has no fixed number of entries. Every variant has its own ID, SKU, price, active flag, and stock count.

For example, these are two distinct variants, possibly on different products:

```json
{"id": 21, "sku": "SHIRT-RED-M", "inventoryQuantity": 8,
 "options": [{"name":"color","value":"red"},{"name":"size","value":"m"}]}
{"id": 32, "sku": "FITTING-BLACK-6", "inventoryQuantity": 3,
 "options": [{"name":"color","value":"black"},{"name":"diameter","value":"mm"},{"name":"size","value":"6mm"}]}
```

A fourth option, such as `{"name":"finish","value":"matte"}`, can be included in the same variant. Each option name can appear only once in a variant. The same normalized combination cannot appear twice within one product. Create combinations through `POST /api/products` or `POST /api/products/{id}/variants`; see [the variant API](03-categories-and-product-variants-api.md).

## Create and read a cart

```bash
curl --fail-with-body -X POST http://localhost:8081/api/carts
```

Status: `201 Created`. The response is `{"id":"<uuid>","items":[]}`. Save the returned UUID and use it in subsequent requests. Read the cart with:

```bash
curl --fail-with-body http://localhost:8081/api/carts/<uuid>
```

A cart UUID acts as the access token for this legacy anonymous API; keep it private. Authenticated customer carts use bearer tokens and `/api/cart` instead.

## Add a selected variant

Get the product detail first and choose an ID from its `variants` array. **`variantId` is mandatory**, including when a product has only one optionless default variant. Product ID, SKU, or option labels alone cannot be used to add an item.

```bash
curl --fail-with-body -X POST http://localhost:8081/api/carts/<uuid>/items \
  -H 'Content-Type: application/json' \
  -d '{"variantId":32,"quantity":2}'
```

Status: `200 OK`. A representative response:

```json
{
  "id": "<uuid>",
  "items": [{
    "variantId": 32,
    "productId": 6,
    "productName": "Fitting",
    "sku": "FITTING-BLACK-6",
    "options": [
      {"name":"color","value":"black"},
      {"name":"diameter","value":"mm"},
      {"name":"size","value":"6mm"}
    ],
    "price": 12.00,
    "currency": "USD",
    "quantity": 2,
    "lineTotal": 24.00
  }]
}
```

Adding the same variant again increases its quantity. Different variants are separate cart lines. The product and variant must both be active, and the requested total quantity cannot exceed that variant's current stock.

## Change quantity and remove

```bash
curl --fail-with-body -X PUT http://localhost:8081/api/carts/<uuid>/items/32 \
  -H 'Content-Type: application/json' \
  -d '{"quantity":1}'

curl --fail-with-body -X DELETE http://localhost:8081/api/carts/<uuid>/items/32
```

Both return the full updated cart. `PUT` replaces the quantity and requires an existing cart line; `DELETE` removes it. A missing cart or cart line returns `404`.

## Validation and stock behavior

| Problem | Status |
| --- | --- |
| Missing, zero, or negative `variantId`; missing or nonpositive `quantity` | `400` |
| Unknown variant or cart | `404` |
| Inactive product or variant, or quantity greater than variant stock | `409` |

Cart additions **check** current stock but do not reserve or decrement it. Cart prices and options are read from the current variant when the cart is fetched; they may change after addition. Checkout and order stock deduction are not implemented yet.
