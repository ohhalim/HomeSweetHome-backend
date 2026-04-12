# HomeSweetHome Backend Agent Guide

## Project Context

HomeSweetHome is a Java 21 / Spring Boot 3.5 backend for a home-living ecommerce service.
Core domains are order/payment, product/cart/review/category, auth, and community.

Important implementation themes:

- Payment uses Toss Payments, Redis idempotency keys, order-scoped Redis locks, and compensation cancel logic.
- Tests should use the `test` profile and mock Toss payment behavior where available.
- Community uses Redis counters, Lua-based atomic operations, write-behind synchronization, cache-aside reads, and bulk counter lookup.
- QueryDSL generated `Q*` classes and build outputs are generated artifacts.

## Repo Map

- `src/main/java/com/homesweet/homesweetback/domain/auth/**`: auth, login, token, user.
- `src/main/java/com/homesweet/homesweetback/domain/community/**`: posts, comments, likes, Redis counters, cache sync.
- `src/main/java/com/homesweet/homesweetback/domain/order/**`: order and payment flow.
- `src/main/java/com/homesweet/homesweetback/domain/product/**`: product, cart, review, category.
- `src/test/java/**`: unit and integration tests.
- `scripts/agent-check.sh`: default verification harness.
- `scripts/agent-review.sh`: deterministic local review guard.
- `.githooks/pre-push`: local push gate installed by `scripts/install-agent-hooks.sh`.
- `docs/AI_AGENT_HARNESS.md`: prompt patterns for analysis, implementation, and review.
- `docs/INTELLIJ_CODEX_AGENT_SETUP.md`: IntelliJ + Codex + Mac setup guide.

## Working Rules

- Do not edit `.env`, real credentials, generated build outputs, Gradle caches, IDE metadata, or graph/report output unless the task explicitly requires it.
- Preserve existing user changes. Check `git status --short` before broad edits and avoid reverting unrelated dirty files.
- Keep changes scoped to the requested domain. Avoid unrelated cleanup while fixing behavior.
- Prefer existing package structure, Spring configuration style, mapper/repository patterns, and test fixtures.
- Follow `Controller -> Service -> Repository`. Do not move business logic into controllers.
- Keep DTO/entity conversion out of controllers when possible.
- Use Korean for user-facing summaries unless the user asks otherwise.
- When changing auth, payment, Redis, concurrency, or DB schema behavior, start with analysis or a small plan before editing.
- When changing payment or community concurrency behavior, add or update tests for duplicate calls, lock/idempotency behavior, or counter consistency.
- Do not commit agent artifacts such as `graphify-out/`, `.graphify*`, temp reports, or local scratch files.

## Task Input Template

Prefer prompts with four fields:

- `Goal`: what should change
- `Scope`: target package, classes, or files
- `Constraints`: what must stay untouched
- `Done when`: verification and acceptance criteria

Example:

```text
Goal: 커뮤니티 게시글 수정 API 버그 수정
Scope: CommunityPostService, CommunityPostServiceTest
Constraints: schema 변경 금지, controller/service/test만 수정
Done when:
- 관련 테스트 추가
- ./scripts/agent-check.sh changed 통과
```

## Execution Modes

- `Analyze only`: no edits. Return root causes, smallest fix, and risks first.
- `Implement`: make narrow edits, add or adjust tests, then run the smallest meaningful check.
- `Review`: act as a reviewer only. Prioritize bugs, regressions, and missing tests over style feedback.

## Standard Commands

Use the local harness script first:

```sh
./scripts/agent-check.sh changed
./scripts/agent-check.sh quick
```

Available modes:

```sh
./scripts/agent-check.sh smoke    # context-load smoke test
./scripts/agent-check.sh changed  # run changed test classes, or quick if none
./scripts/agent-check.sh quick    # Gradle test with test profile
./scripts/agent-check.sh full     # clean test with test profile
./scripts/agent-check.sh build    # clean build with test profile
```

Run deterministic local review before PR or push:

```sh
./scripts/agent-review.sh
```

Install repo-local hooks once per clone:

```sh
./scripts/install-agent-hooks.sh
```

Direct Gradle fallback:

```sh
SPRING_PROFILES_ACTIVE=test ./gradlew test
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
SPRING_PROFILES_ACTIVE=test ./gradlew build
```

Some integration paths expect Redis at `127.0.0.1:6379` / `localhost:6379`.
Start only Redis from the dev compose file when needed:

```sh
docker compose -f docker-compose.dev.yml up -d redis
```

## Agent Workflow

1. Read the relevant code, tests, and current `git status --short`.
2. For non-trivial work, restate a minimal plan before editing.
3. Make narrow edits inside the requested scope.
4. Run `changed` first when possible. Use `quick` or `full` when risk is higher.
5. Finish with changed files, commands run, failed or skipped checks, and remaining risk.

## Review Checklist

Before finishing, verify:

- The code compiles and tests are aligned with the changed behavior.
- Transaction boundaries, Redis keys, soft-delete rules, and external API failure paths remain explicit.
- DTO/API changes are reflected in tests and docs when user-facing.
- New scripts are executable and portable to macOS/Linux shell.
- No secrets, local env values, or generated reports were committed by accident.
