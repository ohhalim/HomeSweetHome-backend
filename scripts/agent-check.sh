#!/usr/bin/env sh

set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-quick}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
export SPRING_PROFILES_ACTIVE

usage() {
  cat <<'EOF'
Usage: ./scripts/agent-check.sh [mode]

Modes:
  smoke    Run the Spring context smoke test.
  changed  Run changed test classes, or quick if no changed test classes exist.
  quick    Run the Gradle test task with the test profile.
  full     Run clean test with the test profile.
  build    Run clean build with the test profile.

Environment:
  AGENT_CHECK_REDIS=auto|require|skip  Redis preflight behavior. Default: auto.
  REDIS_HOST=127.0.0.1                 Redis host for preflight.
  REDIS_PORT=6379                      Redis port for preflight.
EOF
}

log() {
  printf '%s\n' "$*"
}

fail() {
  printf 'agent-check: %s\n' "$*" >&2
  exit 1
}

redis_reachable() {
  host="${REDIS_HOST:-127.0.0.1}"
  port="${REDIS_PORT:-6379}"

  if command -v redis-cli >/dev/null 2>&1; then
    redis-cli -h "$host" -p "$port" --connect-timeout 1 --socket-timeout 1 ping >/dev/null 2>&1
    return $?
  fi

  if command -v nc >/dev/null 2>&1; then
    case "$(uname -s)" in
      Darwin*)
        nc -G 1 -z "$host" "$port" >/dev/null 2>&1
        return $?
        ;;
      *)
        nc -w 1 -z "$host" "$port" >/dev/null 2>&1
        return $?
        ;;
    esac
  fi

  return 2
}

redis_preflight() {
  behavior="${AGENT_CHECK_REDIS:-auto}"

  case "$behavior" in
    skip)
      log "Redis preflight skipped."
      return 0
      ;;
    auto|require)
      if redis_reachable; then
        log "Redis preflight passed."
        return 0
      fi

      if [ "$behavior" = "require" ]; then
        fail "Redis is not reachable. Start it with: docker compose -f docker-compose.dev.yml up -d redis"
      fi

      log "Redis is not reachable. Continuing because AGENT_CHECK_REDIS=auto."
      log "If Redis-backed tests fail, run: docker compose -f docker-compose.dev.yml up -d redis"
      return 0
      ;;
    *)
      fail "Invalid AGENT_CHECK_REDIS value: $behavior"
      ;;
  esac
}

run_gradle() {
  if [ ! -x ./gradlew ]; then
    fail "./gradlew is not executable"
  fi

  log "Running: SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE ./gradlew $*"
  set +e
  ./gradlew "$@"
  status=$?
  set -e

  log "Test report: build/reports/tests/test/index.html"
  log "Jacoco report: build/reports/jacoco/test/html/index.html"

  exit "$status"
}

changed_tests() {
  if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    return 0
  fi

  {
    git diff --name-only --diff-filter=ACMRT -- 'src/test/java/**/*Test.java' 2>/dev/null
    git diff --name-only --diff-filter=ACMRT --cached -- 'src/test/java/**/*Test.java' 2>/dev/null
    git ls-files --others --exclude-standard -- 'src/test/java' 2>/dev/null | grep 'Test\.java$' || true
  } | sort -u
}

case "$MODE" in
  -h|--help|help)
    usage
    ;;
  smoke)
    redis_preflight
    run_gradle test -Dspring.profiles.active=test --tests com.homesweet.homesweetback.HomesweetBackApplicationTests
    ;;
  changed)
    redis_preflight
    tests="$(changed_tests)"
    if [ -z "$tests" ]; then
      log "No changed test classes found. Falling back to quick."
      run_gradle test -Dspring.profiles.active=test
    fi

    set -- test -Dspring.profiles.active=test
    for file in $tests; do
      class_name="$(printf '%s' "$file" | sed 's#^src/test/java/##; s#/#.#g; s#\.java$##')"
      set -- "$@" --tests "$class_name"
    done
    run_gradle "$@"
    ;;
  quick)
    redis_preflight
    run_gradle test -Dspring.profiles.active=test
    ;;
  full)
    redis_preflight
    run_gradle clean test -Dspring.profiles.active=test
    ;;
  build)
    redis_preflight
    run_gradle clean build -Dspring.profiles.active=test
    ;;
  *)
    usage
    fail "Unknown mode: $MODE"
    ;;
esac
