# Phase 4: 서비스 개발 (Service Development)

**기간**: 3-4개월
**목표**: Charlie Parker AI를 웹 서비스로 배포하여 포트폴리오 완성
**시간 배분**: Backend 60% : Music AI 40%

---

## 📋 Phase 4 개요

이 단계는 학습된 AI 모델을 실제 사용 가능한 웹 서비스로 만드는 단계입니다.
백엔드 기술 (Spring Boot)과 ML 모델을 통합하여 풀스택 프로젝트를 완성합니다.

### 목표
1. Flask/FastAPI로 ML 모델 서빙 API 개발
2. Spring Boot와 ML 서버 통합
3. React 프론트엔드 개발
4. MIDI 플레이어 및 시각화
5. Docker Compose로 전체 스택 배포

---

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (React)                      │
│  - MIDI Player (Tone.js)                                │
│  - Piano Roll Visualizer                                │
│  - Generation UI                                         │
└────────────┬────────────────────────────────────────────┘
             │ HTTP/REST
             ▼
┌─────────────────────────────────────────────────────────┐
│              Backend (Spring Boot)                       │
│  - User Management (OAuth2)                             │
│  - Generation History                                   │
│  - Community Features (Reddit-style)                    │
│  - ML Server Proxy                                      │
└────────────┬────────────────────────────────────────────┘
             │ gRPC/HTTP
             ▼
┌─────────────────────────────────────────────────────────┐
│            ML Server (FastAPI)                           │
│  - Music Transformer Inference                          │
│  - MIDI Generation                                      │
│  - Style Evaluation                                     │
└────────────┬────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────┐
│              Database (PostgreSQL)                       │
│  - Users, Generations, Comments, Votes                  │
└─────────────────────────────────────────────────────────┘
```

---

## 🗓️ 주차별 학습 계획

### Week 1-2: ML 서버 개발 (FastAPI)

#### 학습 목표
- FastAPI 프레임워크 이해
- PyTorch 모델 서빙
- gRPC 통신 (선택)

#### 1. FastAPI 설치 및 기본 구조

```bash
# 프로젝트 구조
charlie-parker-ml-server/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI 앱
│   ├── models/
│   │   ├── __init__.py
│   │   ├── transformer.py   # Music Transformer
│   │   └── tokenizer.py     # MIDI Tokenizer
│   ├── api/
│   │   ├── __init__.py
│   │   └── generation.py    # Generation API
│   ├── services/
│   │   ├── __init__.py
│   │   ├── generator.py     # Generation Service
│   │   └── evaluator.py     # Evaluation Service
│   └── schemas/
│       ├── __init__.py
│       └── generation.py    # Pydantic schemas
├── checkpoints/
│   └── best_model.pt
├── requirements.txt
├── Dockerfile
└── docker-compose.yml
```

**requirements.txt**:
```txt
fastapi==0.104.1
uvicorn[standard]==0.24.0
torch==2.1.0
pretty-midi==0.2.10
pydantic==2.5.0
python-multipart==0.0.6
```

#### 2. FastAPI 서버 구현

**app/main.py**:
```python
from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
import uvicorn
from typing import Optional
import torch
import tempfile
from pathlib import Path

from .services.generator import MusicGenerator
from .services.evaluator import StyleEvaluator
from .schemas.generation import (
    GenerationRequest,
    GenerationResponse,
    EvaluationResponse
)

