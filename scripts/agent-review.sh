#!/usr/bin/env sh

set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "agent-review: not inside a git repository" >&2
  exit 1
fi

changed_files="$(
  {
    git diff --name-only --diff-filter=ACMRT 2>/dev/null
    git diff --name-only --diff-filter=ACMRT --cached 2>/dev/null
    git ls-files --others --exclude-standard 2>/dev/null
  } | sort -u
)"

if [ -z "$changed_files" ]; then
  echo "agent-review: no changed files"
  exit 0
fi

echo "agent-review: changed files"
printf '%s\n' "$changed_files" | sed 's/^/  - /'

failures=""
warnings=""

add_failure() {
  failures="${failures}
- $1"
}

add_warning() {
  warnings="${warnings}
- $1"
}

for file in $changed_files; do
  case "$file" in
    .env|*.env|deploy.env|application-dev.yml|*/application-dev.yml)
      add_failure "Possible secret or local env file changed: $file"
      ;;
    .graphify*|graphify-out/*|*/graphify-out/*)
      add_failure "Agent graph artifact should not be committed: $file"
      ;;
    build/*|*/build/*|.gradle/*|.gradle-home/*|.gradle-local/*)
      add_failure "Generated build or Gradle cache artifact changed: $file"
      ;;
    .idea/*|.vscode/*|*.iml|*.iws|*.ipr)
      add_warning "IDE metadata changed: $file"
      ;;
    logs/*|*.log)
      add_warning "Log artifact changed: $file"
      ;;
  esac
done

main_java_changed="$(
  printf '%s\n' "$changed_files" | grep '^src/main/java/.*\.java$' || true
)"
test_java_changed="$(
  printf '%s\n' "$changed_files" | grep '^src/test/java/.*Test\.java$' || true
)"

if [ -n "$main_java_changed" ] && [ -z "$test_java_changed" ]; then
  add_warning "Production Java changed without a changed *Test.java file. Confirm this is config-only, refactor-only, or already covered."
fi

if [ -n "$warnings" ]; then
  echo "agent-review: warnings"
  printf '%s\n' "$warnings"
fi

if [ -n "$failures" ]; then
  echo "agent-review: failures"
  printf '%s\n' "$failures"
  exit 1
fi

echo "agent-review: passed"
