# Reddit-Style Features Documentation

Reddit 스타일 커뮤니티 기능 구현 문서

## 📋 목차

1. [개요](#개요)
2. [주요 기능](#주요-기능)
3. [엔티티 구조](#엔티티-구조)
4. [API 설계](#api-설계)
5. [알고리즘 설명](#알고리즘-설명)
6. [성능 최적화](#성능-최적화)
7. [테스트](#테스트)

---

## 개요

HomeSweetHome 백엔드에 Reddit 스타일의 커뮤니티 기능을 추가하였습니다.

### 구현된 기능

✅ **Subreddit (서브레딧)** - 주제별 독립 커뮤니티
✅ **Upvote/Downvote** - 투표 시스템
✅ **Hot/Top/Controversial 정렬** - 다양한 정렬 알고리즘
✅ **Karma (카르마)** - 사용자 신뢰도 점수
✅ **Moderator System** - 커뮤니티 운영자 시스템

---

## 주요 기능

### 1. Subreddit (서브레딧)

Reddit의 "r/programming", "r/funny"와 같은 주제별 커뮤니티

#### 특징
- **독립적인 커뮤니티**: 각 서브레딧마다 독립적인 게시글/댓글 공간
- **구독 시스템**: 사용자가 관심 있는 서브레딧을 구독
- **모더레이터 관리**: 서브레딧별 운영자 지정
- **접근 제어**: Private, NSFW 설정 가능

#### 주요 기능
```java
// 서브레딧 생성
SubredditEntity createSubreddit(String name, String title, String description,
                               Boolean isPrivate, Boolean isNsfw, Long userId)

// 구독/구독 취소
void subscribeToSubreddit(Long subredditId, Long userId)
void unsubscribeFromSubreddit(Long subredditId, Long userId)

// 모더레이터 추가
void addModerator(Long subredditId, Long userId, Long targetUserId,
                 ModeratorPermission permission)
```

#### 엔티티
- **SubredditEntity**: 서브레딧 기본 정보
- **SubredditSubscriptionEntity**: 구독 관계
- **SubredditModeratorEntity**: 모더레이터 권한

---

### 2. Upvote/Downvote System

Reddit 스타일 투표 시스템으로 게시글/댓글의 인기도를 결정

#### 특징
- **Upvote (+1)**: 좋은 콘텐츠에 대한 긍정 투표
- **Downvote (-1)**: 부적절한 콘텐츠에 대한 부정 투표
- **Score 계산**: `score = upvotes - downvotes`
- **투표 변경 가능**: Upvote ↔ Downvote 전환
- **투표 취소 가능**: 같은 투표 다시 클릭 시 취소

#### 동작 방식
```java
// 투표 토글
togglePostVote(postId, userId, VoteType.UPVOTE)

// 가능한 시나리오:
// 1. 투표 없음 → Upvote 추가
// 2. Upvote 있음 → Upvote 취소 (투표 제거)
// 3. Downvote 있음 → Upvote로 변경
```

#### 동시성 제어
- **분산 락(Distributed Lock)** 사용
- Redis를 통한 투표 카운트 캐싱
- 중복 투표 방지

---

### 3. 정렬 알고리즘

Reddit의 다양한 정렬 알고리즘 구현

#### 3.1 HOT 알고리즘

**최근 + 인기 있는 게시글**

```java
hot_score = log10(max(|score|, 1)) * sign(score) + (created_time / 45000)
```

- 점수가 높을수록 순위 상승
- 시간이 최근일수록 순위 상승
- 45000초(12.5시간)마다 점수 1점과 동등한 가치

**예시**:
- 1시간 전 게시글 (score: 50) → HOT 점수 높음
- 1주일 전 게시글 (score: 1000) → HOT 점수 중간
- 1년 전 게시글 (score: 10000) → HOT 점수 낮음

#### 3.2 TOP 알고리즘

**기간별 최고 점수**

```java
// 단순히 score 순으로 정렬
ORDER BY score DESC
```

기간 옵션:
- **HOUR**: 1시간
- **DAY**: 24시간
- **WEEK**: 1주일
- **MONTH**: 1개월
- **YEAR**: 1년
- **ALL_TIME**: 전체

#### 3.3 NEW 알고리즘

**최신순 정렬**

```java
ORDER BY created_at DESC
```

#### 3.4 CONTROVERSIAL 알고리즘

**논쟁적인 게시글 (Upvote와 Downvote가 비슷한 글)**

```java
controversial_score = min(upvotes, downvotes) / max(upvotes, downvotes)
                     * log10(upvotes + downvotes)
```

- Upvote:Downvote 비율이 1:1에 가까울수록 높은 점수
- 총 투표 수가 많을수록 중요도 상승

**예시**:
- 100 upvotes, 95 downvotes → 매우 논쟁적
- 100 upvotes, 5 downvotes → 논쟁적이지 않음

#### 3.5 RISING 알고리즘

**급상승 게시글**

```java
rising_score = (score / age_in_minutes) + (comment_count * 0.1)
```

- 최근 1-3시간 내 생성
- 짧은 시간에 높은 점수를 받은 글

---

### 4. Karma (카르마) System

사용자 신뢰도 점수

#### 특징
- **Post Karma**: 게시글이 받은 투표 기반
- **Comment Karma**: 댓글이 받은 투표 기반
- **Total Karma**: Post + Comment Karma 합계
- **실시간 계산**: Redis 캐싱으로 빠른 조회

#### 카르마 계산
```java
Post Karma = (게시글 Upvotes) - (게시글 Downvotes)
Comment Karma = (댓글 Upvotes) - (댓글 Downvotes)
Total Karma = Post Karma + Comment Karma
```

#### 카르마 레벨
| 레벨 | 카르마 범위 | 설명 |
|------|-------------|------|
| NEGATIVE | < 0 | 음수 카르마 |
| NEWBIE | 0-99 | 새내기 |
| REGULAR | 100-499 | 일반 |
| CONTRIBUTOR | 500-1,999 | 기여자 |
| VETERAN | 2,000-4,999 | 베테랑 |
| EXPERT | 5,000-9,999 | 전문가 |
| LEGEND | 10,000+ | 전설 |

---

### 5. Moderator (운영자) System

서브레딧별 운영자 관리

#### 권한 종류
- **FULL**: 모든 권한 (서브레딧 설정, 모더레이터 관리 등)
- **POSTS**: 게시글 관리 권한
- **COMMENTS**: 댓글 관리 권한

#### 특징
- 서브레딧 생성자는 자동으로 FULL 권한 모더레이터
- FULL 권한자만 다른 모더레이터 추가 가능
- 서브레딧별 독립적인 모더레이터 관리

---

## 엔티티 구조

### ERD 요약

```
SubredditEntity (1) ──────── (*) SubredditSubscriptionEntity (*) ──────── (1) User
       │                                                                      │
       │ (1)                                                                  │
       │                                                                      │
       │                                                                      │
       └──────── (*) CommunityPostEntity (1) ──────── (*) PostVoteEntity (*) ┘
                      │
                      │ (1)
                      │
                      └──────── (*) CommunityCommentEntity (1) ──────── (*) CommentVoteEntity
```

### 주요 필드

#### CommunityPostEntity
```java
- postId: Long
- author: User
- subreddit: SubredditEntity (NEW)
- title: String
- content: String
- upvoteCount: Integer (NEW)
- downvoteCount: Integer (NEW)
- score: Integer (NEW)
- viewCount: Integer
- likeCount: Integer
- commentCount: Integer
```

#### PostVoteEntity
```java
- post: CommunityPostEntity (복합키)
- user: User (복합키)
- voteType: VoteType (UPVOTE / DOWNVOTE)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

---

## 성능 최적화

### 1. Redis 캐싱
```java
// 투표 카운트 캐싱
"community:post:vote:{postId}:upvotes"
"community:post:vote:{postId}:downvotes"
"community:post:vote:{postId}:score"

// 카르마 캐싱
"karma:user:{userId}:post"
"karma:user:{userId}:comment"
```

### 2. 분산 락
```java
@DistributedLock(
    key = "'community:post:vote:' + #postId + ':' + #userId",
    waitTime = 5,
    leaseTime = 3
)
```

### 3. 데이터베이스 인덱스
```sql
-- Score 기반 정렬 인덱스
CREATE INDEX idx_subreddit_score ON community_posts(subreddit_id, score DESC, created_at DESC);

-- HOT 정렬 인덱스
CREATE INDEX idx_subreddit_hot ON community_posts(subreddit_id, created_at DESC);

-- 투표 조회 인덱스
CREATE INDEX idx_post_user ON post_votes(post_id, user_id);
```

### 4. N+1 문제 해결
```java
@EntityGraph(attributePaths = {"author", "subreddit"})
List<CommunityPostEntity> findBySubreddit_SubredditIdAndIsDeletedFalse(Long subredditId);
```

---

## API 설계

### Subreddit API

#### 서브레딧 생성
```http
POST /api/subreddits
Content-Type: application/json

{
  "name": "programming",
  "title": "프로그래밍",
  "description": "프로그래밍 토론 커뮤니티",
  "isPrivate": false,
  "isNsfw": false
}
```

#### 서브레딧 조회
```http
GET /api/subreddits/r/{name}
```

#### 구독/구독 취소
```http
POST /api/subreddits/{id}/subscribe
DELETE /api/subreddits/{id}/subscribe
```

### Vote API

#### 게시글 투표
```http
POST /api/posts/{id}/vote
Content-Type: application/json

{
  "voteType": "UPVOTE"  // or "DOWNVOTE"
}
```

#### 댓글 투표
```http
POST /api/comments/{id}/vote
Content-Type: application/json

{
  "voteType": "UPVOTE"
}
```

### Sorting API

#### HOT 게시글
```http
GET /api/posts/hot?page=0&size=20
GET /api/subreddits/r/{name}/posts/hot?page=0&size=20
```

#### TOP 게시글
```http
GET /api/posts/top?period=DAY&page=0&size=20
GET /api/posts/top?period=WEEK&page=0&size=20
GET /api/posts/top?period=ALL_TIME&page=0&size=20
```

#### CONTROVERSIAL 게시글
```http
GET /api/posts/controversial?period=DAY&page=0&size=20
```

### Karma API

#### 사용자 카르마 조회
```http
GET /api/users/{id}/karma
```

응답:
```json
{
  "userId": 1,
  "postKarma": 1500,
  "commentKarma": 800,
  "totalKarma": 2300,
  "level": "CONTRIBUTOR"
}
```

---

## 테스트

### Unit Tests

#### VoteServiceTest
```java
- testTogglePostVote_NewUpvote: 새 Upvote 추가
- testTogglePostVote_CancelUpvote: Upvote 취소
- testTogglePostVote_ChangeUpvoteToDownvote: 투표 변경
- testScoreCalculation: 점수 계산 검증
```

#### SubredditServiceTest
```java
- testCreateSubreddit_Success: 서브레딧 생성
- testCreateSubreddit_DuplicateName: 중복 이름 검증
- testSubscribeToSubreddit_Success: 구독 성공
- testHasModeratorPermission: 모더레이터 권한 확인
```

#### PostSortingServiceTest
```java
- testGetHotPosts: HOT 정렬
- testGetTopPosts: TOP 정렬
- testGetControversialPosts: CONTROVERSIAL 정렬
```

### Integration Tests

Testcontainers를 활용한 통합 테스트 (MySQL, Redis)

---

## 향후 개선 사항

### 1. 성능 개선
- [ ] 정렬 알고리즘 캐싱 (Hot, Top 등)
- [ ] 카르마 계산 배치 처리
- [ ] 서브레딧 통계 실시간 업데이트

### 2. 기능 추가
- [ ] 게시글 필터링 (Flair, Tag)
- [ ] 서브레딧 규칙 설정
- [ ] 사용자 차단/신고 시스템
- [ ] Award/Badge 시스템

### 3. 모니터링
- [ ] 투표 패턴 이상 감지
- [ ] 스팸 탐지
- [ ] 모더레이터 활동 로그

---

## 참고 자료

- [Reddit Ranking Algorithm](https://medium.com/hacking-and-gonzo/how-reddit-ranking-algorithms-work-ef111e33d0d9)
- [Reddit API Documentation](https://www.reddit.com/dev/api/)
- [Spring Data JPA Best Practices](https://vladmihalcea.com/tutorials/spring/)

---

**작성일**: 2025-11-18
**작성자**: ohhalim777@gmail.com
