# Project instructions

## Git operations

When the user asks you to commit or push changes to this project, run the Git commands on the WSL host, outside the VS Code development container. The project directory on the host is `/home/guosong/dev/ecom-backend`. Changes made in the development container are visible in this shared working tree.

Before committing, inspect the host working tree and commit only the intended changes. If a host terminal is unavailable, say so and request host access; do not silently run the commit or push from inside the development container. Do not commit or push unless the user has asked for it.

## Running the application

Run the application with the project's normal Docker Compose files against the host Docker daemon. Use `compose.yaml` for the isolated PostgreSQL setup or `compose.existing-db.yaml` when using the existing database, as appropriate. Do not start the application directly with `java`, `mvn spring-boot:run`, or a separate Docker daemon inside the development container. The development container provides the JDK and editor tooling; the application remains in its regular Compose-managed containers.

The shared development container at `/home/guosong/dev/.devcontainer` includes the Docker-outside-of-Docker feature, forwards the host Docker socket, and sets `DOCKER_HOST` so `docker compose` uses the host daemon from its terminal. The same Compose commands may also be run from the WSL host.
