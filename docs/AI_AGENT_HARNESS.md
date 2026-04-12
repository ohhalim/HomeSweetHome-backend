# AI Agent Harness

이 문서는 AI 에이전트에게 일을 맡길 때 범위가 퍼지지 않고, 테스트와 리뷰가 기본으로 따라오게 만드는 최소 루프를 정리한다.

기본 원칙은 단순하다.

1. 먼저 분석만 시킨다.
2. 수정 범위를 제한한다.
3. 검증 명령을 완료 기준에 넣는다.
4. 마지막에 리뷰 모드로 다시 본다.

자세한 repo 규칙은 루트의 `AGENTS.md`를 기준으로 한다.
IntelliJ + Codex + Mac 기준 설치 절차는 `docs/INTELLIJ_CODEX_AGENT_SETUP.md`를 기준으로 한다.

## 바로 쓰는 프롬프트 템플릿

### 1. 분석만 시킬 때

```text
수정하지 말고 분석만 해.
대상: CommunityPostService, CommunityPostRepository, 관련 테스트
출력:
1) 원인 후보 3개
2) 가장 작은 수정안
3) 위험한 부작용
```

### 2. 범위를 제한해서 구현할 때

```text
다음 조건으로만 구현해.
Goal: 게시글 삭제 시 이미지 정리 정책 반영
Scope: CommunityPostEntity, CommunityImageEntity, 관련 테스트
Constraints:
- schema 변경 금지
- 새 패키지 추가 금지
- 필요한 파일만 수정
Done when:
- 관련 테스트 추가 또는 수정
- ./scripts/agent-check.sh changed 통과
```

### 3. 리뷰만 시킬 때

```text
리뷰어 역할로만 봐.
구현은 하지 말고,
1) 버그 가능성
2) 회귀 위험
3) 빠진 테스트
를 심각도 순서로 정리해.
스타일 칭찬은 제외해.
```

## 좋은 작업 요청의 재료

- 대상 도메인이나 파일
- 기대 동작
- 재현 방법 또는 실패 케이스
- 수정하면 안 되는 범위
- 완료 기준과 검증 명령

애매한 요청보다 아래 형식이 훨씬 낫다.

```text
Goal:
Scope:
Constraints:
Done when:
```

## Verification Modes

기본 검증 루프는 `./scripts/agent-check.sh`를 사용한다.

| Mode | Purpose |
| --- | --- |
| `smoke` | Spring context smoke test |
| `changed` | Changed test classes only, falls back to `quick` |
| `quick` | Full Gradle test task with `test` profile |
| `full` | Clean test run |
| `build` | Clean build |

예시:

```sh
./scripts/agent-review.sh
./scripts/agent-check.sh changed
./scripts/agent-check.sh quick
```

로컬 push hook을 설치하려면 한 번만 실행한다.

```sh
./scripts/install-agent-hooks.sh
```

Redis-backed tests expect `127.0.0.1:6379` / `localhost:6379`.
If Redis is not running:

```sh
docker compose -f docker-compose.dev.yml up -d redis
```

## 추천 작업 루프

1. 분석만 요청
2. 가장 작은 수정안 선택
3. 범위를 제한한 구현 요청
4. `changed` 또는 `quick` 실행
5. 리뷰 모드로 다시 점검

## 금지할 오용

- "전체 구조 개선해줘" 같이 범위 없는 요청
- 테스트 없이 구현만 시키기
- schema 변경, 패키지 재구성, 설정 파일 변경을 한 번에 섞기
- 실패 원인 설명 없이 그냥 통과할 때까지 땜질하게 만들기
- 에이전트 산출물이나 임시 파일을 그대로 커밋하기

## Acceptance Record

각 작업의 마지막 응답에는 최소한 아래가 있어야 한다.

- 무엇이 바뀌었는지
- 어떤 검증을 돌렸는지
- 실패하거나 건너뛴 체크가 있는지
- 남은 위험이 무엇인지
