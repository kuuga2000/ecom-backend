#!/usr/bin/env bash
set -euo pipefail

# This marker belongs to the container, so recreation runs setup again.
marker=/tmp/ecom-backend-post-create.done
if [[ ! -f "$marker" ]]; then
  curl -fsSL https://chatgpt.com/codex/install.sh | sh
  touch "$marker"
fi
