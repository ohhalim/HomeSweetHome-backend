"""
HomeSweetHome Community ML Service

머신러닝 기반 커뮤니티 기능을 제공하는 FastAPI 마이크로서비스

기능:
- 게시글 추천 시스템 (협업 필터링 + 콘텐츠 기반)
- 스팸/악성 댓글 탐지
- 게시글 자동 태그 추출
- 트렌딩 게시글 예측
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import uvicorn
from loguru import logger
import sys

from app.routers import community
from app.models import (
    recommendation,
    spam_detection,
    tag_extraction,
    trending_prediction
)

# 로깅 설정
logger.remove()
logger.add(
    sys.stdout,
    colorize=True,
    format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan> | <level>{message}</level>"
)
logger.add(
    "logs/ml-service.log",
    rotation="500 MB",
    retention="10 days",
    level="INFO"
)

# ML 모델 전역 저장소
ml_models = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """애플리케이션 라이프사이클 관리"""
    logger.info("🚀 Starting ML Service...")

    try:
        # 1. 추천 시스템 초기화
        logger.info("Loading recommendation model...")
        ml_models['recommendation'] = recommendation.RecommendationModel()
        await ml_models['recommendation'].initialize()
        logger.success("✓ Recommendation model loaded")

        # 2. 스팸 탐지 모델 초기화
        logger.info("Loading spam detection model...")
        ml_models['spam_detection'] = spam_detection.SpamDetectionModel()
        await ml_models['spam_detection'].initialize()
        logger.success("✓ Spam detection model loaded")

        # 3. 태그 추출 모델 초기화
        logger.info("Loading tag extraction model...")
        ml_models['tag_extraction'] = tag_extraction.TagExtractionModel()
        await ml_models['tag_extraction'].initialize()
        logger.success("✓ Tag extraction model loaded")

        # 4. 트렌딩 예측 모델 초기화
        logger.info("Loading trending prediction model...")
        ml_models['trending_prediction'] = trending_prediction.TrendingPredictionModel()
        await ml_models['trending_prediction'].initialize()
        logger.success("✓ Trending prediction model loaded")

        logger.success("🎉 All ML models loaded successfully!")

    except Exception as e:
        logger.error(f"❌ Failed to initialize ML models: {e}")
        raise

    yield

    # 종료 시 리소스 정리
    logger.info("🔄 Shutting down ML Service...")
    for model_name, model in ml_models.items():
        try:
            if hasattr(model, 'cleanup'):
                await model.cleanup()
            logger.info(f"✓ {model_name} cleaned up")
        except Exception as e:
            logger.error(f"❌ Error cleaning up {model_name}: {e}")

    logger.info("👋 ML Service shutdown complete")


# FastAPI 애플리케이션 생성
app = FastAPI(
    title="HomeSweetHome Community ML Service",
    description="Machine Learning microservice for community features",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 특정 도메인만 허용
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 라우터 등록
app.include_router(
    community.router,
    prefix="/api/v1/ml/community",
    tags=["Community ML"]
)


# ========== 헬스체크 엔드포인트 ==========

@app.get("/", tags=["Health"])
async def root():
    """루트 엔드포인트"""
    return {
        "service": "HomeSweetHome ML Service",
        "status": "running",
        "version": "1.0.0",
        "endpoints": {
            "docs": "/docs",
            "health": "/health",
            "models": "/models/status"
        }
    }


@app.get("/health", tags=["Health"])
async def health_check():
    """헬스 체크"""
    return {
        "status": "healthy",
        "models_loaded": len(ml_models),
        "models": list(ml_models.keys())
    }


@app.get("/models/status", tags=["Health"])
async def models_status():
    """모델 상태 확인"""
    status = {}

    for model_name, model in ml_models.items():
        try:
            model_status = {
                "loaded": True,
                "ready": hasattr(model, 'is_ready') and model.is_ready(),
            }

            if hasattr(model, 'get_info'):
                model_status.update(model.get_info())

            status[model_name] = model_status

        except Exception as e:
            status[model_name] = {
                "loaded": False,
                "error": str(e)
            }

    return status


# ========== 에러 핸들러 ==========

@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc):
    """HTTP 예외 처리"""
    logger.error(f"HTTP {exc.status_code}: {exc.detail}")
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": exc.detail,
            "status_code": exc.status_code
        }
    )


@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    """일반 예외 처리"""
    logger.exception(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content={
            "error": "Internal server error",
            "detail": str(exc)
        }
    )


# ========== ML 모델 접근 헬퍼 ==========

def get_model(model_name: str):
    """모델 가져오기"""
    if model_name not in ml_models:
        raise HTTPException(
            status_code=503,
            detail=f"Model '{model_name}' not available"
        )
    return ml_models[model_name]


# 애플리케이션에 모델 접근 함수 추가
app.state.get_model = get_model
app.state.ml_models = ml_models


# ========== 메인 실행 ==========

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info"
    )
