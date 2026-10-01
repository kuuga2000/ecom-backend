# Milestone 6: addresses and checkout

All routes use `/api/v1` and require a customer JWT. IDs come from the JWT subject;
requests cannot choose a customer or cart. Resources belonging to another customer
return `404`. Errors use the existing RFC 9457 ProblemDetail response format.

## Endpoints

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/v1/customers/me/addresses` | Array of the customer's saved addresses |
| POST | `/api/v1/customers/me/addresses` | Create an address, `201` |
| GET | `/api/v1/customers/me/addresses/{id}` | One owned address |
| PUT | `/api/v1/customers/me/addresses/{id}` | Replace an owned address |
| DELETE | `/api/v1/customers/me/addresses/{id}` | Delete an owned address, `204` |
| GET | `/api/v1/checkout` | Current checkout and computed totals |
| PUT | `/api/v1/checkout/shipping-address` | Copy a saved address; return checkout |
| GET | `/api/v1/checkout/shipping-methods` | Available methods in an `items` envelope |
| PUT | `/api/v1/checkout/shipping-method` | Select a method by code; return checkout |

`GET /checkout` is also the checkout summary. There is no duplicate summary route.

## Architecture and lifecycle

```text
Login -> Active cart -> Select saved address -> Get shipping methods
      -> Select shipping method -> Checkout summary -> Ready for Milestone 7

CustomerAddress -> copy ShippingAddress values onto cart
                -> ShippingProvider -> ShippingOption -> CheckoutResponse
```

The existing customer cart is the active cart: there is one cart per customer and
no order/status lifecycle yet. Address and checkout repositories use JDBC, matching
the existing cart implementation. API and domain values use immutable records.

The cart stores eight shipping address fields and the selected method code. It has
no foreign key to the saved address: editing or deleting an address does not change
the snapshot. Selecting that address again refreshes the snapshot. A default saved
address is a convenience flag; it is not automatically selected during checkout.

Address mutations lock the customer row in a transaction. A partial unique index
also guarantees at most one default address per customer. Deleting or unmarking the
default leaves zero defaults; another address is not automatically promoted.

`ShippingProvider` separates shipping rules from checkout orchestration. The local
`FixedRateShippingProvider` supports **IDR carts and Indonesian (`ID`) destinations**:

| Code | Name | Amount (IDR) | Business days |
| --- | --- | --- | --- |
| STANDARD | Standard Shipping | 20000.00 | 3–5 |
| EXPRESS | Express Shipping | 40000.00 | 1–2 |

Other two-letter country codes are accepted in the address book. Unsupported
countries or cart currencies return an empty method list; no currency conversion
is implied. Replace the provider to expand coverage. Rates live in one provider
constant and all money calculations use `BigDecimal`.

Every checkout read/selection reuses the cart service's current variant prices and
availability checks (active product/variant, stock, currency, quantities 1–99).
Missing carts return `404`, empty carts return `400`, and stale/unpurchasable lines
return the existing `409` cart error. Foreign keys prevent dangling variant references.

Address/method changes lock the same cart row used by cart mutations. Checkout
reads use a read-only repeatable-read transaction for a consistent view of cart
and snapshot. Every read fetches current provider quotes: unavailable selections
are represented as null, with readiness false. Address changes clear an incompatible
stored method code. A provider becoming available again can make a previously
selected code usable again; no shipping price is promised until order placement.

Only valid, non-empty carts reach the summary calculation. A missing address or
method yields `readyForOrder: false`, `shippingAmount: null`, and `grandTotal: null`.
A valid selection yields `grandTotal = subtotal + shippingAmount`. Subtotal comes
from current variant prices times quantities. No totals or prices are accepted as
inputs; extra price fields have no effect. Readiness is derived, never persisted.
Milestone 7 must repeat validation during order placement; this does not reserve stock.

## Address validation

Required: recipientName (200 characters), phone (40), addressLine1 (255), city (100),
province (100), postalCode (20), countryCode (two letters). Label (100) and
addressLine2 (255) are optional. Strings are trimmed and country codes uppercased.
Phone numbers permit international formatting. Province is required by this
milestone's address contract. `defaultShipping` defaults to false; PUT replaces all
fields. Timestamps and IDs are assigned by the server.

## Complete curl workflow

The Compose API defaults to port **8081**. Requires `curl` and `jq`. Use an existing
account and an active IDR variant with enough stock from the catalog.

```bash
BASE=http://localhost:8081/api/v1
EMAIL=customer@example.com
PASSWORD='your-password'
TOKEN=$(curl --fail-with-body -s "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg email "$EMAIL" --arg password "$PASSWORD" '{email:$email,password:$password}')" \
  | jq -r '.accessToken')

