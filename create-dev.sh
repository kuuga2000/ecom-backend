#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
export DOCKER_GID="$(stat -c '%g' /var/run/docker.sock)"
compose=(docker compose -f .devcontainer/compose.yaml)
build_compose=("${compose[@]}" -f .devcontainer/compose.build.yaml)

case "${1:-up}" in
  build)
    "${build_compose[@]}" build
    exit 0
    ;;
  rebuild)
    "${build_compose[@]}" build
    "${compose[@]}" up -d --force-recreate
    ;;
  up)
    "${build_compose[@]}" up -d --build
    ;;
  *)
    echo "Usage: $0 [up|build|rebuild]" >&2
    exit 2
    ;;
esac

"${compose[@]}" exec -T workspace bash .devcontainer/post-create.sh
"${compose[@]}" exec workspace bash
