# Milestone 4: customer authentication

V7 adds the customers table. Each row has a stable ID, name, normalized email, BCrypt password hash, active flag, role, and timestamps. The migration leaves previous catalog and cart rows intact. A PostgreSQL unique index on lower(email) backs up the application's duplicate-email check.

Registration accepts only name, email, and password. The server always creates an active CUSTOMER account and hashes its password. The response has no password field. Login normalizes the email, asks Spring Security's AuthenticationManager to check the password, and returns a short-lived signed JWT. Invalid credentials have one generic error whether the email is unknown or the password is wrong.

The JWT's subject and customer_id claim both hold the stable customer ID. The decoder verifies HS256 signature, expiration, issuer, and required claims. Spring Security's resource server validates bearer tokens; its authentication converter reloads the customer to check that the account is still active and the role still matches. Thus deactivation or role changes invalidate existing tokens. GET /api/v1/customers/me obtains the ID from the token and returns the current safe profile.

SecurityFilterChain leaves registration, login, and catalog reads public. Product and category writes require ADMIN. The existing anonymous carts are left as they were for this milestone. Authentication uses an Authorization: Bearer header, not a cookie, and is stateless, so CSRF protection is disabled. A browser does not automatically send this header to another site. For deployment, use HTTPS and keep the key outside source control.

There is no public way to create an admin. To test admin operations on an isolated development database, register a customer, promote that row through a privileged PostgreSQL session, and log in again. See [the authentication API guide](../api-docs/05-authentication-api.md) for the commands, environment variables, and example requests.
