# Milestone 4: customer authentication

Customers are stored in `customers` with a stable numeric ID, name, normalized email, BCrypt password hash, active flag, role (`CUSTOMER` or `ADMIN`), and creation/update timestamps. V7 creates the table without changing existing catalog or cart rows. PostgreSQL enforces normalized email and a unique `lower(email)` index.

Registration and login are public. Catalog GET requests stay public. Product and category POST/PUT/PATCH/DELETE requests require `ADMIN`. `GET /api/customers/me` requires a bearer token. Existing anonymous cart endpoints remain available and are not associated with customer accounts. Milestone 5 adds the separate [authenticated customer cart API](06-customer-cart-api.md).

## Configuration

Set these environment variables before starting the backend:

| Variable | Requirement |
| --- | --- |
| `JWT_SECRET` | Base64-encoded random key that decodes to at least 32 bytes; required |
| `JWT_ISSUER` | Nonblank issuer name or URI; required and checked on every token |
| `JWT_EXPIRATION_SECONDS` | Optional, default `900`; allowed range 60–3600 seconds |

For local development, generate a fresh key with `openssl rand -base64 32` and put it in an untracked `.env` file. Do not commit the key. The app fails startup with a clear message if the key, issuer, or expiration setting is invalid. All running backend instances must use the same issuer and key to accept one another's tokens. Change the key to invalidate previously issued tokens.

## Register

```bash
curl --fail-with-body -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice Example","email":" Alice@Example.com ","password":"replace-with-a-long-private-password"}'
```

Status: `201 Created`. Example response:

```json
{
  "id": 4,
  "name": "Alice Example",
  "email": "alice@example.com",
  "active": true,
  "role": "CUSTOMER",
  "createdAt": "2026-09-29T00:00:00Z",
  "updatedAt": "2026-09-29T00:00:00Z"
}
```

Only `name`, `email`, and `password` are accepted. Clients cannot choose ID, role, active state, or password hash. Name must be nonblank and at most 200 characters. Email is trimmed, lowercased, validated, and at most 320 characters. Password must be 12–72 UTF-8 bytes. A duplicate normalized email returns `409`. The response never includes a password hash.

## Login

```bash
curl --fail-with-body -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ALICE@example.com","password":"replace-with-a-long-private-password"}'
```

Status: `200 OK`. Example response (token shortened for display):

```json
{
  "accessToken": "<signed-jwt>",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-29T00:15:00Z",
  "expiresIn": 900
}
```

Email lookup is case insensitive after normalization. Unknown email, wrong password, and inactive account all return the same `401` detail: `Invalid email or password`. The access token is signed with HS256 and contains issuer, subject/customer ID, `customer_id`, current role, access-token type, issue time, and expiration. It contains no password or email.

## Current customer

```bash
curl --fail-with-body http://localhost:8081/api/customers/me \
  -H 'Authorization: Bearer <signed-jwt>'
```

Status: `200 OK`, with the same safe profile shape as registration. The server takes the customer ID from the validated token, then loads the account; the client does not supply an ID. Missing, expired, incorrectly signed, wrong-issuer, or incomplete tokens return `401`. A customer token on a product/category write returns `403`. Deactivating an account or changing its role invalidates its existing tokens.

Access tokens expire after a short period. There are no refresh tokens: log in again after expiration.

## Development admin provisioning

On a development database only, register an ordinary customer through the API, then promote that specific account using a privileged PostgreSQL session:

```bash
docker compose exec postgres psql -U postgres -d ecommerce \
  -c "UPDATE customers SET role = 'ADMIN', updated_at = now() WHERE email = lower('admin@example.test') RETURNING id, email, role"
```

For the supplied external database, replace `docker compose exec postgres` with `docker exec slardar_postgres`, or use your own privileged `psql` connection. Replace the sample email with the account you registered. Verify exactly one row was updated, then log in again to get a token with the new role. The old token is invalid after the role change. No default admin account or password is created.

## Security notes

The API uses bearer tokens in the `Authorization` header and keeps no authentication session or cookie. CSRF protection is disabled for this transport; browsers do not automatically attach this header to cross-site requests. Keep tokens out of URLs and logs, and use HTTPS beyond local development. A legacy cart UUID remains an independent anonymous access token; `/api/cart` is owned through the validated customer token.
