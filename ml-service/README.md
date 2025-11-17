# HomeSweetHome Community ML Service 🤖

머신러닝 기반 커뮤니티 기능을 제공하는 FastAPI 마이크로서비스

## 🎯 주요 기능

### 1. 게시글 추천 시스템
- **협업 필터링** (Collaborative Filtering)
  - User-based CF
  - Item-based CF
- **콘텐츠 기반 필터링** (Content-based Filtering)
  - TF-IDF 벡터화
  - 코사인 유사도
- **하이브리드 추천** (가중 평균)
  - CF 60% + Content-based 40%

### 2. 스팸/악성 댓글 탐지
- **규칙 기반 탐지**
  - 스팸 키워드 검사
  - 악성 패턴 검사 (URL, 전화번호, 이메일)
  - 특수문자/대문자 과다 사용 검사
- **ML 기반 탐지**
  - TF-IDF + Ensemble (LR + RF + SVM)
  - 소프트 보팅
- **하이브리드 탐지**
  - 규칙 40% + ML 60%

### 3. 게시글 자동 태그 추출
- **TF-IDF 기반 키워드 추출**
- **빈도 기반 키워드 추출**
- **카테고리 자동 분류**
  - interior, furniture, living, diy, general

### 4. 트렌딩 게시글 예측
- **트렌딩 스코어 계산**
  - 조회수, 좋아요, 댓글, 공유 가중치
  - 시간 가중치 (Exponential Decay)
- **바이럴 계수 계산**
  - 바이럴 계수 = (공유 * 2 + 댓글) / 조회수
- **성장률 추정**
  - 시간당 engagement 증가율

---

## 🚀 빠른 시작

### Prerequisites
- Docker & Docker Compose
- Python 3.11+
- pip

### 1. Docker Compose로 실행 (권장)

```bash
cd ml-service
docker-compose up -d
```

**서비스 URL**:
- ML API: http://localhost:8000
- API Docs: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
- Redis: localhost:6379

### 2. 로컬 실행

```bash
cd ml-service

# 가상환경 생성
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 의존성 설치
pip install -r requirements.txt

# 서버 실행
uvicorn app.main:app --reload --port 8000
```

---

## 📡 API 엔드포인트

### Health Check

```bash
# 서비스 상태 확인
GET /health

# 모델 상태 확인
GET /models/status
```

### 1. 게시글 추천

```bash
POST /api/v1/ml/community/recommend
```

**Request**:
```json
{
  "user_id": 123,
  "user_liked_posts": [1, 2, 3],
  "k": 10,
  "use_hybrid": true
}
```

**Response**:
```json
{
  "user_id": 123,
  "recommendations": [
    {
      "post_id": 42,
      "score": 0.95,
      "rank": 1,
      "reason": "Very popular among similar users"
    }
  ],
  "total_count": 10
}
```

### 2. 스팸 탐지

```bash
POST /api/v1/ml/community/spam/detect
```

**Request**:
```json
{
  "text": "광고입니다! 클릭하세요 http://spam.com",
  "threshold": 0.5
}
```

**Response**:
```json
{
  "is_spam": true,
  "confidence": 0.87,
  "spam_score": 0.87,
  "category": "spam",
  "reasons": [
    "Contains promotion keywords: 광고, 클릭",
    "Contains suspicious pattern",
    "High spam probability by ML model"
  ]
}
```

### 3. 배치 스팸 탐지

```bash
POST /api/v1/ml/community/spam/detect/batch
```

**Request**:
```json
{
  "texts": [
    "좋은 정보 감사합니다",
    "광고입니다 클릭",
    "안녕하세요"
  ],
  "threshold": 0.5
}
```

### 4. 태그 추출

```bash
POST /api/v1/ml/community/tags/extract
```

**Request**:
```json
{
  "title": "북유럽 스타일 인테리어",
  "content": "북유럽 스타일로 거실을 꾸몄습니다. 화이트 톤 가구와 우드 소재를 활용했어요.",
  "max_tags": 5,
  "min_score": 0.1
}
```

**Response**:
```json
{
  "tags": [
    {
      "tag": "북유럽",
      "score": 0.92,
      "category": "interior"
    },
    {
      "tag": "인테리어",
      "score": 0.85,
      "category": "interior"
    },
    {
      "tag": "거실",
      "score": 0.73,
      "category": "living"
    },
    {
      "tag": "가구",
      "score": 0.68,
      "category": "furniture"
    }
  ],
  "total_count": 4
}
```

### 5. 트렌딩 예측

```bash
POST /api/v1/ml/community/trending/predict
```

**Request**:
```json
{
  "posts": [
    {
      "post_id": 1,
      "views": 1000,
      "likes": 50,
      "comments": 20,
      "shares": 5,
      "created_at": "2025-01-17T10:00:00Z"
    }
  ],
  "top_k": 10,
  "time_window_hours": 24
}
```