# FastAPI 앱 생성
app = FastAPI(
    title="Charlie Parker ML API",
    description="Music Transformer API for Charlie Parker style generation",
    version="1.0.0"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Production에서는 특정 도메인만 허용
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 전역 서비스 (앱 시작 시 한 번만 로드)
generator: Optional[MusicGenerator] = None
evaluator: Optional[StyleEvaluator] = None

@app.on_event("startup")
async def startup_event():
    """서버 시작 시 모델 로드"""
    global generator, evaluator

    print("Loading models...")

    device = "cuda" if torch.cuda.is_available() else "cpu"
    model_path = "checkpoints/best_model.pt"

    generator = MusicGenerator(model_path=model_path, device=device)
    evaluator = StyleEvaluator()

    print(f"✓ Models loaded on {device}")

@app.get("/")
async def root():
    """Health check"""
    return {
        "service": "Charlie Parker ML API",
        "status": "running",
        "device": "cuda" if torch.cuda.is_available() else "cpu"
    }

@app.post("/generate", response_model=GenerationResponse)
async def generate_improvisation(request: GenerationRequest):
    """
    Charlie Parker 스타일 즉흥 연주 생성

    Args:
        request: 생성 파라미터

    Returns:
        생성된 MIDI 파일 정보 및 토큰
    """
    try:
        # 임시 파일 생성
        with tempfile.NamedTemporaryFile(suffix=".mid", delete=False) as tmp:
            output_path = tmp.name

        # 생성
        result = generator.generate(
            output_file=output_path,
            max_length=request.max_length,
            temperature=request.temperature,
            top_k=request.top_k,
            top_p=request.top_p
        )

        # 평가
        scores = evaluator.evaluate(output_path)

        # MIDI 파일을 base64로 인코딩 (또는 S3 업로드)
        with open(output_path, 'rb') as f:
            import base64
            midi_base64 = base64.b64encode(f.read()).decode()

        # 임시 파일 삭제
        Path(output_path).unlink()

        return GenerationResponse(
            midi_base64=midi_base64,
            tokens=result['tokens'],
            length=result['length'],
            bebop_score=scores['total_score'],
            tempo_score=scores['tempo_score'],
            rhythm_score=scores['rhythm_score'],
            melodic_score=scores['melodic_score']
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/evaluate", response_model=EvaluationResponse)
async def evaluate_midi(file: UploadFile = File(...)):
    """
    업로드된 MIDI 파일의 Bebop 스타일 평가

    Args:
        file: MIDI 파일

    Returns:
        스타일 평가 점수
    """
    try:
        # 임시 파일로 저장
        with tempfile.NamedTemporaryFile(suffix=".mid", delete=False) as tmp:
            contents = await file.read()
            tmp.write(contents)
            tmp_path = tmp.name

        # 평가
        scores = evaluator.evaluate(tmp_path)

        # 임시 파일 삭제
        Path(tmp_path).unlink()

        return EvaluationResponse(**scores)

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
async def health():
    """헬스 체크"""
    return {
        "status": "healthy",
        "model_loaded": generator is not None
    }

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True
    )
```

**app/schemas/generation.py**:
```python
from pydantic import BaseModel, Field
from typing import List, Optional

class GenerationRequest(BaseModel):
    """생성 요청"""
    max_length: int = Field(default=1000, ge=100, le=5000)
    temperature: float = Field(default=1.0, ge=0.1, le=2.0)
    top_k: int = Field(default=40, ge=0, le=100)
    top_p: float = Field(default=0.9, ge=0.0, le=1.0)

    class Config:
        json_schema_extra = {
            "example": {
                "max_length": 1000,
                "temperature": 0.9,
                "top_k": 40,
                "top_p": 0.9
            }
        }

class GenerationResponse(BaseModel):
    """생성 응답"""
    midi_base64: str
    tokens: List[int]
    length: int
    bebop_score: float
    tempo_score: float
    rhythm_score: float
    melodic_score: float

class EvaluationResponse(BaseModel):
    """평가 응답"""
    total_score: float
    tempo_score: float
    rhythm_score: float
    melodic_score: float
    scale_score: float
    chromatic_score: float
```

**app/services/generator.py**:
```python
import torch
from typing import Dict
from ..models.transformer import MusicTransformer
from ..models.tokenizer import MIDITokenizer

class MusicGenerator:
    """음악 생성 서비스"""

    def __init__(self, model_path: str, device: str = "cuda"):
        self.device = device

        # 모델 로드
        checkpoint = torch.load(model_path, map_location=device)

        self.model = MusicTransformer(vocab_size=391)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        self.model.to(device)
        self.model.eval()

        self.tokenizer = MIDITokenizer()

    def generate(
        self,
        output_file: str,
        max_length: int = 1000,
        temperature: float = 1.0,
        top_k: int = 40,
        top_p: float = 0.9
    ) -> Dict:
        """즉흥 연주 생성"""

        # 시작 토큰
        start_tokens = torch.tensor(
            [[self.tokenizer.START_TOKEN]],
            device=self.device
        )

        # 생성
        with torch.no_grad():
            generated_tokens = self.model.generate(
                start_tokens,
                max_length=max_length,
                temperature=temperature,
                top_k=top_k,
                top_p=top_p
            )

        # MIDI로 변환
        tokens = generated_tokens[0].cpu().tolist()
        self.tokenizer.decode(tokens, output_file)

        return {
            'tokens': tokens,
            'length': len(tokens),
            'output_file': output_file
        }
```

#### 3. Docker 이미지 빌드

**Dockerfile**:
```dockerfile
FROM python:3.10-slim

WORKDIR /app

# PyTorch dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# App code
COPY app/ ./app/
COPY checkpoints/ ./checkpoints/

# Expose port
EXPOSE 8000

# Run
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**실행**:
```bash
# Docker 이미지 빌드
docker build -t charlie-parker-ml:latest .

# 컨테이너 실행
docker run -p 8000:8000 charlie-parker-ml:latest

# 테스트
curl http://localhost:8000/health
```

---

### Week 3-4: Spring Boot 통합

#### 학습 목표
- Spring Boot에서 외부 API 호출
- WebClient 사용
- DTO 매핑

#### 1. ML 클라이언트 구현

**MusicGenerationClient.java**:
```java
package com.homesweet.homesweetback.ml.client;

import com.homesweet.homesweetback.ml.dto.GenerationRequest;
import com.homesweet.homesweetback.ml.dto.GenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * ML 서버 클라이언트
 *
 * FastAPI ML 서버와 통신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MusicGenerationClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${ml.server.url:http://localhost:8000}")
    private String mlServerUrl;

    @Value("${ml.server.timeout:60}")
    private int timeoutSeconds;

    /**
     * 음악 생성 요청
     *
     * @param request 생성 파라미터
     * @return 생성 결과
     */
    public Mono<GenerationResponse> generate(GenerationRequest request) {
        log.info("Requesting music generation: temp={}, length={}",
                request.getTemperature(), request.getMaxLength());

        WebClient webClient = webClientBuilder
                .baseUrl(mlServerUrl)
                .build();

        return webClient.post()
                .uri("/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerationResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(response ->
                        log.info("Generation completed: score={}", response.getBebopScore())
                )
                .doOnError(error ->
                        log.error("Generation failed", error)
                );
    }

    /**
     * ML 서버 헬스 체크
     */
    public Mono<Boolean> healthCheck() {
        WebClient webClient = webClientBuilder
                .baseUrl(mlServerUrl)
                .build();

        return webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false)
                .timeout(Duration.ofSeconds(5));
    }
}
```

**GenerationRequest.java**:
```java
package com.homesweet.homesweetback.ml.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRequest {

    @Min(100)
    @Max(5000)
    @Builder.Default
    private Integer maxLength = 1000;

    @Min(1)
    @Max(20)
    @Builder.Default
    private Double temperature = 1.0;

    @Min(0)
    @Max(100)
    @Builder.Default
    private Integer topK = 40;

    @Min(0)
    @Max(10)
    @Builder.Default
    private Double topP = 0.9;
}
```

**GenerationResponse.java**:
```java
package com.homesweet.homesweetback.ml.dto;

import lombok.Data;

import java.util.List;

@Data
public class GenerationResponse {
    private String midiBase64;
    private List<Integer> tokens;
    private Integer length;
    private Double bebopScore;
    private Double tempoScore;
    private Double rhythmScore;
    private Double melodicScore;
}
```

#### 2. Service Layer

**MusicGenerationService.java**:
```java
package com.homesweet.homesweetback.ml.service;

import com.homesweet.homesweetback.domain.music.entity.GenerationEntity;
import com.homesweet.homesweetback.domain.music.repository.GenerationRepository;
import com.homesweet.homesweetback.ml.client.MusicGenerationClient;
import com.homesweet.homesweetback.ml.dto.GenerationRequest;
import com.homesweet.homesweetback.ml.dto.GenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 음악 생성 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicGenerationService {

    private final MusicGenerationClient mlClient;
    private final GenerationRepository generationRepository;
    private final S3StorageService s3StorageService; // MIDI 파일 저장

    /**
     * Charlie Parker 스타일 즉흥 연주 생성
     *
     * @param userId 사용자 ID
     * @param request 생성 파라미터
     * @return 생성 결과
     */
    @Transactional
    public Mono<GenerationEntity> generateImprovisation(Long userId, GenerationRequest request) {
        log.info("User {} requesting generation", userId);

        return mlClient.generate(request)
                .flatMap(response -> {
                    // MIDI 파일 저장
                    byte[] midiBytes = Base64.getDecoder().decode(response.getMidiBase64());

                    return s3StorageService.uploadMidi(midiBytes, userId)
                            .map(s3Url -> {
                                // DB에 저장
                                GenerationEntity entity = GenerationEntity.builder()
                                        .userId(userId)
                                        .s3Url(s3Url)
                                        .bebopScore(response.getBebopScore())
                                        .tempoScore(response.getTempoScore())
                                        .rhythmScore(response.getRhythmScore())
                                        .melodicScore(response.getMelodicScore())
                                        .temperature(request.getTemperature())
                                        .maxLength(request.getMaxLength())
                                        .tokenCount(response.getLength())
                                        .createdAt(LocalDateTime.now())
                                        .build();

                                return generationRepository.save(entity);
                            });
                });
    }

    /**
     * 사용자의 생성 히스토리 조회
     */
    public List<GenerationEntity> getUserGenerations(Long userId) {
        return generationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 베스트 생성 조회 (Bebop 점수 순)
     */
    public List<GenerationEntity> getBestGenerations(int limit) {
        return generationRepository.findTopByOrderByBebopScoreDesc(limit);
    }
}
```

#### 3. Controller

**MusicGenerationController.java**:
```java
package com.homesweet.homesweetback.ml.controller;

import com.homesweet.homesweetback.domain.music.entity.GenerationEntity;
import com.homesweet.homesweetback.ml.dto.GenerationRequest;
import com.homesweet.homesweetback.ml.service.MusicGenerationService;
import com.homesweet.homesweetback.security.OAuth2UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Music Generation", description = "Charlie Parker 스타일 음악 생성 API")
@RestController
@RequestMapping("/api/v1/music")
@RequiredArgsConstructor
public class MusicGenerationController {

    private final MusicGenerationService musicGenerationService;

    @PostMapping("/generate")
    @Operation(summary = "즉흥 연주 생성", description = "Charlie Parker 스타일의 즉흥 연주를 생성합니다")
    public Mono<ResponseEntity<GenerationEntity>> generate(
            @RequestBody @Valid GenerationRequest request,
            Authentication authentication
    ) {
        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        return musicGenerationService.generateImprovisation(userId, request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/my-generations")
    @Operation(summary = "내 생성 히스토리")
    public ResponseEntity<List<GenerationEntity>> getMyGenerations(
            Authentication authentication
    ) {
        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        List<GenerationEntity> generations = musicGenerationService.getUserGenerations(userId);

        return ResponseEntity.ok(generations);
    }

    @GetMapping("/best")
    @Operation(summary = "베스트 생성")
    public ResponseEntity<List<GenerationEntity>> getBestGenerations(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<GenerationEntity> generations = musicGenerationService.getBestGenerations(limit);

        return ResponseEntity.ok(generations);
    }
}
```

---

### Week 5-6: React 프론트엔드

#### 학습 목표
- React 기본
- MIDI 플레이어 (Tone.js)
- Piano Roll 시각화

#### 1. 프로젝트 설정

```bash
# React 앱 생성
npx create-react-app charlie-parker-frontend
cd charlie-parker-frontend

# 패키지 설치
npm install \
  axios \
  tone \
  @tonejs/midi \
  react-router-dom \
  @mui/material \
  @emotion/react \
  @emotion/styled
```

#### 2. MIDI 플레이어 컴포넌트

**components/MidiPlayer.jsx**:
```javascript
import React, { useState, useEffect, useRef } from 'react';
import * as Tone from 'tone';
import { Midi } from '@tonejs/midi';
import { Button, Slider, Box, Typography } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import StopIcon from '@mui/icons-material/Stop';

export default function MidiPlayer({ midiBase64 }) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const synthRef = useRef(null);
  const partRef = useRef(null);

  useEffect(() => {
    // Polyphonic Synth 초기화
    synthRef.current = new Tone.PolySynth(Tone.Synth).toDestination();

    return () => {
      // Cleanup
      if (synthRef.current) {
        synthRef.current.dispose();
      }
      if (partRef.current) {
        partRef.current.dispose();
      }
    };
  }, []);

  useEffect(() => {
    if (midiBase64) {
      loadMidi(midiBase64);
    }
  }, [midiBase64]);

  const loadMidi = async (base64) => {
    try {
      // Base64 → ArrayBuffer
      const binaryString = atob(base64);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      // Parse MIDI
      const midi = new Midi(bytes.buffer);
      setDuration(midi.duration);

      // MIDI → Tone.js Part
      const notes = [];

      midi.tracks.forEach(track => {
        track.notes.forEach(note => {
          notes.push({
            time: note.time,
            note: note.name,
            duration: note.duration,
            velocity: note.velocity
          });
        });
      });

      // Create Part
      partRef.current = new Tone.Part((time, note) => {
        synthRef.current.triggerAttackRelease(
          note.note,
          note.duration,
          time,
          note.velocity
        );
      }, notes);

      console.log('MIDI loaded:', notes.length, 'notes');

    } catch (error) {
      console.error('Failed to load MIDI:', error);
    }
  };

  const handlePlay = async () => {
    await Tone.start(); // 브라우저 오디오 권한

    if (Tone.Transport.state === 'started') {
      Tone.Transport.pause();
      setIsPlaying(false);
    } else {
      partRef.current.start(0);
      Tone.Transport.start();
      setIsPlaying(true);

      // 진행 상황 업데이트
      const interval = setInterval(() => {
        setCurrentTime(Tone.Transport.seconds);

        if (Tone.Transport.seconds >= duration) {
          clearInterval(interval);
          setIsPlaying(false);
        }
      }, 100);
    }
  };

  const handleStop = () => {
    Tone.Transport.stop();
    Tone.Transport.position = 0;
    setCurrentTime(0);
    setIsPlaying(false);
  };

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <Box sx={{ width: '100%', p: 2 }}>
      <Typography variant="h6">MIDI Player</Typography>

      <Box sx={{ display: 'flex', gap: 1, my: 2 }}>
        <Button
          variant="contained"
          startIcon={isPlaying ? <PauseIcon /> : <PlayArrowIcon />}
          onClick={handlePlay}
        >
          {isPlaying ? 'Pause' : 'Play'}
        </Button>

        <Button
          variant="outlined"
          startIcon={<StopIcon />}
          onClick={handleStop}
        >
          Stop
        </Button>
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Typography variant="body2">
          {formatTime(currentTime)}
        </Typography>

        <Slider
          value={currentTime}
          max={duration}
          onChange={(e, value) => {
            setCurrentTime(value);
            Tone.Transport.seconds = value;
          }}
          sx={{ flexGrow: 1 }}
        />

        <Typography variant="body2">
          {formatTime(duration)}
        </Typography>
      </Box>
    </Box>
  );
}
```

#### 3. 생성 UI

**pages/GeneratePage.jsx**:
```javascript
import React, { useState } from 'react';
import axios from 'axios';
import {
  Container,
  Paper,
  Typography,
  Slider,
  Button,
  Box,
  CircularProgress,
  Alert
} from '@mui/material';
import MidiPlayer from '../components/MidiPlayer';

export default function GeneratePage() {
  const [params, setParams] = useState({
    maxLength: 1000,
    temperature: 1.0,
    topK: 40,
    topP: 0.9
  });

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleGenerate = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await axios.post(
        'http://localhost:8080/api/v1/music/generate',
        params,
        {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          }
        }
      );

      setResult(response.data);

    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h3" gutterBottom>
        Charlie Parker AI
      </Typography>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Generation Parameters
        </Typography>

        <Box sx={{ my: 2 }}>
          <Typography gutterBottom>
            Length: {params.maxLength}
          </Typography>
          <Slider
            value={params.maxLength}
            min={100}
            max={5000}
            step={100}
            onChange={(e, value) => setParams({ ...params, maxLength: value })}
          />
        </Box>

        <Box sx={{ my: 2 }}>
          <Typography gutterBottom>
            Temperature: {params.temperature.toFixed(2)}
          </Typography>
          <Slider
            value={params.temperature}
            min={0.1}
            max={2.0}
            step={0.1}
            onChange={(e, value) => setParams({ ...params, temperature: value })}
          />
        </Box>

        <Box sx={{ my: 2 }}>
          <Typography gutterBottom>
            Top-K: {params.topK}
          </Typography>
          <Slider
            value={params.topK}
            min={0}
            max={100}
            step={5}
            onChange={(e, value) => setParams({ ...params, topK: value })}
          />
        </Box>

        <Box sx={{ my: 2 }}>
          <Typography gutterBottom>
            Top-P: {params.topP.toFixed(2)}
          </Typography>
          <Slider
            value={params.topP}
            min={0.0}
            max={1.0}
            step={0.05}
            onChange={(e, value) => setParams({ ...params, topP: value })}
          />
        </Box>

        <Button
          variant="contained"
          size="large"
          fullWidth
          onClick={handleGenerate}
          disabled={loading}
        >
          {loading ? <CircularProgress size={24} /> : 'Generate'}
        </Button>
      </Paper>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {result && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Generated Improvisation
          </Typography>

          <MidiPlayer midiBase64={result.midiBase64} />

          <Box sx={{ mt: 2 }}>
            <Typography variant="subtitle2">
              Bebop Style Score: {(result.bebopScore * 100).toFixed(1)}%
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Tempo: {(result.tempoScore * 100).toFixed(0)}% |
              Rhythm: {(result.rhythmScore * 100).toFixed(0)}% |
              Melody: {(result.melodicScore * 100).toFixed(0)}%
            </Typography>
          </Box>
        </Paper>
      )}
    </Container>
  );
}
```

---

### Week 7-8: Docker Compose 통합 배포

#### 학습 목표
- Docker Compose로 전체 스택 오케스트레이션
- Nginx 리버스 프록시
- 로컬 배포 및 테스트

#### docker-compose.yml

```yaml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15
    container_name: charlie-parker-db
    environment:
      POSTGRES_DB: charlie_parker
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - charlie-parker-network

  # Redis Cache
  redis:
    image: redis:7-alpine
    container_name: charlie-parker-redis
    ports:
      - "6379:6379"
    networks:
      - charlie-parker-network

  # ML Server (FastAPI)
  ml-server:
    build:
      context: ./charlie-parker-ml-server
      dockerfile: Dockerfile
    container_name: charlie-parker-ml
    ports:
      - "8000:8000"
    environment:
      - MODEL_PATH=/app/checkpoints/best_model.pt
    volumes:
      - ./checkpoints:/app/checkpoints
    networks:
      - charlie-parker-network
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]

  # Backend (Spring Boot)
  backend:
    build:
      context: ./HomeSweetHome-backend
      dockerfile: Dockerfile
    container_name: charlie-parker-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/charlie_parker
      - SPRING_DATASOURCE_USERNAME=admin
      - SPRING_DATASOURCE_PASSWORD=password
      - SPRING_REDIS_HOST=redis
      - ML_SERVER_URL=http://ml-server:8000
    depends_on:
      - postgres
      - redis
      - ml-server
    networks:
      - charlie-parker-network

  # Frontend (React)
  frontend:
    build:
      context: ./charlie-parker-frontend
      dockerfile: Dockerfile
    container_name: charlie-parker-frontend
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - charlie-parker-network

  # Nginx Reverse Proxy
  nginx:
    image: nginx:alpine
    container_name: charlie-parker-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - frontend
      - backend
      - ml-server
    networks:
      - charlie-parker-network

volumes:
  postgres_data:

networks:
  charlie-parker-network:
    driver: bridge
```

**실행**:
```bash
# 전체 스택 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 중지
docker-compose down

# 볼륨까지 삭제
docker-compose down -v
```

---

## 📊 Phase 4 평가 기준

### ML 서버 (25%)
- [ ] FastAPI 서버 구현
- [ ] 모델 로딩 및 inference
- [ ] Docker 이미지 빌드

### Backend 통합 (25%)
- [ ] WebClient로 ML 서버 호출
- [ ] 생성 히스토리 저장
- [ ] S3 파일 업로드

### Frontend (30%)
- [ ] MIDI 플레이어 구현
- [ ] 생성 UI
- [ ] 히스토리 페이지

### 배포 (20%)
- [ ] Docker Compose 구성
- [ ] 전체 스택 실행
- [ ] 엔드투엔드 테스트

---

## 🎯 Phase 4 완료 후 산출물

1. **charlie-parker-ml-server/**
   - FastAPI 앱
   - Dockerfile

2. **charlie-parker-frontend/**
   - React 앱
   - MIDI 플레이어

3. **docker-compose.yml**
   - 전체 스택 오케스트레이션

4. **포트폴리오 데모**
   - 로컬 실행 가능한 전체 시스템
   - README.md 문서

---

다음 단계: [Phase 5: 프로덕션 배포](phase5_production.md)
