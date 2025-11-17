"""
게시글 추천 시스템

협업 필터링(Collaborative Filtering)과 콘텐츠 기반 필터링(Content-based Filtering)을
결합한 하이브리드 추천 시스템

알고리즘:
- Collaborative Filtering: User-based & Item-based CF
- Content-based: TF-IDF + Cosine Similarity
- Hybrid: Weighted combination
"""

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from scipy.sparse import csr_matrix
from typing import List, Dict, Tuple, Optional
from loguru import logger
import pickle
import os
from collections import defaultdict


class RecommendationModel:
    """게시글 추천 모델"""

    def __init__(self):
        self.user_item_matrix = None
        self.item_similarity_matrix = None
        self.content_similarity_matrix = None
        self.tfidf_vectorizer = TfidfVectorizer(
            max_features=1000,
            ngram_range=(1, 2),
            stop_words='english'
        )

        # 가중치 설정
        self.cf_weight = 0.6  # 협업 필터링 가중치
        self.content_weight = 0.4  # 콘텐츠 기반 가중치

        # 캐시
        self.user_history = defaultdict(set)
        self.post_metadata = {}

        self._is_ready = False

    async def initialize(self):
        """모델 초기화"""
        try:
            # 기존 모델 로드 시도
            model_path = "trained_models/recommendation_model.pkl"
            if os.path.exists(model_path):
                logger.info(f"Loading existing recommendation model from {model_path}")
                self.load_model(model_path)
            else:
                logger.info("No existing model found. Creating new model...")
                # 기본 모델 생성 (실제 데이터로 학습 필요)
                self._create_default_model()

            self._is_ready = True
            logger.success("Recommendation model initialized successfully")

        except Exception as e:
            logger.error(f"Failed to initialize recommendation model: {e}")
            raise

    def _create_default_model(self):
        """기본 모델 생성 (데모용)"""
        # 실제 환경에서는 데이터베이스에서 데이터를 가져와 학습
        logger.warning("Using default recommendation model. Train with real data for production.")

        # 더미 user-item 행렬
        self.user_item_matrix = csr_matrix((100, 100))
        self.item_similarity_matrix = np.eye(100)
        self.content_similarity_matrix = np.eye(100)

    def is_ready(self) -> bool:
        """모델 준비 상태"""
        return self._is_ready

    def get_info(self) -> Dict:
        """모델 정보"""
        return {
            "model_type": "Hybrid Recommendation (CF + Content-based)",
            "cf_weight": self.cf_weight,
            "content_weight": self.content_weight,
            "ready": self._is_ready
        }

    # ========== 협업 필터링 ==========

    def collaborative_filtering(
            self,
            user_id: int,
            k: int = 10
    ) -> List[Tuple[int, float]]:
        """
        협업 필터링 기반 추천

        Args:
            user_id: 사용자 ID
            k: 추천할 게시글 수

        Returns:
            List of (post_id, score) tuples
        """
        if self.user_item_matrix is None:
            return []

        try:
            # User-based CF
            user_similarities = self._compute_user_similarity(user_id)
            recommendations = self._get_cf_recommendations(
                user_id,
                user_similarities,
                k
            )

            return recommendations

        except Exception as e:
            logger.error(f"CF recommendation error: {e}")
            return []

    def _compute_user_similarity(self, user_id: int) -> np.ndarray:
        """사용자 간 유사도 계산"""
        if user_id >= self.user_item_matrix.shape[0]:
            return np.zeros(self.user_item_matrix.shape[0])

        user_vector = self.user_item_matrix[user_id].toarray().flatten()
        similarities = cosine_similarity(
            [user_vector],
            self.user_item_matrix.toarray()
        )[0]

        return similarities

    def _get_cf_recommendations(
            self,
            user_id: int,
            user_similarities: np.ndarray,
            k: int
    ) -> List[Tuple[int, float]]:
        """CF 기반 추천 결과 생성"""
        # 유사한 사용자들의 선호도 집계
        similar_users_idx = np.argsort(user_similarities)[::-1][1:11]  # Top 10 similar users

        recommendations = defaultdict(float)

        for similar_user_idx in similar_users_idx:
            similarity_score = user_similarities[similar_user_idx]

            # 유사 사용자가 좋아한 게시글
            liked_items = self.user_item_matrix[similar_user_idx].nonzero()[1]

            for item_id in liked_items:
                # 사용자가 이미 본 게시글은 제외
                if item_id not in self.user_history[user_id]:
                    recommendations[item_id] += similarity_score

        # 점수순으로 정렬
        sorted_recommendations = sorted(
            recommendations.items(),
            key=lambda x: x[1],
            reverse=True
        )[:k]

        return sorted_recommendations

    # ========== 콘텐츠 기반 필터링 ==========

    def content_based_filtering(
            self,
            user_id: int,
            user_liked_posts: List[int],
            k: int = 10
    ) -> List[Tuple[int, float]]:
        """
        콘텐츠 기반 추천

        Args:
            user_id: 사용자 ID
            user_liked_posts: 사용자가 좋아한 게시글 ID 리스트
            k: 추천할 게시글 수

        Returns:
            List of (post_id, score) tuples
        """
        if self.content_similarity_matrix is None:
            return []

        if not user_liked_posts:
            return []

        try:
            recommendations = defaultdict(float)

            # 사용자가 좋아한 게시글과 유사한 게시글 찾기
            for liked_post_id in user_liked_posts:
                if liked_post_id >= self.content_similarity_matrix.shape[0]:
                    continue

                similarities = self.content_similarity_matrix[liked_post_id]

                for post_id, similarity in enumerate(similarities):
                    # 이미 본 게시글 제외
                    if post_id not in self.user_history[user_id] and post_id not in user_liked_posts:
                        recommendations[post_id] += similarity

            # 점수순으로 정렬
            sorted_recommendations = sorted(
                recommendations.items(),
                key=lambda x: x[1],
                reverse=True
            )[:k]

            return sorted_recommendations

        except Exception as e:
            logger.error(f"Content-based recommendation error: {e}")
            return []

    # ========== 하이브리드 추천 ==========

    def recommend(
            self,
            user_id: int,
            user_liked_posts: List[int] = None,
            k: int = 10,
            use_hybrid: bool = True
    ) -> List[Dict]:
        """
        하이브리드 추천 (CF + Content-based)

        Args:
            user_id: 사용자 ID
            user_liked_posts: 사용자가 좋아한 게시글 ID 리스트
            k: 추천할 게시글 수
            use_hybrid: 하이브리드 추천 사용 여부

        Returns:
            List of recommended posts with scores
        """
        if user_liked_posts is None:
            user_liked_posts = []

        try:
            if not use_hybrid:
                # CF만 사용
                cf_recommendations = self.collaborative_filtering(user_id, k)
                return self._format_recommendations(cf_recommendations)

            # 1. 협업 필터링 추천
            cf_recommendations = self.collaborative_filtering(user_id, k * 2)

            # 2. 콘텐츠 기반 추천
            content_recommendations = self.content_based_filtering(
                user_id,
                user_liked_posts,
                k * 2
            )

            # 3. 하이브리드 결합 (가중 평균)
            hybrid_scores = defaultdict(float)

            # CF 점수 추가
            for post_id, score in cf_recommendations:
                hybrid_scores[post_id] += score * self.cf_weight

            # 콘텐츠 기반 점수 추가
            for post_id, score in content_recommendations:
                hybrid_scores[post_id] += score * self.content_weight

            # 최종 추천 리스트
            sorted_recommendations = sorted(
                hybrid_scores.items(),
                key=lambda x: x[1],
                reverse=True
            )[:k]

            return self._format_recommendations(sorted_recommendations)

        except Exception as e:
            logger.error(f"Hybrid recommendation error: {e}")
            return []

    def _format_recommendations(
            self,
            recommendations: List[Tuple[int, float]]
    ) -> List[Dict]:
        """추천 결과 포맷팅"""
        formatted = []

        for rank, (post_id, score) in enumerate(recommendations, 1):
            formatted.append({
                "post_id": int(post_id),
                "score": float(score),
                "rank": rank,
                "reason": self._get_recommendation_reason(post_id, score)
            })

        return formatted

    def _get_recommendation_reason(self, post_id: int, score: float) -> str:
        """추천 이유 생성"""
        if score > 0.8:
            return "Very popular among similar users"
        elif score > 0.6:
            return "Based on your interests"
        elif score > 0.4:
            return "Trending in your community"
        else:
            return "You might like this"

    # ========== 모델 학습 ==========

    def train(
            self,
            user_interactions: pd.DataFrame,
            post_contents: pd.DataFrame
    ):
        """
        모델 학습

        Args:
            user_interactions: 사용자 상호작용 데이터
                Columns: user_id, post_id, interaction_type, timestamp
            post_contents: 게시글 콘텐츠 데이터
                Columns: post_id, title, content, category
        """
        logger.info("Training recommendation model...")

        try:
            # 1. User-Item 행렬 생성
            self._build_user_item_matrix(user_interactions)

            # 2. 콘텐츠 유사도 행렬 생성
            self._build_content_similarity_matrix(post_contents)

            # 3. Item-Item 유사도 행렬 생성
            self._build_item_similarity_matrix()

            logger.success("Model training completed")

        except Exception as e:
            logger.error(f"Training error: {e}")
            raise

    def _build_user_item_matrix(self, interactions: pd.DataFrame):
        """User-Item 행렬 생성"""
        # 상호작용 타입별 가중치
        interaction_weights = {
            'view': 1.0,
            'like': 3.0,
            'comment': 2.0,
            'share': 4.0
        }

        # 가중치 적용
        interactions['weight'] = interactions['interaction_type'].map(
            lambda x: interaction_weights.get(x, 1.0)
        )

        # Pivot table 생성
        user_item_df = interactions.pivot_table(
            index='user_id',
            columns='post_id',
            values='weight',
            aggfunc='sum',
            fill_value=0
        )

        self.user_item_matrix = csr_matrix(user_item_df.values)

        logger.info(f"User-Item matrix shape: {self.user_item_matrix.shape}")

    def _build_content_similarity_matrix(self, posts: pd.DataFrame):
        """콘텐츠 유사도 행렬 생성"""
        # 제목과 내용 결합
        posts['combined_text'] = posts['title'] + ' ' + posts['content']

        # TF-IDF 벡터화
        tfidf_matrix = self.tfidf_vectorizer.fit_transform(
            posts['combined_text']
        )

        # 코사인 유사도 계산
        self.content_similarity_matrix = cosine_similarity(tfidf_matrix)

        logger.info(f"Content similarity matrix shape: {self.content_similarity_matrix.shape}")

    def _build_item_similarity_matrix(self):
        """Item-Item 유사도 행렬 생성"""
        # Item-based collaborative filtering
        self.item_similarity_matrix = cosine_similarity(
            self.user_item_matrix.T
        )

        logger.info(f"Item similarity matrix shape: {self.item_similarity_matrix.shape}")

    # ========== 모델 저장/로드 ==========

    def save_model(self, path: str = "trained_models/recommendation_model.pkl"):
        """모델 저장"""
        os.makedirs(os.path.dirname(path), exist_ok=True)

        model_data = {
            'user_item_matrix': self.user_item_matrix,
            'item_similarity_matrix': self.item_similarity_matrix,
            'content_similarity_matrix': self.content_similarity_matrix,
            'tfidf_vectorizer': self.tfidf_vectorizer,
            'cf_weight': self.cf_weight,
            'content_weight': self.content_weight
        }

        with open(path, 'wb') as f:
            pickle.dump(model_data, f)

        logger.info(f"Model saved to {path}")

    def load_model(self, path: str):
        """모델 로드"""
        with open(path, 'rb') as f:
            model_data = pickle.load(f)

        self.user_item_matrix = model_data['user_item_matrix']
        self.item_similarity_matrix = model_data['item_similarity_matrix']
        self.content_similarity_matrix = model_data['content_similarity_matrix']
        self.tfidf_vectorizer = model_data['tfidf_vectorizer']
        self.cf_weight = model_data['cf_weight']
        self.content_weight = model_data['content_weight']

        logger.info(f"Model loaded from {path}")

    # ========== 유틸리티 ==========

    def update_user_history(self, user_id: int, post_id: int):
        """사용자 히스토리 업데이트"""
        self.user_history[user_id].add(post_id)

    async def cleanup(self):
        """리소스 정리"""
        logger.info("Cleaning up recommendation model...")
        # 필요한 정리 작업
