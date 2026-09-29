# API v1 route migration

`v1` is the frontend API contract version. It does not change with project milestones. All frontend-facing controller routes now begin with `/api/v1`; framework routes such as `/error` are outside this inventory. HTTP methods, bodies, and responses are unchanged.

The repository contains no frontend application, generated client, or other in-repo consumer of the old URLs. The old paths have no aliases. Any separately deployed client must switch to `/api/v1` when this backend is updated.

This before/after table was generated from the `@RequestMapping` and HTTP method annotations in the six application controllers. `ApiRouteDatabaseTest` independently checks the live Spring MVC route registry for exactly these 22 mappings and rejects any unversioned application route.

| Method | Before | After | Access |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | `/api/v1/auth/register` | Public |
| `POST` | `/api/auth/login` | `/api/v1/auth/login` | Public |
| `GET` | `/api/customers/me` | `/api/v1/customers/me` | Bearer token |
| `POST` | `/api/carts` | `/api/v1/carts` | Public (UUID cart) |
| `GET` | `/api/carts/{cartId}` | `/api/v1/carts/{cartId}` | Public (UUID cart) |
| `POST` | `/api/carts/{cartId}/items` | `/api/v1/carts/{cartId}/items` | Public (UUID cart) |
| `PUT` | `/api/carts/{cartId}/items/{variantId}` | `/api/v1/carts/{cartId}/items/{variantId}` | Public (UUID cart) |
| `DELETE` | `/api/carts/{cartId}/items/{variantId}` | `/api/v1/carts/{cartId}/items/{variantId}` | Public (UUID cart) |
| `GET` | `/api/cart` | `/api/v1/cart` | Bearer token |
| `POST` | `/api/cart/items` | `/api/v1/cart/items` | Bearer token |
| `PATCH` | `/api/cart/items/{itemId}` | `/api/v1/cart/items/{itemId}` | Bearer token |
| `DELETE` | `/api/cart/items/{itemId}` | `/api/v1/cart/items/{itemId}` | Bearer token |
| `GET` | `/api/categories` | `/api/v1/categories` | Public |
| `GET` | `/api/categories/{id}` | `/api/v1/categories/{id}` | Public |
| `POST` | `/api/categories` | `/api/v1/categories` | ADMIN token |
| `PUT` | `/api/categories/{id}` | `/api/v1/categories/{id}` | ADMIN token |
| `GET` | `/api/products` | `/api/v1/products` | Public |
| `GET` | `/api/products/{id}` | `/api/v1/products/{id}` | Public |
| `POST` | `/api/products` | `/api/v1/products` | ADMIN token |
| `PUT` | `/api/products/{id}` | `/api/v1/products/{id}` | ADMIN token |
| `POST` | `/api/products/{id}/variants` | `/api/v1/products/{id}/variants` | ADMIN token |
| `PUT` | `/api/products/{id}/variants/{variantId}` | `/api/v1/products/{id}/variants/{variantId}` | ADMIN token |

Security matchers use the same new paths: authentication POST routes and catalog GET routes are public; UUID carts remain public; customer profile and customer cart require a valid bearer token; catalog writes require `ADMIN`. The older UUID cart API still cannot access a customer-owned cart.

Use the versioned paths in every `curl` example in the [API guides](../README.md). Existing `/api/...` requests should be considered removed; update callers to `/api/v1/...`.

Verification: the complete PostgreSQL-backed Maven suite ran with `mvn --batch-mode test` in the project's Java 25 Maven Docker image against an isolated database copy. Result: **21 tests, 0 failures, 0 errors, 0 skipped**. A live Docker smoke test confirmed public reads, protected customer/cart calls, and that an authenticated request to the former `/api/products` route returns `404`.
