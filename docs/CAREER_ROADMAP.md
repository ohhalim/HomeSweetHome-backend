# 음악 AI + 백엔드 개발자 커리어 로드맵

**목표**: Charlie Parker 스타일 재즈 즉흥 연주 AI 개발 + 백엔드 개발자 취업

**기간**: 12개월 (단계별 조정 가능)

**현재 상황**:
- ✅ AI 부트캠프 수료 (머신러닝, 딥러닝 기초)
- ✅ 백엔드 2개월 학습 (Spring Boot, JPA, Redis 등)
- ❌ GPU 리소스 제한적
- ❌ 고졸 학력

**전략**: 투트랙 (백엔드 취업 + 음악 AI 개발)

---

## 📋 전체 로드맵 요약

| Phase | 기간 | 목표 | 결과물 |
|-------|------|------|--------|
| **Phase 1** | 1-2개월 | 기초 다지기 | 백엔드 포트폴리오 1개, 음악 데이터 이해 |
| **Phase 2** | 1-2개월 | 데이터 준비 | Charlie Parker 데이터셋, 전처리 파이프라인 |
| **Phase 3** | 2-3개월 | 프로토타입 | 작동하는 음악 생성 모델 |
| **Phase 4** | 3-4개월 | 서비스화 | 웹 데모 (백엔드 + ML) |
| **Phase 5** | 지속적 | 고도화 & 취업 | 프로덕션 서비스, 취업 |

---

## 🎯 Phase 1: 기초 다지기 (1-2개월)

### 목표
- 백엔드 포트폴리오 완성
- 음악 이론 & MIDI 기초
- Python 음악 라이브러리 숙달

### 학습 내용

#### 1.1 백엔드 (주 3-4일, 4시간)

**현재 프로젝트 완성**:
- ✅ Reddit 기능 완성 (이미 진행 중)
- ✅ 테스트 코드 작성
- ✅ API 문서화 완료
- ✅ 배포 (AWS/GCP 무료 티어)

**추가 학습**:
```
- Docker/Docker Compose
- CI/CD (GitHub Actions)
- 모니터링 (Prometheus, Grafana)
- 코딩테스트 (백준, 프로그래머스)
```

**결과물**:
- GitHub 프로필 정리
- 이력서 작성
- 포트폴리오 사이트

#### 1.2 음악 이론 (주 2-3일, 2시간)

**학습 내용**:
```
1. MIDI 이해
   - Note, Velocity, Duration
   - MIDI 메시지 구조
   - CC (Control Change)

2. 재즈 이론 기초
   - 코드 진행 (ii-V-I)
   - 스케일 (Blues, Bebop)
   - 리듬 (Swing)

3. Charlie Parker 스타일 분석
   - Bebop 특징
   - 대표곡 분석 (Ornithology, Confirmation)
```

**실습**:
```python
# pretty_midi로 MIDI 파일 읽기
import pretty_midi

midi_data = pretty_midi.PrettyMIDI('charlie_parker.mid')
for instrument in midi_data.instruments:
    for note in instrument.notes:
        print(f"Note: {note.pitch}, Start: {note.start}, End: {note.end}")
```

#### 1.3 Python 음악 라이브러리 (주 2일, 2시간)

**라이브러리**:
```bash
pip install pretty_midi music21 mido librosa
```

**실습 프로젝트**:
1. MIDI 파일 읽고 시각화
2. MIDI → Piano Roll 변환
3. 간단한 멜로디 생성 (규칙 기반)

