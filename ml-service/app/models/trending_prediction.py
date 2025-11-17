"""
트렌딩 게시글 예측 모델

시계열 분석과 통계 기반 인기 게시글 예측

알고리즘:
- Exponential Weighted Moving Average (EWMA)
- Viral Score Calculation
- Trending Score = f(views, likes, comments, recency)
"""

import numpy as np
from typing import List, Dict
from loguru import logger
from datetime import datetime, timedelta
import math


class TrendingPredictionModel:
    """트렌딩 예측 모델"""

    def __init__(self):
        # 가중치 설정
        self.weights = {
            'views': 1.0,
            'likes': 3.0,
            'comments': 2.0,
            'shares': 4.0,
            'recency': 5.0
        }

        # EWMA 파라미터
        self.alpha = 0.7  # smoothing factor

        self._is_ready = False

    async def initialize(self):
        """모델 초기화"""
        try:
            logger.info("Initializing trending prediction model...")
            self._is_ready = True
            logger.success("Trending prediction model initialized")
        except Exception as e:
            logger.error(f"Failed to initialize trending prediction model: {e}")
            raise

    def is_ready(self) -> bool:
        return self._is_ready

    def get_info(self) -> Dict:
        return {
            "model_type": "Trending Prediction (EWMA + Viral Score)",
            "weights": self.weights,
            "ready": self._is_ready
        }

    def predict_trending(
        self,
        posts: List[Dict],
        top_k: int = 10,
        time_window_hours: int = 24
    ) -> List[Dict]:
        """
        트렌딩 게시글 예측

        Args:
            posts: 게시글 리스트
                [{
                    'post_id': int,
                    'views': int,
                    'likes': int,
                    'comments': int,
                    'shares': int,
                    'created_at': datetime
                }]
            top_k: 상위 K개
            time_window_hours: 시간 윈도우 (시간)

        Returns:
            트렌딩 게시글 리스트
        """
        try:
            current_time = datetime.now()
            cutoff_time = current_time - timedelta(hours=time_window_hours)

            # 시간 윈도우 내 게시글 필터링
            recent_posts = [
                post for post in posts
                if post.get('created_at', current_time) >= cutoff_time
            ]

            # 트렌딩 스코어 계산
            scored_posts = []
            for post in recent_posts:
                score = self._calculate_trending_score(post, current_time)
                scored_posts.append({
                    **post,
                    'trending_score': score,
                    'viral_coefficient': self._calculate_viral_coefficient(post),
                    'growth_rate': self._estimate_growth_rate(post)
                })

            # 정렬
            sorted_posts = sorted(
                scored_posts,
                key=lambda x: x['trending_score'],
                reverse=True
            )[:top_k]

            return sorted_posts

        except Exception as e:
            logger.error(f"Trending prediction error: {e}")
            return []

    def _calculate_trending_score(
        self,
        post: Dict,
        current_time: datetime
    ) -> float:
        """트렌딩 스코어 계산"""
        views = post.get('views', 0)
        likes = post.get('likes', 0)
        comments = post.get('comments', 0)
        shares = post.get('shares', 0)
        created_at = post.get('created_at', current_time)

        # 시간 가중치 (최신일수록 높은 점수)
        time_diff = (current_time - created_at).total_seconds() / 3600  # hours
        recency_score = math.exp(-time_diff / 24)  # 24시간 기준 exponential decay

        # 상호작용 스코어
        interaction_score = (
            views * self.weights['views'] +
            likes * self.weights['likes'] +
            comments * self.weights['comments'] +
            shares * self.weights['shares']
        )

        # 최종 트렌딩 스코어
        trending_score = interaction_score * recency_score * self.weights['recency']

        return trending_score

    def _calculate_viral_coefficient(self, post: Dict) -> float:
        """바이럴 계수 계산"""
        views = max(post.get('views', 1), 1)
        shares = post.get('shares', 0)
        comments = post.get('comments', 0)

        # 바이럴 계수 = (공유 + 댓글) / 조회수
        viral_coefficient = (shares * 2 + comments) / views

        return min(viral_coefficient, 1.0)  # 최대 1.0

    def _estimate_growth_rate(self, post: Dict) -> float:
        """성장률 추정"""
        # 간단한 성장률 추정 (실제로는 시계열 데이터 필요)
        current_engagement = (
            post.get('likes', 0) +
            post.get('comments', 0) +
            post.get('shares', 0)
        )

        time_since_post = (datetime.now() - post.get('created_at', datetime.now())).total_seconds() / 3600

        if time_since_post > 0:
            growth_rate = current_engagement / time_since_post
        else:
            growth_rate = 0.0

        return growth_rate

    def detect_viral_potential(
        self,
        post: Dict,
        threshold: float = 0.1
    ) -> Dict:
        """바이럴 가능성 탐지"""
        viral_coef = self._calculate_viral_coefficient(post)
        growth_rate = self._estimate_growth_rate(post)

        is_viral = viral_coef >= threshold and growth_rate > 1.0

        return {
            "is_viral": is_viral,
            "viral_coefficient": viral_coef,
            "growth_rate": growth_rate,
            "confidence": min(viral_coef * growth_rate, 1.0)
        }

    async def cleanup(self):
        logger.info("Cleaning up trending prediction model...")
