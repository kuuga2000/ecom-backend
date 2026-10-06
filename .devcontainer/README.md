# Start the development workspace with Docker Compose

Run these commands from the WSL host in `/home/guosong/dev/ecom-backend`.
Use `create-dev.sh` to build and start the workspace, run post-create setup,
and open a shell. VS Code is not required.

```bash
cd /home/guosong/dev/ecom-backend
./create-dev.sh
```

Inside the workspace, `docker` and `docker compose` use the host Docker daemon.
To start this project's API, first configure the required values in the
project's `.env` file, then run the usual application command:

```bash
docker compose -f compose.yaml up -d --build
```

Use `compose.existing-db.yaml` instead when connecting to the existing
PostgreSQL network. The workspace does not run the API itself.

To stop the workspace from the host:

```bash
docker compose -f .devcontainer/compose.yaml down
```

The Maven cache and `/home/guosong/.codex` are stored in named Docker volumes,
so they survive `down` and container rebuilds. `down -v` deletes those volumes.
If you are migrating Codex data from an older container, restore your existing
backup after starting this workspace, before launching Codex.

# Open the running workspace in VS Code

After starting the workspace with Docker Compose, run `code .` from the WSL
project directory. Select **Dev Containers: Reopen in Container** in VS Code.
The project's `devcontainer.json` uses `compose.yaml`, which references
the existing image without a build step. Do not select **Rebuild Container**;
that command explicitly requests a rebuild.

If you prefer to attach directly, select **Dev Containers: Attach to Running
Container** and choose `ecom-backend-dev-workspace-1`.

If you have an `agent-backup/.codex` backup from an older container, copy its
contents into the persistent Codex volume before opening Codex:

```bash
docker compose -f .devcontainer/compose.yaml exec workspace bash -lc \
  'cp -a agent-backup/.codex/. "$HOME/.codex/"'
```

Launcher options:

- `./create-dev.sh` or `./create-dev.sh up`: build as needed, start the workspace, run setup, and open a shell.
- `./create-dev.sh build`: build the workspace image without starting a container.
- `./create-dev.sh rebuild`: build, recreate the workspace container, run setup, and open a shell.

Docker Compose does not execute `devcontainer.json` lifecycle hooks. The
launcher explicitly runs `.devcontainer/post-create.sh`, which is also the
VS Code post-create hook. Setup runs once per container; if it fails, the
next launch retries it. Building an image alone does not run post-create setup.