**참고 자료**:
- [Music21 Tutorial](https://web.mit.edu/music21/)
- [Magenta MIDI Processing](https://magenta.tensorflow.org/)

---

## 🎵 Phase 2: 데이터 준비 (1-2개월)

### 목표
- Charlie Parker MIDI 데이터 수집
- 데이터 전처리 파이프라인 구축
- 데이터셋 큐레이션

### 2.1 데이터 수집 (2주)

**소스**:
```
1. The Jazzomat Research Project
   - https://jazzomat.hfm-weimar.de/

2. Weimar Jazz Database
   - 재즈 즉흥 연주 MIDI 데이터

3. MIDI 변환
   - YouTube → Audio → MIDI (Basic Pitch)
   - 정확도 낮지만 보조 데이터로 활용
```

**수집 스크립트**:
```python
# scripts/collect_data.py
import os
import requests
from pathlib import Path

class CharlieParkerDataCollector:
    def __init__(self, output_dir='data/raw'):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def download_midi(self, url, filename):
        """MIDI 파일 다운로드"""
        response = requests.get(url)
        filepath = self.output_dir / filename
        filepath.write_bytes(response.content)
        print(f"Downloaded: {filename}")

    def collect_from_source(self, source_name):
        """소스별 수집 로직"""
        if source_name == 'jazzomat':
            # Jazzomat API 호출
            pass
        elif source_name == 'weimar':
            # Weimar DB 접근
            pass

# 사용법
collector = CharlieParkerDataCollector()
collector.collect_from_source('jazzomat')
```

### 2.2 데이터 전처리 (3주)

**전처리 파이프라인**:
```python
# scripts/preprocess_midi.py
import pretty_midi
import numpy as np
from pathlib import Path

class MIDIPreprocessor:
    def __init__(self, input_dir='data/raw', output_dir='data/processed'):
        self.input_dir = Path(input_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def midi_to_piano_roll(self, midi_file, fs=100):
        """MIDI → Piano Roll 변환"""
        midi_data = pretty_midi.PrettyMIDI(str(midi_file))
        piano_roll = midi_data.get_piano_roll(fs=fs)
        return piano_roll

    def extract_melody(self, midi_file):
        """멜로디 라인 추출 (가장 높은 음)"""
        midi_data = pretty_midi.PrettyMIDI(str(midi_file))
        melody_notes = []

        for instrument in midi_data.instruments:
            if not instrument.is_drum:
                for note in instrument.notes:
                    melody_notes.append({
                        'pitch': note.pitch,
                        'start': note.start,
                        'end': note.end,
                        'velocity': note.velocity
                    })

        return melody_notes

    def normalize_timing(self, notes):
        """타이밍 정규화 (4/4 박자 기준)"""
        # 첫 음을 0으로 맞춤
        start_time = notes[0]['start']
        for note in notes:
            note['start'] -= start_time
            note['end'] -= start_time
        return notes

    def process_all(self):
        """모든 MIDI 파일 처리"""
        midi_files = list(self.input_dir.glob('*.mid'))

        for midi_file in midi_files:
            try:
                melody = self.extract_melody(midi_file)
                melody = self.normalize_timing(melody)

                # NPZ 형식으로 저장
                output_file = self.output_dir / f"{midi_file.stem}.npz"
                np.savez(output_file, melody=melody)

                print(f"Processed: {midi_file.name}")
            except Exception as e:
                print(f"Error processing {midi_file.name}: {e}")

# 사용법
preprocessor = MIDIPreprocessor()
preprocessor.process_all()
```

### 2.3 데이터 증강 (1주)

**증강 기법**:
```python
# scripts/augment_data.py
class DataAugmentor:
    def transpose(self, notes, semitones):
        """음높이 변환 (-3 ~ +3 반음)"""
        augmented = notes.copy()
        for note in augmented:
            note['pitch'] += semitones
        return augmented

    def time_stretch(self, notes, factor):
        """시간 늘림/줄임 (0.9 ~ 1.1배)"""
        augmented = notes.copy()
        for note in augmented:
            note['start'] *= factor
            note['end'] *= factor
        return augmented

    def velocity_variation(self, notes, factor):
        """강약 변화 (0.8 ~ 1.2배)"""
        augmented = notes.copy()
        for note in augmented:
            note['velocity'] = int(note['velocity'] * factor)
            note['velocity'] = np.clip(note['velocity'], 1, 127)
        return augmented

# 사용법
augmentor = DataAugmentor()

# 원본 데이터에 대해 3가지 증강 적용
for semitones in [-2, -1, 1, 2]:
    augmented = augmentor.transpose(original_notes, semitones)
    # 저장...
```

### 2.4 데이터셋 분할

```python
# scripts/split_dataset.py
from sklearn.model_selection import train_test_split

class DatasetSplitter:
    def split(self, data_files, train_ratio=0.8, val_ratio=0.1):
        """Train/Val/Test 분할"""
        train_files, temp_files = train_test_split(
            data_files, train_size=train_ratio, random_state=42
        )
        val_files, test_files = train_test_split(
            temp_files,
            train_size=val_ratio/(1-train_ratio),
            random_state=42
        )

        return {
            'train': train_files,
            'val': val_files,
            'test': test_files
        }

# 80% train, 10% val, 10% test
splitter = DatasetSplitter()
splits = splitter.split(all_data_files)
```

---

## 🤖 Phase 3: 프로토타입 (2-3개월)

### 목표
- Google Colab으로 첫 모델 학습
- Music Transformer 파인튜닝
- 작동하는 데모

### 3.1 환경 설정 (1주)

**Google Colab 설정**:
```python
# colab_setup.ipynb
# GPU 할당 확인
!nvidia-smi

# 라이브러리 설치
!pip install transformers datasets pretty_midi music21

# Google Drive 마운트 (데이터 저장용)
from google.colab import drive
drive.mount('/content/drive')
```

### 3.2 Music Transformer 파인튜닝 (4주)

**모델 선택**:
```
Option 1: Magenta의 Music Transformer
- 구글에서 개발한 음악 생성 모델
- MIDI 직접 처리 가능

Option 2: Hugging Face의 사전학습 모델
- musicgen-small (Meta)
- music-transformer (커뮤니티)
```

**파인튜닝 코드**:
```python
# notebooks/finetune_music_transformer.ipynb
from transformers import AutoModelForCausalLM, AutoTokenizer, Trainer, TrainingArguments
import torch

# 1. 데이터 로드
class CharlieParkerDataset(torch.utils.data.Dataset):
    def __init__(self, data_dir):
        self.data_files = list(Path(data_dir).glob('*.npz'))

    def __len__(self):
        return len(self.data_files)

    def __getitem__(self, idx):
        data = np.load(self.data_files[idx])
        melody = data['melody']

        # MIDI notes를 토큰으로 변환
        tokens = self.notes_to_tokens(melody)
        return {'input_ids': torch.tensor(tokens)}

    def notes_to_tokens(self, notes):
        """Note → Token 변환"""
        tokens = []
        for note in notes:
            # Pitch (0-127) + Time + Velocity를 토큰화
            pitch_token = note['pitch']
            time_token = int(note['start'] * 100)  # 0.01초 단위
            velocity_token = note['velocity']
            tokens.extend([pitch_token, time_token, velocity_token])
        return tokens

# 2. 모델 로드
model_name = "sander-wood/music-transformer"
model = AutoModelForCausalLM.from_pretrained(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)

# 3. 데이터셋 준비
train_dataset = CharlieParkerDataset('data/processed/train')
val_dataset = CharlieParkerDataset('data/processed/val')

# 4. 학습 설정
training_args = TrainingArguments(
    output_dir='./results',
    num_train_epochs=10,
    per_device_train_batch_size=4,
    per_device_eval_batch_size=4,
    warmup_steps=500,
    weight_decay=0.01,
    logging_dir='./logs',
    logging_steps=10,
    evaluation_strategy="steps",
    eval_steps=100,
    save_steps=500,
    save_total_limit=3,
    fp16=True,  # GPU 메모리 절약
)

# 5. Trainer
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=train_dataset,
    eval_dataset=val_dataset,
)

# 6. 학습 시작
trainer.train()

# 7. 모델 저장
model.save_pretrained('./charlie_parker_model')
```

### 3.3 음악 생성 (2주)

**생성 코드**:
```python
# scripts/generate_music.py
import torch
from transformers import AutoModelForCausalLM
import pretty_midi

class CharlieParkerGenerator:
    def __init__(self, model_path='./charlie_parker_model'):
        self.model = AutoModelForCausalLM.from_pretrained(model_path)
        self.model.eval()

    def generate(self, prompt_notes=None, max_length=512, temperature=0.9):
        """음악 생성"""
        with torch.no_grad():
            if prompt_notes is None:
                # 랜덤 시작
                input_ids = torch.randint(0, 128, (1, 10))
            else:
                input_ids = self.notes_to_tensor(prompt_notes)

            # 생성
            output = self.model.generate(
                input_ids,
                max_length=max_length,
                temperature=temperature,
                do_sample=True,
                top_k=50,
                top_p=0.95,
            )

            # 토큰 → MIDI 변환
            generated_notes = self.tokens_to_notes(output[0])
            return generated_notes

    def notes_to_midi(self, notes, output_file='output.mid'):
        """Notes → MIDI 파일"""
        midi = pretty_midi.PrettyMIDI()
        instrument = pretty_midi.Instrument(program=0)  # Acoustic Grand Piano

        for note_data in notes:
            note = pretty_midi.Note(
                velocity=note_data['velocity'],
                pitch=note_data['pitch'],
                start=note_data['start'],
                end=note_data['end']
            )
            instrument.notes.append(note)

        midi.instruments.append(instrument)
        midi.write(output_file)
        print(f"MIDI saved: {output_file}")

# 사용법
generator = CharlieParkerGenerator()

# Prompt: C major scale
prompt = [
    {'pitch': 60, 'start': 0.0, 'end': 0.5, 'velocity': 80},  # C
    {'pitch': 62, 'start': 0.5, 'end': 1.0, 'velocity': 80},  # D
    {'pitch': 64, 'start': 1.0, 'end': 1.5, 'velocity': 80},  # E
]

generated = generator.generate(prompt_notes=prompt)
generator.notes_to_midi(generated, 'charlie_parker_ai.mid')
```

### 3.4 평가 (1주)

**평가 지표**:
```python
# scripts/evaluate.py
class MusicEvaluator:
    def pitch_class_histogram(self, notes):
        """음높이 분포 (Charlie Parker 스타일과 비교)"""
        histogram = [0] * 12
        for note in notes:
            pitch_class = note['pitch'] % 12
            histogram[pitch_class] += 1
        return histogram

    def rhythm_complexity(self, notes):
        """리듬 복잡도"""
        intervals = []
        for i in range(len(notes) - 1):
            interval = notes[i+1]['start'] - notes[i]['start']
            intervals.append(interval)

        # 표준편차가 높을수록 복잡
        return np.std(intervals)

    def note_density(self, notes, duration):
        """음표 밀도 (초당 음표 수)"""
        return len(notes) / duration

    def evaluate_all(self, generated_notes, reference_notes):
        """종합 평가"""
        metrics = {
            'pitch_similarity': self.compare_histograms(
                self.pitch_class_histogram(generated_notes),
                self.pitch_class_histogram(reference_notes)
            ),
            'rhythm_complexity': self.rhythm_complexity(generated_notes),
            'note_density': self.note_density(generated_notes, 60.0),
        }
        return metrics
```

---

## 🌐 Phase 4: 서비스화 (3-4개월)

### 목표
- 백엔드 + ML 모델 통합
- RESTful API 구축
- 웹 데모 완성

### 4.1 아키텍처 설계 (1주)

```
┌─────────────────┐
│   Frontend      │  React/Vue (간단한 UI)
│  - MIDI Player  │
│  - 생성 버튼    │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────┐
│  Spring Boot    │  백엔드 API
│  - REST API     │
│  - 인증/인가    │
│  - 파일 관리    │
└────────┬────────┘
         │ gRPC / REST
         ▼
┌─────────────────┐
│  Flask/FastAPI  │  ML 서빙
│  - 모델 로드    │
│  - 추론 API     │
└────────┬────────┘
         │
         ▼
    [ML Model]
```

### 4.2 ML 서빙 API (Flask) (2주)

```python
# ml_server/app.py
from flask import Flask, request, jsonify, send_file
from charlie_parker_generator import CharlieParkerGenerator
import tempfile

app = Flask(__name__)

# 모델 로드 (서버 시작 시 1회)
generator = CharlieParkerGenerator(model_path='./models/charlie_parker_v1')

@app.route('/health', methods=['GET'])
def health():
    """헬스체크"""
    return jsonify({'status': 'ok'})

@app.route('/generate', methods=['POST'])
def generate_music():
    """
    음악 생성 API

    Request:
    {
        "prompt": [
            {"pitch": 60, "start": 0.0, "end": 0.5, "velocity": 80}
        ],
        "max_length": 512,
        "temperature": 0.9
    }

    Response:
    {
        "midi_file_url": "http://localhost:5000/download/abc123.mid"
    }
    """
    data = request.json

    prompt = data.get('prompt', None)
    max_length = data.get('max_length', 512)
    temperature = data.get('temperature', 0.9)

    # 생성
    generated_notes = generator.generate(
        prompt_notes=prompt,
        max_length=max_length,
        temperature=temperature
    )

    # 임시 MIDI 파일 생성
    temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.mid')
    generator.notes_to_midi(generated_notes, temp_file.name)

    # 파일 ID 반환
    file_id = temp_file.name.split('/')[-1]

    return jsonify({
        'file_id': file_id,
        'download_url': f'/download/{file_id}'
    })

@app.route('/download/<file_id>', methods=['GET'])
def download_midi(file_id):
    """MIDI 파일 다운로드"""
    file_path = f'/tmp/{file_id}'
    return send_file(file_path, mimetype='audio/midi', as_attachment=True)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
```

### 4.3 백엔드 API (Spring Boot) (3주)

```java
// src/main/java/com/music/ai/controller/MusicGenerationController.java
@RestController
@RequestMapping("/api/v1/music")
@RequiredArgsConstructor
public class MusicGenerationController {

    private final MusicGenerationService musicService;

    /**
     * 음악 생성 요청
     */
    @PostMapping("/generate")
    @Operation(summary = "Charlie Parker AI 음악 생성")
    public ResponseEntity<MusicGenerationResponse> generateMusic(
            @RequestBody @Valid MusicGenerationRequest request,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        // ML 서버로 요청
        MusicGenerationResponse response = musicService.generateMusic(request, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 생성 히스토리 조회
     */
    @GetMapping("/history")
    public ResponseEntity<Page<GeneratedMusic>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        Page<GeneratedMusic> history = musicService.getUserHistory(userId, PageRequest.of(page, size));

        return ResponseEntity.ok(history);
    }

    /**
     * MIDI 파일 다운로드
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadMidi(@PathVariable String fileId) {
        Resource file = musicService.downloadMidiFile(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                .body(file);
    }
}
```

**Service 계층**:
```java
// src/main/java/com/music/ai/service/MusicGenerationService.java
@Service
@RequiredArgsConstructor
public class MusicGenerationService {

    private final RestTemplate restTemplate;
    private final GeneratedMusicRepository musicRepository;

    @Value("${ml.server.url}")
    private String mlServerUrl;

    @Async("musicGenerationExecutor")
    @Transactional
    public MusicGenerationResponse generateMusic(MusicGenerationRequest request, Long userId) {
        // 1. ML 서버로 요청
        String mlEndpoint = mlServerUrl + "/generate";

        ResponseEntity<MLGenerationResponse> mlResponse = restTemplate.postForEntity(
                mlEndpoint,
                request,
                MLGenerationResponse.class
        );

        // 2. 생성 기록 저장
        GeneratedMusic music = GeneratedMusic.builder()
                .userId(userId)
                .fileId(mlResponse.getBody().getFileId())
                .prompt(request.getPrompt())
                .temperature(request.getTemperature())
                .build();

        musicRepository.save(music);

        // 3. 이벤트 발행
        eventPublisher.publishEvent(new MusicGeneratedEvent(this, music.getId(), userId));

        return MusicGenerationResponse.from(music);
    }
}
```

### 4.4 프론트엔드 (React) (2주)

```jsx
// frontend/src/components/MusicGenerator.jsx
import React, { useState } from 'react';
import axios from 'axios';
import MidiPlayer from 'react-midi-player';

const MusicGenerator = () => {
    const [isGenerating, setIsGenerating] = useState(false);
    const [midiUrl, setMidiUrl] = useState(null);
    const [temperature, setTemperature] = useState(0.9);

    const generateMusic = async () => {
        setIsGenerating(true);

        try {
            const response = await axios.post('/api/v1/music/generate', {
                prompt: null,  // 랜덤 시작
                max_length: 512,
                temperature: temperature
            });

            setMidiUrl(response.data.downloadUrl);
        } catch (error) {
            console.error('Generation failed:', error);
        } finally {
            setIsGenerating(false);
        }
    };

    return (
        <div className="music-generator">
            <h1>Charlie Parker AI</h1>

            <div className="controls">
                <label>
                    Temperature (창의성):
                    <input
                        type="range"
                        min="0.5"
                        max="1.5"
                        step="0.1"
                        value={temperature}
                        onChange={(e) => setTemperature(e.target.value)}
                    />
                    {temperature}
                </label>

                <button
                    onClick={generateMusic}
                    disabled={isGenerating}
                >
                    {isGenerating ? '생성 중...' : 'Charlie Parker 연주 생성'}
                </button>
            </div>

            {midiUrl && (
                <div className="player">
                    <h3>생성된 연주:</h3>
                    <MidiPlayer src={midiUrl} />
                    <a href={midiUrl} download>MIDI 다운로드</a>
                </div>
            )}
        </div>
    );
};

export default MusicGenerator;
```

### 4.5 배포 (Docker) (1주)

**docker-compose.yml**:
```yaml
version: '3.8'

services:
  # 백엔드 API
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - ML_SERVER_URL=http://ml-server:5000
    depends_on:
      - postgres
      - redis

  # ML 서버
  ml-server:
    build: ./ml_server
    ports:
      - "5000:5000"
    volumes:
      - ./models:/app/models
    environment:
      - MODEL_PATH=/app/models/charlie_parker_v1

  # 프론트엔드
  frontend:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - backend

  # PostgreSQL
  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=music_ai
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=password
    volumes:
      - postgres_data:/var/lib/postgresql/data

  # Redis
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

---

## 🚀 Phase 5: 고도화 & 취업 (지속적)

### 5.1 클라우드 GPU 활용 (월 10-20만원)

**옵션**:
```
1. Google Colab Pro+ (₩13,000/월)
   - V100 GPU 사용 가능
   - 백그라운드 실행

2. AWS EC2 g4dn.xlarge (~₩20만원/월)
   - NVIDIA T4 GPU
   - 온디맨드 or Spot Instance

3. Lambda Labs (~$10만원/월)
   - A100 GPU 저렴하게 사용
```

### 5.2 대규모 파인튜닝

**개선 방향**:
```
1. 더 큰 모델
   - GPT-2 → GPT-3 크기
   - Transformer layers 증가

2. 더 많은 데이터
   - 다른 재즈 뮤지션 데이터 추가
   - 데이터 증강 강화

3. 앙상블
   - 여러 모델 조합
   - 스타일 믹싱
```

### 5.3 백엔드 취업 준비

**이력서**:
```markdown
# 이력서

## 프로젝트

### 1. Charlie Parker AI (개인 프로젝트)
- **기술**: Python, PyTorch, Transformers, Spring Boot, React
- **설명**: 재즈 거장 Charlie Parker 스타일의 즉흥 연주 생성 AI
- **성과**:
  - Music Transformer 파인튜닝
  - 웹 서비스 구축 (백엔드 + ML)
  - 1,000+ MIDI 데이터 전처리 파이프라인
- **링크**: https://charlie-parker-ai.com
- **GitHub**: https://github.com/yourusername/charlie-parker-ai

### 2. Reddit-style 커뮤니티 (팀 프로젝트)
- **기술**: Spring Boot, JPA, Redis, PostgreSQL, Docker
- **설명**: Reddit 스타일의 커뮤니티 플랫폼
- **성과**:
  - Upvote/Downvote 시스템 구현
  - 분산 락을 활용한 동시성 제어
  - HOT/TOP/CONTROVERSIAL 정렬 알고리즘
  - 21개 REST API 구현
- **GitHub**: https://github.com/yourusername/community

## 기술 스택
- **Backend**: Java, Spring Boot, JPA, Redis
- **ML/AI**: Python, PyTorch, Transformers, Scikit-learn
- **DevOps**: Docker, AWS, GitHub Actions
```

**포트폴리오 사이트**:
```
https://yourname.dev
├─ About (소개)
├─ Projects
│   ├─ Charlie Parker AI (데모 + 코드)
│   └─ Reddit Community
├─ Blog (기술 블로그)
└─ Contact
```

### 5.4 취업 전략

**타겟 회사**:
```
Tier 1: AI/ML 스타트업
- 뮤직 AI 스타트업 (우선순위 1)
- MLOps 포지션

Tier 2: 일반 백엔드
- 스타트업 백엔드 개발자
- 퇴근 후 음악 AI 개발 지속

Tier 3: 프리랜서
- 백엔드 + ML 프로젝트
- 자유도 높음
```

**면접 준비**:
```
1. 코딩테스트
   - 백준 골드 3 이상
   - 프로그래머스 Lv.2-3

2. 기술 면접
   - Spring Boot 깊이 있게
   - ML 기초 (면접관이 물어보면)
   - Charlie Parker AI 프로젝트 설명 준비

3. 포트폴리오 발표
   - 데모 시연
   - 기술적 도전과 해결 과정
```

---

## 📅 타임라인

### 개월별 계획

**1-2개월** (Phase 1):
```
Week 1-2: 백엔드 프로젝트 완성
Week 3-4: 음악 이론 학습
Week 5-6: Python 음악 라이브러리
Week 7-8: 데이터 수집 시작
```

**3-4개월** (Phase 2):
```
Week 9-10: 데이터 수집 완료
Week 11-12: 전처리 파이프라인
Week 13-14: 데이터 증강
Week 15-16: 데이터셋 검증
```

**5-7개월** (Phase 3):
```
Week 17-18: Colab 환경 세팅
Week 19-22: Music Transformer 파인튜닝
Week 23-24: 첫 음악 생성 성공
Week 25-26: 평가 및 개선
Week 27-28: 프로토타입 완성
```

**8-11개월** (Phase 4):
```
Week 29-30: 아키텍처 설계
Week 31-32: ML 서빙 API
Week 33-35: 백엔드 API
Week 36-37: 프론트엔드
Week 38-39: 통합 테스트
Week 40-42: 배포
Week 43-44: 데모 사이트 공개
```

**12개월+** (Phase 5):
```
Week 45-48: 클라우드 GPU 활용
Week 49-52: 대규모 파인튜닝
이후: 취업 활동 + 서비스 운영
```

---

## 💰 예산 계획

| 항목 | 비용 | 주기 |
|------|------|------|
| Google Colab Pro+ | ₩13,000 | 월 |
| AWS/GCP 크레딧 | ₩0 (무료 티어) | - |
| 도메인 | ₩10,000 | 연 |
| SSL 인증서 | ₩0 (Let's Encrypt) | - |
| 책/강의 | ₩50,000 | 1회 |
| **총계 (월)** | **₩13,000** | - |

---

## 🎓 학습 리소스

### 음악 AI
```
1. Magenta 공식 문서
   https://magenta.tensorflow.org/

2. "Deep Learning for Music" (arXiv 논문들)
   https://arxiv.org/abs/1606.04930

3. Music Transformer 논문
   https://arxiv.org/abs/1809.04281

4. YouTube: "Music Generation with Deep Learning" 강의
```

### 백엔드
```
1. 인프런: "스프링 부트와 JPA 실무 완전 정복"
2. "토비의 스프링 3.1"
3. Redis 공식 문서
4. Docker 공식 튜토리얼
```

---

## ✅ 체크리스트

### Phase 1
- [ ] Reddit 프로젝트 완성
- [ ] GitHub 프로필 정리
- [ ] MIDI 기초 이해
- [ ] pretty_midi 라이브러리 숙달

### Phase 2
- [ ] Charlie Parker MIDI 100개 수집
- [ ] 전처리 파이프라인 구축
- [ ] 데이터 증강 3배
- [ ] Train/Val/Test 분할

### Phase 3
- [ ] Colab 환경 세팅
- [ ] Music Transformer 파인튜닝
- [ ] 첫 음악 생성 성공
- [ ] 평가 지표 구현

### Phase 4
- [ ] ML 서빙 API (Flask)
- [ ] 백엔드 API (Spring Boot)
- [ ] 프론트엔드 UI
- [ ] Docker 배포

### Phase 5
- [ ] 클라우드 GPU 구매
- [ ] 대규모 파인튜닝
- [ ] 이력서 작성
- [ ] 포트폴리오 사이트

---

## 🎯 성공 지표

**3개월 후**:
- ✅ 백엔드 포트폴리오 1개 완성
- ✅ Charlie Parker 데이터 200개 수집
- ✅ 전처리 파이프라인 완성

**6개월 후**:
- ✅ 첫 음악 생성 모델 완성
- ✅ 데모 사이트 배포
- ✅ 코딩테스트 골드 3

**12개월 후**:
- ✅ Charlie Parker AI 서비스 런칭
- ✅ 백엔드 취업 or AI 스타트업 입사
- ✅ 기술 블로그 10개 작성

---

## 🚨 주의사항

### 함정 피하기

1. **완벽주의 피하기**
   - 작은 것부터 빠르게 만들고 개선
   - 프로토타입 → 개선 → 완성

2. **GPU 없다고 포기하지 말기**
   - Colab 무료 GPU도 충분
   - 작은 모델로 시작

3. **투트랙 균형 유지**
   - 백엔드 50% : 음악 AI 50%
   - 둘 다 포트폴리오가 됨

4. **고졸 콤플렉스 버리기**
   - 결과물로 증명
   - Charlie Parker AI가 학력보다 강력

---

## 💪 동기 부여

당신의 강점:
- ✅ **명확한 목표**: Charlie Parker AI
- ✅ **실행력**: 이미 백엔드 2개월 학습
- ✅ **투트랙 전략**: 현실적
- ✅ **타이밍**: 한국 AI 투자 확대

성공 사례:
- OpenAI Jukebox: 개인 프로젝트로 시작
- Suno AI: 작은 팀이 음악 AI 성공
- MuseNet: 연구 프로젝트 → 제품화

**당신도 할 수 있습니다!** 🎺🤖

---

## 📞 지원

- **GitHub Discussions**: 커뮤니티 질문
- **Discord**: AI 개발자 커뮤니티
- **Reddit r/MachineLearning**: 기술 질문

**당신의 Charlie Parker AI, 함께 만들어갑시다!** 🎵✨
