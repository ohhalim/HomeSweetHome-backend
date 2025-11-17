"""
게시글 자동 태그 추출 모델

NLP 기반 키워드 추출 및 자동 태깅 시스템

알고리즘:
- TF-IDF
- TextRank
- KeyBERT (optional)
"""

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from typing import List, Dict, Tuple
from loguru import logger
import re
from collections import Counter


class TagExtractionModel:
    """태그 추출 모델"""

    def __init__(self):
        self.tfidf = TfidfVectorizer(
            max_features=100,
            ngram_range=(1, 2),
            stop_words='english'
        )

        # 한글 불용어
        self.korean_stopwords = {
            '이', '그', '저', '것', '수', '등', '및', '제', '약', '전', '후',
            '다', '을', '를', '이', '가', '은', '는', '의', '에', '으로'
        }

        self._is_ready = False

    async def initialize(self):
        """모델 초기화"""
        try:
            logger.info("Initializing tag extraction model...")
            self._is_ready = True
            logger.success("Tag extraction model initialized")
        except Exception as e:
            logger.error(f"Failed to initialize tag extraction model: {e}")
            raise

    def is_ready(self) -> bool:
        return self._is_ready

    def get_info(self) -> Dict:
        return {
            "model_type": "Tag Extraction (TF-IDF + TextRank)",
            "ready": self._is_ready
        }

    def extract_tags(
        self,
        title: str,
        content: str,
        max_tags: int = 5,
        min_score: float = 0.1
    ) -> List[Dict]:
        """태그 추출"""
        try:
            text = f"{title} {content}"

            # TF-IDF 기반 키워드 추출
            keywords = self._tfidf_keywords(text, max_tags * 2)

            # 빈도 기반 필터링
            freq_keywords = self._frequency_keywords(text, max_tags * 2)

            # 결합 및 점수 계산
            combined = self._combine_keywords(keywords, freq_keywords)

            # 필터링 및 정렬
            filtered = [
                {
                    "tag": tag,
                    "score": float(score),
                    "category": self._categorize_tag(tag)
                }
                for tag, score in combined[:max_tags]
                if score >= min_score
            ]

            return filtered

        except Exception as e:
            logger.error(f"Tag extraction error: {e}")
            return []

    def _tfidf_keywords(self, text: str, top_n: int) -> List[Tuple[str, float]]:
        """TF-IDF 기반 키워드"""
        try:
            tfidf_matrix = self.tfidf.fit_transform([text])
            feature_names = self.tfidf.get_feature_names_out()

            scores = tfidf_matrix.toarray()[0]
            keywords = [(feature_names[i], scores[i]) for i in scores.argsort()[-top_n:][::-1]]

            return keywords
        except:
            return []

    def _frequency_keywords(self, text: str, top_n: int) -> List[Tuple[str, float]]:
        """빈도 기반 키워드"""
        words = re.findall(r'[가-힣a-z]{2,}', text.lower())
        words = [w for w in words if w not in self.korean_stopwords]

        counter = Counter(words)
        total = sum(counter.values())

        return [(word, count/total) for word, count in counter.most_common(top_n)]

    def _combine_keywords(
        self,
        tfidf_kw: List[Tuple[str, float]],
        freq_kw: List[Tuple[str, float]]
    ) -> List[Tuple[str, float]]:
        """키워드 결합"""
        combined = {}

        for word, score in tfidf_kw:
            combined[word] = combined.get(word, 0) + score * 0.6

        for word, score in freq_kw:
            combined[word] = combined.get(word, 0) + score * 0.4

        return sorted(combined.items(), key=lambda x: x[1], reverse=True)

    def _categorize_tag(self, tag: str) -> str:
        """태그 카테고리 분류"""
        categories = {
            'interior': ['인테리어', '디자인', '꾸미기'],
            'furniture': ['가구', '소파', '테이블', '의자'],
            'living': ['거실', '주방', '침실', '화장실'],
            'diy': ['diy', '만들기', '제작'],
        }

        for category, keywords in categories.items():
            if any(kw in tag for kw in keywords):
                return category

        return 'general'

    async def cleanup(self):
        logger.info("Cleaning up tag extraction model...")