**Response**:
```json
{
  "trending_posts": [
    {
      "post_id": 1,
      "views": 1000,
      "likes": 50,
      "comments": 20,
      "shares": 5,
      "created_at": "2025-01-17T10:00:00Z",
      "trending_score": 345.67,
      "viral_coefficient": 0.125,
      "growth_rate": 15.2
    }
  ],
  "total_count": 1,
  "time_window_hours": 24
}
```

### 6. 바이럴 탐지

```bash
POST /api/v1/ml/community/trending/viral/detect
```

**Request**:
```json
{
  "post": {
    "post_id": 1,
    "views": 5000,
    "likes": 500,
    "comments": 200,
    "shares": 100,
    "created_at": "2025-01-17T10:00:00Z"
  },
  "threshold": 0.1
}
```

**Response**:
```json
{
  "post_id": 1,
  "is_viral": true,
  "viral_coefficient": 0.28,
  "growth_rate": 87.5,
  "confidence": 0.95
}
```

---

## 🔧 Spring Boot 연동

### 1. WebClient Configuration

```java
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

### 2. ML Service Client 사용

```java
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final MLServiceClient mlServiceClient;

    // 게시글 추천
    public Mono<List<Long>> getRecommendedPosts(Long userId, int count) {
        return mlServiceClient.getRecommendations(userId, null, count)
                .map(response -> response.recommendations().stream()
                        .map(item -> item.postId())
                        .toList());
    }

    // 스팸 탐지
    public Mono<Boolean> isSpam(String comment) {
        return mlServiceClient.detectSpam(comment)
                .map(MLServiceClient.MLSpamDetectionResponse::isSpam)
                .defaultIfEmpty(false);
    }

    // 태그 추출
    public Mono<List<String>> extractTags(String title, String content) {
        return mlServiceClient.extractTags(title, content, 5)
                .map(response -> response.tags().stream()
                        .map(MLServiceClient.MLTagExtractionResponse.TagItem::tag)
                        .toList());
    }
}
```

---

## 📊 모델 학습

### 추천 시스템 학습

```python
import pandas as pd
from app.models.recommendation import RecommendationModel

# 데이터 로드
user_interactions = pd.read_csv('data/user_interactions.csv')
post_contents = pd.read_csv('data/post_contents.csv')

# 모델 학습
model = RecommendationModel()
model.train(user_interactions, post_contents)

# 모델 저장
model.save_model('trained_models/recommendation_model.pkl')
```

### 스팸 탐지 모델 학습

```python
from app.models.spam_detection import SpamDetectionModel

# 데이터 로드
texts = ["정상 댓글", "스팸 광고", ...]
labels = [0, 1, ...]  # 0: 정상, 1: 스팸

# 모델 학습
model = SpamDetectionModel()
result = model.train(texts, labels, test_size=0.2)

print(f"Accuracy: {result['accuracy']:.4f}")

# 모델 저장
model.save_model('trained_models/spam_detection_model.pkl')
```

---

## 🧪 테스트

```bash
# 단위 테스트 실행
pytest

# 커버리지 리포트
pytest --cov=app --cov-report=html
```

---

## 📈 성능 최적화

### 1. 캐싱
- Redis를 사용한 추천 결과 캐싱
- TTL: 1시간

### 2. 배치 처리
- 스팸 탐지 배치 API 사용
- 최대 100개까지 한번에 처리

### 3. 비동기 처리
- FastAPI의 async/await 활용
- 동시 요청 처리

---

## 🛠️ 개발

### 디렉토리 구조

```
ml-service/
├── app/
│   ├── main.py                    # FastAPI 앱
│   ├── models/
│   │   ├── recommendation.py      # 추천 모델
│   │   ├── spam_detection.py      # 스팸 탐지 모델
│   │   ├── tag_extraction.py      # 태그 추출 모델
│   │   └── trending_prediction.py # 트렌딩 예측 모델
│   ├── routers/
│   │   └── community.py           # API 라우터
│   ├── schemas/
│   │   └── requests.py            # Pydantic 스키마
│   └── utils/
│       └── preprocessing.py       # 전처리 유틸
├── data/                          # 학습 데이터
├── trained_models/                # 학습된 모델
├── logs/                          # 로그 파일
├── tests/                         # 테스트 코드
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
└── README.md
```

### 환경변수

```bash
# .env 파일 생성
ENV=development
LOG_LEVEL=INFO
ML_SERVICE_PORT=8000
REDIS_URL=redis://localhost:6379
```

---

## 🐳 Docker

### 이미지 빌드

```bash
docker build -t homesweet-ml-service .
```

### 컨테이너 실행

```bash
docker run -d -p 8000:8000 --name ml-service homesweet-ml-service
```

### Docker Compose

```bash
# 시작
docker-compose up -d

# 중지
docker-compose down

# 로그 확인
docker-compose logs -f ml-service

# 재시작
docker-compose restart ml-service
```

---

## 📝 라이선스

MIT License

---

## 👥 기여

ML 모델 개선 및 새로운 기능 제안은 이슈로 등록해주세요.

**만든 사람**: HomeSweetHome Team
**날짜**: 2025-01-17
**버전**: 1.0.0