# Inspect the catalog and set an available IDR variant ID.
curl --fail-with-body "$BASE/products"
VARIANT_ID=101 # Replace with an existing IDR variant ID.
curl --fail-with-body -X POST "$BASE/cart/items" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"variantId\":$VARIANT_ID,\"quantity\":2}"

ADDRESS_ID=$(curl --fail-with-body -s -X POST "$BASE/customers/me/addresses" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"label":"Home","recipientName":"John Doe","phone":"08123456789",
       "addressLine1":"Jl. Example No. 10","addressLine2":null,"city":"Jakarta",
       "province":"DKI Jakarta","postalCode":"12345","countryCode":"ID",
       "defaultShipping":true}' | jq -r '.id')

curl --fail-with-body "$BASE/customers/me/addresses" -H "Authorization: Bearer $TOKEN"
curl --fail-with-body "$BASE/customers/me/addresses/$ADDRESS_ID" -H "Authorization: Bearer $TOKEN"

curl --fail-with-body -X PUT "$BASE/checkout/shipping-address" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"addressId\":$ADDRESS_ID}"
curl --fail-with-body "$BASE/checkout/shipping-methods" -H "Authorization: Bearer $TOKEN"
curl --fail-with-body -X PUT "$BASE/checkout/shipping-method" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"STANDARD"}'
curl --fail-with-body "$BASE/checkout" -H "Authorization: Bearer $TOKEN"

# Address book edits/deletes leave the already-selected checkout snapshot intact.
curl --fail-with-body -X PUT "$BASE/customers/me/addresses/$ADDRESS_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"label":"Office","recipientName":"John Doe","phone":"08123456789",
       "addressLine1":"Jl. Office No. 20","city":"Jakarta","province":"DKI Jakarta",
       "postalCode":"12345","countryCode":"ID","defaultShipping":false}'
curl --fail-with-body -X DELETE "$BASE/customers/me/addresses/$ADDRESS_ID" \
  -H "Authorization: Bearer $TOKEN"
```

Example complete checkout with one IDR 150000 variant and quantity 2 (IDs/options
are illustrative; items retain the existing customer-cart DTO contract):

```json
{
  "cartId": "ce7e215c-d6d4-48d3-b7df-84ea046114d8",
  "items": [{
    "id": 1, "variantId": 101, "sku": "TSHIRT-BLK-L", "productName": "T-Shirt",
    "options": [{"name":"size","value":"l"}], "quantity": 2,
    "unitPrice": 150000.00, "currency": "IDR", "lineSubtotal": 300000.00,
    "purchasable": true, "unavailableReason": null
  }],
  "shippingAddress": {
    "recipientName": "John Doe", "phone": "08123456789",
    "addressLine1": "Jl. Example No. 10", "addressLine2": null,
    "city": "Jakarta", "province": "DKI Jakarta", "postalCode": "12345", "countryCode": "ID"
  },
  "shippingMethod": {
    "code": "STANDARD", "name": "Standard Shipping", "amount": 20000.00, "currency": "IDR",
    "estimatedDeliveryMinDays": 3, "estimatedDeliveryMaxDays": 5
  },
  "subtotal": 300000.00, "shippingAmount": 20000.00, "grandTotal": 320000.00,
  "currency": "IDR", "readyForOrder": true
}
```

## Database and verification

`V9__addresses_and_checkout.sql` creates `customer_addresses` with a customer foreign
key, ownership index, country-code check, timestamps, and a partial unique default
index. It adds shipping snapshot columns and a method code to `carts`, with an
all-or-none snapshot constraint. Totals and readiness are not persisted.

Run `mvn --batch-mode verify` with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET` and `JWT_ISSUER` configured for a disposable PostgreSQL test database.
Database tests skip if `DB_URL` is absent. Tests cover CRUD, ownership, concurrent
defaults, HTTP validation/authentication, snapshots, provider changes, current
prices, totals, stale cart rejection and readiness, alongside all previous tests.
Use the host Docker daemon and the project's Compose PostgreSQL service; do not
start another daemon inside the editor container.

## Deferred

- Milestone 7: order creation, place order, and order history.
- Milestone 8: payment and inventory/stock workflow.
- Real courier APIs, payment gateways, and inventory reservation are not implemented.

Verified on 2026-10-01 with Java 25 and PostgreSQL 18 using `mvn --batch-mode verify`:
**30 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. This includes nine new
checkout/address tests and all 21 existing tests. The exact route inventory was
extended to include the nine new routes. Verification used a temporary Maven
container on the host Compose network and a disposable database, removed afterward.
