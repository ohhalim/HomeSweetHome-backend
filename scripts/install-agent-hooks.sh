#!/usr/bin/env sh

set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "install-agent-hooks: not inside a git repository" >&2
  exit 1
fi

chmod +x .githooks/pre-push
chmod +x scripts/agent-check.sh
chmod +x scripts/agent-review.sh

current_hooks_path="$(git config --get core.hooksPath || true)"
if [ -n "$current_hooks_path" ] && [ "$current_hooks_path" != ".githooks" ]; then
  echo "install-agent-hooks: replacing existing core.hooksPath: $current_hooks_path"
fi

git config core.hooksPath .githooks
echo "install-agent-hooks: core.hooksPath set to .githooks"
echo "install-agent-hooks: pre-push will run ./scripts/agent-review.sh and ./scripts/agent-check.sh changed"
