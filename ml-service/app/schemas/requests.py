"""
API Request/Response Schemas
"""

from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


# ========== 추천 시스템 ==========

class RecommendationRequest(BaseModel):
    """추천 요청"""
    user_id: int = Field(..., description="사용자 ID")
    user_liked_posts: Optional[List[int]] = Field(
        default=[],
        description="사용자가 좋아한 게시글 ID 리스트"
    )
    k: int = Field(default=10, ge=1, le=50, description="추천할 게시글 수")
    use_hybrid: bool = Field(default=True, description="하이브리드 추천 사용 여부")


class RecommendationResponse(BaseModel):
    """추천 응답"""
    user_id: int
    recommendations: List[dict]
    total_count: int


# ========== 스팸 탐지 ==========

class SpamDetectionRequest(BaseModel):
    """스팸 탐지 요청"""
    text: str = Field(..., min_length=1, description="검사할 텍스트")
    threshold: float = Field(
        default=0.5,
        ge=0.0,
        le=1.0,
        description="스팸 판정 임계값"
    )


class SpamDetectionBatchRequest(BaseModel):
    """배치 스팸 탐지 요청"""
    texts: List[str] = Field(..., min_items=1, max_items=100)
    threshold: float = Field(default=0.5, ge=0.0, le=1.0)


class SpamDetectionResponse(BaseModel):
    """스팸 탐지 응답"""
    is_spam: bool
    confidence: float
    spam_score: float
    category: str
    reasons: List[str]


# ========== 태그 추출 ==========

class TagExtractionRequest(BaseModel):
    """태그 추출 요청"""
    title: str = Field(..., description="게시글 제목")
    content: str = Field(..., description="게시글 내용")
    max_tags: int = Field(default=5, ge=1, le=20, description="최대 태그 수")
    min_score: float = Field(
        default=0.1,
        ge=0.0,
        le=1.0,
        description="최소 태그 점수"
    )


class TagExtractionResponse(BaseModel):
    """태그 추출 응답"""
    tags: List[dict]
    total_count: int


# ========== 트렌딩 예측 ==========

class PostMetrics(BaseModel):
    """게시글 메트릭"""
    post_id: int
    views: int = Field(default=0, ge=0)
    likes: int = Field(default=0, ge=0)
    comments: int = Field(default=0, ge=0)
    shares: int = Field(default=0, ge=0)
    created_at: datetime


class TrendingPredictionRequest(BaseModel):
    """트렌딩 예측 요청"""
    posts: List[PostMetrics] = Field(..., min_items=1)
    top_k: int = Field(default=10, ge=1, le=100, description="상위 K개")
    time_window_hours: int = Field(
        default=24,
        ge=1,
        le=168,
        description="시간 윈도우 (시간)"
    )


class TrendingPredictionResponse(BaseModel):
    """트렌딩 예측 응답"""
    trending_posts: List[dict]
    total_count: int
    time_window_hours: int


class ViralDetectionRequest(BaseModel):
    """바이럴 탐지 요청"""
    post: PostMetrics
    threshold: float = Field(
        default=0.1,
        ge=0.0,
        le=1.0,
        description="바이럴 판정 임계값"
    )


class ViralDetectionResponse(BaseModel):
    """바이럴 탐지 응답"""
    post_id: int
    is_viral: bool
    viral_coefficient: float
    growth_rate: float
    confidence: float
