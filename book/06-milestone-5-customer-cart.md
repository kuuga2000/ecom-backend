# Milestone 5: customer carts

This milestone introduces `/api/cart`, a persistent cart owned by the authenticated customer. The server uses the customer ID in the validated JWT. A customer can read only that cart and mutate only its own item IDs; another customer's item ID receives `404`.

A cart item stores one exact product variant ID and a quantity. The selected variant includes the complete option combination, so a product with color, size, diameter, or any other options uses the same request shape. V8 adds the customer foreign key and unique customer constraint to `carts`, and gives `cart_items` stable IDs while retaining unique cart/variant pairs. Existing UUID-based anonymous carts remain independent for compatibility.

The catalog is authoritative for display fields. Each read joins the cart lines with products and variants to return current name, SKU, options, price, active state, and stock. No price or display snapshot is stored. The estimated subtotal uses only purchasable lines, while total quantity counts all lines. An inactive or understocked line stays visible with a reason and can be removed. Cart writes check stock but never reserve it. Checkout will check price and availability again in a later milestone.

A first add creates the cart through `INSERT ... ON CONFLICT`; the service locks the customer cart row before updating lines. PostgreSQL uniqueness constraints and row locking handle simultaneous first adds and increments across backend instances. The cart uses one three-letter currency; a changed catalog currency marks an existing line unavailable.

For request and response examples, errors, and the full `curl` flow, see [the customer cart API](../api-docs/06-customer-cart-api.md). Run the PostgreSQL-backed suite with the command in [the Docker guide](../docker-docs/README.md); it exercises ownership, JWT validation, cart mutations, catalog changes, and concurrent adds.

## Verification command

The complete suite was run against an isolated copy of the existing PostgreSQL database on Docker network `graphql-slardar_default`. After creating `ecom_m5_test` from `ecommerce`, the exact Maven command was:

```bash
JWT_SECRET=$(openssl rand -base64 32)
export JWT_SECRET
docker run --rm --network graphql-slardar_default -v "$PWD":/workspace -w /workspace \
  -e DB_URL=jdbc:postgresql://postgres:5432/ecom_m5_test \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  -e JWT_SECRET -e JWT_ISSUER=ecom-backend-test \
  maven:3.9.11-eclipse-temurin-25 mvn --batch-mode test
```

The database-backed tests require `DB_URL`; without it, they are skipped by design. Use a disposable database because integration tests create and update rows.

Final result on the isolated PostgreSQL copy: **20 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`).
