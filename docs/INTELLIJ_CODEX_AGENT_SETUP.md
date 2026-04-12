# IntelliJ + Codex Agent Setup

이 프로젝트에서 Codex를 코딩 하네스로 쓰는 최소 실전 셋업이다.

## 1. 한 번만 설치

```sh
chmod +x scripts/*.sh .githooks/pre-push
./scripts/install-agent-hooks.sh
```

설치 후 이 repo의 `git push` 직전에 자동으로 아래가 실행된다.

```sh
./scripts/agent-review.sh
./scripts/agent-check.sh changed
```

Redis가 필요한 테스트를 돌릴 때만 켠다.

```sh
docker compose -f docker-compose.dev.yml up -d redis
```

## 2. IntelliJ에서 기본 터미널 확인

IntelliJ Terminal에서 아래가 통과해야 한다.

```sh
java -version
./gradlew --version
./scripts/agent-check.sh smoke
```

이 프로젝트는 Java 21 toolchain을 사용한다.

## 3. Codex에게 작업 주는 형식

가장 안정적인 형식:

```text
Goal:
Scope:
Constraints:
Done when:
```

예시:

```text
Goal: 결제 중복 승인 실패 케이스 보강
Scope: PaymentServiceImpl, PaymentServiceTest
Constraints:
- schema 변경 금지
- 결제 API 응답 DTO 변경 금지
Done when:
- 관련 테스트 추가 또는 수정
- ./scripts/agent-check.sh changed 통과
```

## 4. 작업 루프

1. `분석만 해`로 원인과 최소 수정안을 먼저 받는다.
2. `Scope`를 좁혀 구현을 맡긴다.
3. Codex가 `./scripts/agent-check.sh changed`를 돌리게 한다.
4. 끝나면 `리뷰어 역할로만 봐`로 자기검토를 분리한다.
5. push 직전에 hook이 다시 검증한다.

## 5. 자주 쓰는 명령

```sh
./scripts/agent-review.sh
./scripts/agent-check.sh changed
./scripts/agent-check.sh quick
./scripts/agent-check.sh full
```

일시적으로 hook을 건너뛰어야 할 때:

```sh
SKIP_AGENT_HOOKS=1 git push
```

검증 강도를 바꿔 push할 때:

```sh
AGENT_HOOK_MODE=quick git push
AGENT_HOOK_MODE=full git push
```

## 6. PR 전 체크

PR 만들기 전에 Codex에게 이렇게 시킨다.

```text
리뷰어 역할로만 봐.
현재 diff 기준으로
1) 버그 가능성
2) 회귀 위험
3) 빠진 테스트
를 심각도 순서로 정리해.
구현은 하지 마.
```

그 다음:

```sh
./scripts/agent-review.sh
./scripts/agent-check.sh quick
```

CI는 GitHub에서 `./gradlew clean test`와 Jacoco report를 다시 실행한다.
