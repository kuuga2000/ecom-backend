I want to build an ecommerce application to learn Java Spring Boot. Act as my senior engineer and build it with me step by step.

The workspace will eventually contain two separate projects: `ecom-backend/` for the Spring Boot REST API and `ecom-frontend/` for a React Router and Vite storefront. **For now, work only in `ecom-backend/`. Do not create the frontend yet.**

Use **Java 25 LTS** and the **latest stable Spring Boot release available when you create the project**. Configure Maven to compile for Java 25, and use Java 25 to run the backend. Choose dependency versions compatible with both. Use Spring Web, Spring Data JPA, PostgreSQL, Flyway, and Maven. Use standard Docker and Docker Compose for local development. **Do not create or use a Dev Container or `.devcontainer/` configuration.** Configure the database connection through environment variables so it can later point to Supabase.

First, briefly propose the backend folder structure, database tables, API endpoints, and milestones. Then implement **Milestone 1 only**:

* Create a runnable Spring Boot project in `ecom-backend/`.
* Add Docker Compose for PostgreSQL and a Flyway migration for products.
* Add a small set of sample products.
* Implement `GET /api/products` and `GET /api/products/{id}` using controller, service, repository, entity, and response DTO layers.
* Return a clear `404` response when a product does not exist.
* Add meaningful tests for the product endpoints.
* Provide exact commands to start PostgreSQL, run the backend, and check both endpoints with `curl`.

Explain the Spring Boot concepts as we encounter them. Assume I am an experienced backend engineer who is new to Spring Boot. Stop after Milestone 1, summarize what works, and propose the next backend milestone. We will build `ecom-frontend/` only after the backend API is ready.
