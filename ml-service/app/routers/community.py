"""
Community ML API Router
"""

from fastapi import APIRouter, HTTPException, Request
from loguru import logger

from app.schemas.requests import (
    RecommendationRequest,
    RecommendationResponse,
    SpamDetectionRequest,
    SpamDetectionBatchRequest,
    SpamDetectionResponse,
    TagExtractionRequest,
    TagExtractionResponse,
    TrendingPredictionRequest,
    TrendingPredictionResponse,
    ViralDetectionRequest,
    ViralDetectionResponse
)

router = APIRouter()


# ========== 게시글 추천 ==========

@router.post("/recommend", response_model=RecommendationResponse, tags=["Recommendation"])
async def recommend_posts(request_data: RecommendationRequest, req: Request):
    """
    게시글 추천 API

    협업 필터링과 콘텐츠 기반 필터링을 결합한 하이브리드 추천
    """
    try:
        model = req.app.state.get_model('recommendation')

        recommendations = model.recommend(
            user_id=request_data.user_id,
            user_liked_posts=request_data.user_liked_posts,
            k=request_data.k,
            use_hybrid=request_data.use_hybrid
        )

        return RecommendationResponse(
            user_id=request_data.user_id,
            recommendations=recommendations,
            total_count=len(recommendations)
        )

    except Exception as e:
        logger.error(f"Recommendation error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ========== 스팸 탐지 ==========

@router.post("/spam/detect", response_model=SpamDetectionResponse, tags=["Spam Detection"])
async def detect_spam(request_data: SpamDetectionRequest, req: Request):
    """
    스팸/악성 댓글 탐지 API

    규칙 기반 + ML 기반 하이브리드 탐지
    """
    try:
        model = req.app.state.get_model('spam_detection')

        result = model.detect_spam(
            text=request_data.text,
            threshold=request_data.threshold
        )

        return SpamDetectionResponse(**result)

    except Exception as e:
        logger.error(f"Spam detection error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/spam/detect/batch", tags=["Spam Detection"])
async def detect_spam_batch(request_data: SpamDetectionBatchRequest, req: Request):
    """
    배치 스팸 탐지 API

    여러 텍스트를 한번에 검사
    """
    try:
        model = req.app.state.get_model('spam_detection')

        results = model.detect_spam_batch(
            texts=request_data.texts,
            threshold=request_data.threshold
        )

        return {
            "results": results,
            "total_count": len(results),
            "spam_count": sum(1 for r in results if r['is_spam'])
        }

    except Exception as e:
        logger.error(f"Batch spam detection error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ========== 태그 추출 ==========

@router.post("/tags/extract", response_model=TagExtractionResponse, tags=["Tag Extraction"])
async def extract_tags(request_data: TagExtractionRequest, req: Request):
    """
    게시글 자동 태그 추출 API

    TF-IDF 및 빈도 기반 키워드 추출
    """
    try:
        model = req.app.state.get_model('tag_extraction')

        tags = model.extract_tags(
            title=request_data.title,
            content=request_data.content,
            max_tags=request_data.max_tags,
            min_score=request_data.min_score
        )

        return TagExtractionResponse(
            tags=tags,
            total_count=len(tags)
        )

    except Exception as e:
        logger.error(f"Tag extraction error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ========== 트렌딩 예측 ==========

@router.post("/trending/predict", response_model=TrendingPredictionResponse, tags=["Trending"])
async def predict_trending(request_data: TrendingPredictionRequest, req: Request):
    """
    트렌딩 게시글 예측 API

    시간 가중치와 상호작용 점수 기반 트렌딩 스코어 계산
    """
    try:
        model = req.app.state.get_model('trending_prediction')

        posts_data = [post.dict() for post in request_data.posts]

        trending_posts = model.predict_trending(
            posts=posts_data,
            top_k=request_data.top_k,
            time_window_hours=request_data.time_window_hours
        )

        return TrendingPredictionResponse(
            trending_posts=trending_posts,
            total_count=len(trending_posts),
            time_window_hours=request_data.time_window_hours
        )

    except Exception as e:
        logger.error(f"Trending prediction error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/trending/viral/detect", response_model=ViralDetectionResponse, tags=["Trending"])
async def detect_viral(request_data: ViralDetectionRequest, req: Request):
    """
    바이럴 게시글 탐지 API

    바이럴 계수와 성장률 기반 바이럴 가능성 판정
    """
    try:
        model = req.app.state.get_model('trending_prediction')

        post_data = request_data.post.dict()

        result = model.detect_viral_potential(
            post=post_data,
            threshold=request_data.threshold
        )

        return ViralDetectionResponse(
            post_id=post_data['post_id'],
            **result
        )

    except Exception as e:
        logger.error(f"Viral detection error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ========== 헬스체크 ==========

@router.get("/health", tags=["Health"])
async def health_check():
    """ML 서비스 헬스체크"""
    return {
        "status": "healthy",
        "service": "Community ML API"
    }
