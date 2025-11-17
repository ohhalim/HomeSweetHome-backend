"""
스팸/악성 댓글 탐지 모델

텍스트 분류 기반 스팸 및 악성 댓글 탐지 시스템

알고리즘:
- Text Preprocessing: 한글/영어 전처리
- Feature Extraction: TF-IDF
- Classification: Logistic Regression, Random Forest, SVM
- Ensemble: Voting Classifier
"""

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, VotingClassifier
from sklearn.svm import SVC
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score
from typing import Dict, List, Tuple
from loguru import logger
import pickle
import os
import re


class SpamDetectionModel:
    """스팸/악성 댓글 탐지 모델"""

    def __init__(self):
        self.vectorizer = TfidfVectorizer(
            max_features=5000,
            ngram_range=(1, 3),
            min_df=2,
            max_df=0.9
        )

        # 앙상블 분류기
        self.classifier = None

        # 스팸 키워드 (한글/영어)
        self.spam_keywords = {
            'promotion': ['광고', '홍보', '클릭', 'click', '무료', 'free', '이벤트', 'event'],
            'abusive': ['욕설', '비방', '혐오', 'hate', '바보', '멍청'],
            'scam': ['사기', '피싱', 'phishing', '돈', 'money', '송금'],
        }

        # 악성 패턴
        self.malicious_patterns = [
            r'https?://[^\s]+',  # URL
            r'\d{3}-\d{4}-\d{4}',  # 전화번호
            r'\w+@\w+\.\w+',  # 이메일
        ]

        self._is_ready = False
        self.model_info = {
            "accuracy": 0.0,
            "precision": 0.0,
            "recall": 0.0,
            "f1_score": 0.0
        }

    async def initialize(self):
        """모델 초기화"""
        try:
            model_path = "trained_models/spam_detection_model.pkl"
            if os.path.exists(model_path):
                logger.info(f"Loading existing spam detection model from {model_path}")
                self.load_model(model_path)
            else:
                logger.info("No existing model found. Creating default model...")
                self._create_default_model()

            self._is_ready = True
            logger.success("Spam detection model initialized successfully")

        except Exception as e:
            logger.error(f"Failed to initialize spam detection model: {e}")
            raise

    def _create_default_model(self):
        """기본 모델 생성"""
        logger.warning("Using default spam detection model. Train with real data for production.")

        # 기본 분류기 생성
        lr = LogisticRegression(max_iter=1000, random_state=42)
        rf = RandomForestClassifier(n_estimators=100, random_state=42)
        svm = SVC(kernel='linear', probability=True, random_state=42)

        self.classifier = VotingClassifier(
            estimators=[
                ('lr', lr),
                ('rf', rf),
                ('svm', svm)
            ],
            voting='soft'
        )

        # 더미 데이터로 초기화 (실제로는 학습 필요)
        dummy_texts = [
            "안녕하세요",
            "광고입니다 클릭하세요",
            "좋은 정보 감사합니다",
            "욕설 비방 혐오"
        ]
        dummy_labels = [0, 1, 0, 1]  # 0: 정상, 1: 스팸

        X = self.vectorizer.fit_transform(dummy_texts)
        self.classifier.fit(X, dummy_labels)

        self.model_info['accuracy'] = 0.85  # 데모값

    def is_ready(self) -> bool:
        """모델 준비 상태"""
        return self._is_ready

    def get_info(self) -> Dict:
        """모델 정보"""
        return {
            "model_type": "Spam Detection (Ensemble)",
            "classifiers": ["LogisticRegression", "RandomForest", "SVM"],
            "performance": self.model_info,
            "ready": self._is_ready
        }

    # ========== 전처리 ==========

    def preprocess_text(self, text: str) -> str:
        """텍스트 전처리"""
        if not text:
            return ""

        # 소문자 변환
        text = text.lower()

        # 특수문자 제거 (한글, 영어, 숫자, 공백만 유지)
        text = re.sub(r'[^가-힣a-z0-9\s]', ' ', text)

        # 연속된 공백 제거
        text = re.sub(r'\s+', ' ', text).strip()

        return text

    # ========== 스팸 탐지 ==========

    def detect_spam(self, text: str, threshold: float = 0.5) -> Dict:
        """
        스팸 탐지

        Args:
            text: 검사할 텍스트
            threshold: 스팸 판정 임계값 (0.0 ~ 1.0)

        Returns:
            {
                "is_spam": bool,
                "confidence": float,
                "spam_score": float,
                "category": str,
                "reasons": List[str]
            }
        """
        if not text or not text.strip():
            return {
                "is_spam": False,
                "confidence": 0.0,
                "spam_score": 0.0,
                "category": "normal",
                "reasons": []
            }

        try:
            # 1. 전처리
            processed_text = self.preprocess_text(text)

            # 2. 규칙 기반 탐지
            rule_based_result = self._rule_based_detection(text)

            # 3. ML 기반 탐지
            ml_based_result = self._ml_based_detection(processed_text)

            # 4. 결과 결합
            final_score = (rule_based_result['score'] * 0.4 +
                           ml_based_result['score'] * 0.6)

            is_spam = final_score >= threshold

            # 5. 카테고리 판정
            category = self._determine_category(
                rule_based_result,
                ml_based_result,
                final_score
            )

            # 6. 탐지 이유
            reasons = rule_based_result['reasons'] + ml_based_result['reasons']

            return {
                "is_spam": bool(is_spam),
                "confidence": float(final_score),
                "spam_score": float(final_score),
                "category": category,
                "reasons": reasons[:5]  # 최대 5개
            }

        except Exception as e:
            logger.error(f"Spam detection error: {e}")
            return {
                "is_spam": False,
                "confidence": 0.0,
                "spam_score": 0.0,
                "category": "error",
                "reasons": [f"Detection error: {str(e)}"]
            }

    def _rule_based_detection(self, text: str) -> Dict:
        """규칙 기반 스팸 탐지"""
        score = 0.0
        reasons = []

        # 1. 스팸 키워드 검사
        for category, keywords in self.spam_keywords.items():
            matched_keywords = [kw for kw in keywords if kw in text.lower()]
            if matched_keywords:
                score += 0.2 * len(matched_keywords)
                reasons.append(f"Contains {category} keywords: {', '.join(matched_keywords[:3])}")

        # 2. 악성 패턴 검사
        for pattern in self.malicious_patterns:
            if re.search(pattern, text):
                score += 0.3
                reasons.append(f"Contains suspicious pattern")

        # 3. 연속된 특수문자
        if re.search(r'[!@#$%^&*]{3,}', text):
            score += 0.2
            reasons.append("Excessive special characters")

        # 4. 대문자 과다 사용
        if len(re.findall(r'[A-Z]', text)) / max(len(text), 1) > 0.5:
            score += 0.15
            reasons.append("Excessive capitalization")

        # 5. 반복 문자
        if re.search(r'(.)\1{4,}', text):
            score += 0.15
            reasons.append("Repetitive characters")

        return {
            "score": min(score, 1.0),
            "reasons": reasons
        }

    def _ml_based_detection(self, text: str) -> Dict:
        """ML 기반 스팸 탐지"""
        if not self.classifier:
            return {"score": 0.0, "reasons": []}

        try:
            # TF-IDF 벡터화
            X = self.vectorizer.transform([text])

            # 예측
            proba = self.classifier.predict_proba(X)[0]
            spam_probability = proba[1] if len(proba) > 1 else 0.0

            reasons = []
            if spam_probability > 0.7:
                reasons.append("High spam probability by ML model")
            elif spam_probability > 0.5:
                reasons.append("Moderate spam probability by ML model")

            return {
                "score": float(spam_probability),
                "reasons": reasons
            }

        except Exception as e:
            logger.error(f"ML detection error: {e}")
            return {"score": 0.0, "reasons": []}

    def _determine_category(
            self,
            rule_result: Dict,
            ml_result: Dict,
            final_score: float
    ) -> str:
        """스팸 카테고리 판정"""
        if final_score < 0.3:
            return "normal"
        elif final_score < 0.5:
            return "suspicious"
        elif final_score < 0.7:
            return "likely_spam"
        else:
            return "spam"

    # ========== 배치 탐지 ==========

    def detect_spam_batch(
            self,
            texts: List[str],
            threshold: float = 0.5
    ) -> List[Dict]:
        """배치 스팸 탐지"""
        results = []

        for text in texts:
            result = self.detect_spam(text, threshold)
            results.append(result)

        return results

    # ========== 모델 학습 ==========

    def train(
            self,
            texts: List[str],
            labels: List[int],
            test_size: float = 0.2
    ) -> Dict:
        """
        모델 학습

        Args:
            texts: 텍스트 리스트
            labels: 라벨 리스트 (0: 정상, 1: 스팸)
            test_size: 테스트 데이터 비율

        Returns:
            학습 결과 (accuracy, precision, recall, f1)
        """
        logger.info("Training spam detection model...")

        try:
            # 1. 전처리
            processed_texts = [self.preprocess_text(text) for text in texts]

            # 2. 데이터 분할
            X_train, X_test, y_train, y_test = train_test_split(
                processed_texts,
                labels,
                test_size=test_size,
                random_state=42,
                stratify=labels
            )

            # 3. TF-IDF 벡터화
            X_train_tfidf = self.vectorizer.fit_transform(X_train)
            X_test_tfidf = self.vectorizer.transform(X_test)

            # 4. 분류기 학습
            lr = LogisticRegression(max_iter=1000, random_state=42)
            rf = RandomForestClassifier(n_estimators=100, random_state=42)
            svm = SVC(kernel='linear', probability=True, random_state=42)

            self.classifier = VotingClassifier(
                estimators=[
                    ('lr', lr),
                    ('rf', rf),
                    ('svm', svm)
                ],
                voting='soft'
            )

            self.classifier.fit(X_train_tfidf, y_train)

            # 5. 평가
            y_pred = self.classifier.predict(X_test_tfidf)
            accuracy = accuracy_score(y_test, y_pred)

            report = classification_report(y_test, y_pred, output_dict=True)

            self.model_info = {
                "accuracy": accuracy,
                "precision": report['1']['precision'],
                "recall": report['1']['recall'],
                "f1_score": report['1']['f1-score']
            }

            logger.success(f"Training completed - Accuracy: {accuracy:.4f}")

            return self.model_info

        except Exception as e:
            logger.error(f"Training error: {e}")
            raise

    # ========== 모델 저장/로드 ==========

    def save_model(self, path: str = "trained_models/spam_detection_model.pkl"):
        """모델 저장"""
        os.makedirs(os.path.dirname(path), exist_ok=True)

        model_data = {
            'vectorizer': self.vectorizer,
            'classifier': self.classifier,
            'model_info': self.model_info,
            'spam_keywords': self.spam_keywords,
            'malicious_patterns': self.malicious_patterns
        }

        with open(path, 'wb') as f:
            pickle.dump(model_data, f)

        logger.info(f"Model saved to {path}")

    def load_model(self, path: str):
        """모델 로드"""
        with open(path, 'rb') as f:
            model_data = pickle.load(f)

        self.vectorizer = model_data['vectorizer']
        self.classifier = model_data['classifier']
        self.model_info = model_data['model_info']
        self.spam_keywords = model_data.get('spam_keywords', self.spam_keywords)
        self.malicious_patterns = model_data.get('malicious_patterns', self.malicious_patterns)

        logger.info(f"Model loaded from {path}")

    async def cleanup(self):
        """리소스 정리"""
        logger.info("Cleaning up spam detection model...")
