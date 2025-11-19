# Phase 1: 기초 다지기 (1-2개월)

**목표**: 백엔드 포트폴리오 완성 + 음악 이론 & MIDI 기초

---

## 📚 학습 커리큘럼

### Week 1-2: 백엔드 프로젝트 완성

#### ✅ 현재 프로젝트 (Reddit Community) 완성하기

**체크리스트**:
- [ ] 모든 API 테스트 작성
- [ ] Swagger 문서 완성
- [ ] Docker 배포
- [ ] README 작성

**테스트 코드 예제**:
```java
// src/test/java/com/homesweet/community/integration/SubredditIntegrationTest.java
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubredditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("서브레딧 생성 → 구독 → 게시글 작성 → 투표 전체 플로우")
    void fullFlow_CreateSubreddit_Subscribe_Post_Vote() throws Exception {
        // 1. 서브레딧 생성
        SubredditCreateRequest createRequest = SubredditCreateRequest.builder()
                .name("javaspring")
                .title("자바 스프링")
                .description("자바 스프링 커뮤니티")
                .build();

        String subredditJson = mockMvc.perform(post("/api/v1/subreddits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("javaspring"))
                .andReturn().getResponse().getContentAsString();

        SubredditResponse subreddit = objectMapper.readValue(subredditJson, SubredditResponse.class);

        // 2. 구독
        mockMvc.perform(post("/api/v1/subreddits/" + subreddit.getSubredditId() + "/subscribe"))
                .andExpect(status().isOk());

        // 3. 게시글 작성
        // ...

        // 4. 투표
        // ...
    }
}
```

**Docker 배포**:
```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=community
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=password

  redis:
    image: redis:7-alpine
```

**README 템플릿**:
```markdown
# Reddit-Style Community Platform

Reddit 스타일의 커뮤니티 플랫폼

## 주요 기능

- ✅ Subreddit (주제별 커뮤니티)
- ✅ Upvote/Downvote 투표 시스템
- ✅ Hot/Top/Controversial 정렬
- ✅ Karma (사용자 신뢰도)
- ✅ Moderator 시스템

## 기술 스택

- **Backend**: Java 21, Spring Boot 3.5
- **Database**: PostgreSQL, Redis
- **Caching**: Caffeine (L1), Redis (L2)
- **API Docs**: Swagger/OpenAPI

## 아키텍처

[다이어그램]

## API 문서

Swagger UI: http://localhost:8080/swagger-ui/index.html

## 로컬 실행

```bash
docker-compose up -d
./gradlew bootRun
```

## 성능

- HOT 정렬: 50ms 이하
- 투표 처리: 100ms 이하 (분산 락)
- 캐시 히트율: 95%+
```

---

### Week 3-4: 음악 이론 기초

#### 📖 학습 내용

**1. MIDI 기초**

MIDI (Musical Instrument Digital Interface):
- 음악 정보를 디지털로 전송하는 표준
- 실제 음성이 아닌 "연주 정보" 전송

**MIDI 메시지 구조**:
```
Note On:  [144, pitch, velocity]
Note Off: [128, pitch, velocity]

예: C4 (Middle C) 연주
[144, 60, 100]  # Note On, C4, 강하게
[128, 60, 0]    # Note Off
```

**MIDI 번호 → 음이름 변환**:
```
MIDI 60 = C4 (Middle C)
MIDI 61 = C#4
MIDI 62 = D4
...

공식: 음이름 = (MIDI % 12)
0=C, 1=C#, 2=D, 3=D#, 4=E, 5=F, 6=F#, 7=G, 8=G#, 9=A, 10=A#, 11=B
```

**실습 코드**:
```python
# learn_midi.py
import pretty_midi
import matplotlib.pyplot as plt

def explore_midi(midi_file):
    """MIDI 파일 분석"""
    midi_data = pretty_midi.PrettyMIDI(midi_file)

    print(f"총 악기 수: {len(midi_data.instruments)}")
    print(f"총 길이: {midi_data.get_end_time():.2f}초")

    for i, instrument in enumerate(midi_data.instruments):
        print(f"\n악기 {i+1}:")
        print(f"  프로그램 번호: {instrument.program}")
        print(f"  총 음표 수: {len(instrument.notes)}")

        # 첫 5개 음표 출력
        for note in instrument.notes[:5]:
            pitch_name = pretty_midi.note_number_to_name(note.pitch)
            print(f"  {pitch_name}: {note.start:.2f}s ~ {note.end:.2f}s, vel={note.velocity}")

def visualize_piano_roll(midi_file):
    """Piano Roll 시각화"""
    midi_data = pretty_midi.PrettyMIDI(midi_file)
    piano_roll = midi_data.get_piano_roll(fs=100)

    plt.figure(figsize=(12, 4))
    plt.imshow(piano_roll, aspect='auto', origin='lower', cmap='hot')
    plt.xlabel('Time (0.01s)')
    plt.ylabel('MIDI Note Number')
    plt.colorbar(label='Velocity')
    plt.title('Piano Roll')
    plt.show()

# 사용법
explore_midi('charlie_parker_ornithology.mid')
visualize_piano_roll('charlie_parker_ornithology.mid')
```

**2. 재즈 이론 기초**

**코드 진행 (Chord Progression)**:

가장 기본: **ii-V-I 진행**
```
C major에서:
ii (Dm7) → V (G7) → I (Cmaj7)

재즈 스탠다드의 90%가 이 진행 포함
```

**스케일**:
```
Blues Scale (C):
C - Eb - F - F# - G - Bb - C
[0, 3, 5, 6, 7, 10, 12] (반음 단위)

Bebop Scale (C):
C - D - E - F - G - A - Bb - B - C
[0, 2, 4, 5, 7, 9, 10, 11, 12]
```

**실습**:
```python
# jazz_theory.py
class JazzTheory:
    SCALE_PATTERNS = {
        'blues': [0, 3, 5, 6, 7, 10, 12],
        'bebop_major': [0, 2, 4, 5, 7, 9, 10, 11, 12],
        'bebop_dominant': [0, 2, 4, 5, 7, 9, 10, 11, 12],
    }

    def generate_scale(self, root_note, scale_type):
        """특정 루트음에서 스케일 생성"""
        pattern = self.SCALE_PATTERNS[scale_type]
        scale = [root_note + interval for interval in pattern]
        return scale

    def is_chord_tone(self, note, chord_root, chord_type='major7'):
        """코드 구성음인지 확인"""
        chord_intervals = {
            'major7': [0, 4, 7, 11],  # 1, 3, 5, 7
            'dominant7': [0, 4, 7, 10],
            'minor7': [0, 3, 7, 10],
        }

        note_in_octave = note % 12
        chord_root_in_octave = chord_root % 12

        interval = (note_in_octave - chord_root_in_octave) % 12
        return interval in chord_intervals.get(chord_type, [])

# 사용법
theory = JazzTheory()

# C Blues Scale
c_blues = theory.generate_scale(60, 'blues')  # [60, 63, 65, 66, 67, 70, 72]

# D는 Cmaj7의 코드음인가?
is_chord = theory.is_chord_tone(62, 60, 'major7')  # False (D=2, 코드음 아님)
```

**3. Charlie Parker 스타일 분석**

**특징**:
```
1. Bebop 스케일 사용
2. 빠른 템포 (200+ BPM)
3. 크로매틱 어프로치 노트
4. 대칭적 프레이즈
5. 코드 톤 중심 + 텐션 노트
```

**대표곡 분석**:
```python
# analyze_charlie_parker.py
import pretty_midi
import numpy as np

class CharlieParkerAnalyzer:
    def analyze_rhythm(self, midi_file):
        """리듬 분석"""
        midi = pretty_midi.PrettyMIDI(midi_file)
        instrument = midi.instruments[0]

        # 음표 간격 (Inter-Onset Interval)
        onsets = [note.start for note in instrument.notes]
        iois = np.diff(onsets)

        print(f"평균 음표 간격: {np.mean(iois):.3f}초")
        print(f"최소 간격: {np.min(iois):.3f}초")
        print(f"최대 간격: {np.max(iois):.3f}초")

        # 히스토그램
        import matplotlib.pyplot as plt
        plt.hist(iois, bins=50)
        plt.xlabel('Inter-Onset Interval (s)')
        plt.ylabel('Frequency')
        plt.title('Charlie Parker Rhythm Pattern')
        plt.show()

    def analyze_pitch_class(self, midi_file):
        """음계 분포 분석"""
        midi = pretty_midi.PrettyMIDI(midi_file)
        instrument = midi.instruments[0]

        pitch_classes = [note.pitch % 12 for note in instrument.notes]
        histogram = [pitch_classes.count(pc) for pc in range(12)]

        note_names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

        import matplotlib.pyplot as plt
        plt.bar(note_names, histogram)
        plt.xlabel('Pitch Class')
        plt.ylabel('Count')
        plt.title('Charlie Parker Pitch Class Distribution')
        plt.show()

# 사용법
analyzer = CharlieParkerAnalyzer()
analyzer.analyze_rhythm('ornithology.mid')
analyzer.analyze_pitch_class('ornithology.mid')
```

---

### Week 5-6: Python 음악 라이브러리

#### 🛠️ 라이브러리 설치

```bash
# 가상환경 생성
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 라이브러리 설치
pip install pretty_midi music21 mido librosa matplotlib numpy
```

#### 📘 라이브러리 가이드

**1. pretty_midi**

가장 간단한 MIDI 처리 라이브러리

```python
import pretty_midi

# MIDI 파일 읽기
midi = pretty_midi.PrettyMIDI('song.mid')

# 악기 순회
for instrument in midi.instruments:
    print(f"Instrument: {instrument.name}")

    # 음표 순회
    for note in instrument.notes:
        print(f"Pitch: {note.pitch}, Start: {note.start}, End: {note.end}")

# 새 MIDI 만들기
new_midi = pretty_midi.PrettyMIDI()
piano = pretty_midi.Instrument(program=0)

# C major scale
for i, pitch in enumerate([60, 62, 64, 65, 67, 69, 71, 72]):
    note = pretty_midi.Note(
        velocity=100,
        pitch=pitch,
        start=i * 0.5,
        end=(i + 1) * 0.5
    )
    piano.notes.append(note)

new_midi.instruments.append(piano)
new_midi.write('c_major_scale.mid')
```

**2. music21**

음악 이론 분석에 강력

```python
from music21 import converter, chord, note

# MIDI 파일 분석
score = converter.parse('song.mid')

# 조성 분석
key = score.analyze('key')
print(f"Key: {key}")

# 코드 추출
chords = score.flatten().getElementsByClass(chord.Chord)
for c in chords[:5]:
    print(f"Chord: {c.pitches}")

# 새 멜로디 만들기
from music21 import stream, note, tempo

melody = stream.Stream()
melody.append(tempo.MetronomeMark(number=120))

notes_to_add = ['C4', 'D4', 'E4', 'F4', 'G4', 'A4', 'B4', 'C5']
for pitch in notes_to_add:
    n = note.Note(pitch, quarterLength=1.0)
    melody.append(n)

melody.write('midi', 'melody.mid')
```

**3. mido**

저수준 MIDI 조작

```python
import mido

# MIDI 파일 읽기
mid = mido.MidiFile('song.mid')

# 메시지 출력
for msg in mid.play():
    print(msg)

# 새 MIDI 파일 만들기
mid = mido.MidiFile()
track = mido.MidiTrack()
mid.tracks.append(track)

# 메타 정보
track.append(mido.MetaMessage('set_tempo', tempo=500000))

# 음표 추가
track.append(mido.Message('note_on', note=60, velocity=64, time=0))
track.append(mido.Message('note_off', note=60, velocity=64, time=480))

mid.save('new_song.mid')
```

---

### Week 7-8: 프로젝트 실습

#### 🎹 프로젝트 1: MIDI 시각화 도구

```python
# midi_visualizer.py
import pretty_midi
import matplotlib.pyplot as plt
import numpy as np

class MIDIVisualizer:
    def __init__(self, midi_file):
        self.midi = pretty_midi.PrettyMIDI(midi_file)

    def plot_piano_roll(self, fs=100):
        """Piano Roll 그리기"""
        piano_roll = self.midi.get_piano_roll(fs=fs)

        plt.figure(figsize=(14, 6))
        plt.imshow(piano_roll, aspect='auto', origin='lower', cmap='hot', interpolation='nearest')
        plt.xlabel('Time (frames)')
        plt.ylabel('MIDI Note Number')
        plt.title('Piano Roll')
        plt.colorbar(label='Velocity')
        plt.tight_layout()
        plt.savefig('piano_roll.png', dpi=150)
        plt.show()

    def plot_note_histogram(self):
        """음높이 히스토그램"""
        all_notes = []
        for instrument in self.midi.instruments:
            all_notes.extend([note.pitch for note in instrument.notes])

        plt.figure(figsize=(10, 5))
        plt.hist(all_notes, bins=range(0, 128), alpha=0.7)
        plt.xlabel('MIDI Note Number')
        plt.ylabel('Count')
        plt.title('Note Distribution')
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig('note_histogram.png', dpi=150)
        plt.show()

    def plot_velocity_over_time(self):
        """시간에 따른 Velocity 변화"""
        times = []
        velocities = []

        for instrument in self.midi.instruments:
            for note in instrument.notes:
                times.append(note.start)
                velocities.append(note.velocity)

        plt.figure(figsize=(12, 4))
        plt.scatter(times, velocities, alpha=0.5, s=10)
        plt.xlabel('Time (s)')
        plt.ylabel('Velocity')
        plt.title('Velocity Over Time')
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig('velocity_over_time.png', dpi=150)
        plt.show()

# 사용법
visualizer = MIDIVisualizer('charlie_parker.mid')
visualizer.plot_piano_roll()
visualizer.plot_note_histogram()
visualizer.plot_velocity_over_time()
```

#### 🎼 프로젝트 2: 간단한 멜로디 생성기 (규칙 기반)

```python
# melody_generator.py
import pretty_midi
import random

class SimpleMelodyGenerator:
    def __init__(self, key='C', scale_type='major'):
        self.key = self.note_to_midi(key + '4')
        self.scale = self.get_scale(scale_type)

    def note_to_midi(self, note_name):
        """음이름 → MIDI 번호"""
        notes = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
        octave = int(note_name[-1])
        note = note_name[:-1]
        return 12 * (octave + 1) + notes[note]

    def get_scale(self, scale_type):
        """스케일 패턴"""
        patterns = {
            'major': [0, 2, 4, 5, 7, 9, 11, 12],
            'minor': [0, 2, 3, 5, 7, 8, 10, 12],
            'blues': [0, 3, 5, 6, 7, 10, 12],
        }
        return [self.key + interval for interval in patterns[scale_type]]

    def generate_melody(self, num_notes=16, bpm=120):
        """멜로디 생성"""
        midi = pretty_midi.PrettyMIDI(initial_tempo=bpm)
        piano = pretty_midi.Instrument(program=0)

        current_time = 0.0
        note_duration = 0.5  # 8th note

        for _ in range(num_notes):
            # 스케일에서 랜덤 선택
            pitch = random.choice(self.scale)

            # 이전 음과 너무 멀리 떨어지지 않게 (최대 5도)
            # (간단화를 위해 생략)

            # Velocity 변화 (80-110)
            velocity = random.randint(80, 110)

            note = pretty_midi.Note(
                velocity=velocity,
                pitch=pitch,
                start=current_time,
                end=current_time + note_duration
            )
            piano.notes.append(note)

            current_time += note_duration

        midi.instruments.append(piano)
        return midi

# 사용법
generator = SimpleMelodyGenerator(key='C', scale_type='blues')
midi = generator.generate_melody(num_notes=32, bpm=140)
midi.write('generated_melody.mid')
```

---

## 📝 과제

### 과제 1: Charlie Parker 곡 5개 분석

1. `ornithology.mid` 다운로드
2. `MIDIVisualizer`로 시각화
3. `CharlieParkerAnalyzer`로 분석
4. 분석 리포트 작성

**리포트 템플릿**:
```markdown
# Charlie Parker 분석 리포트

## 곡 정보
- 제목: Ornithology
- BPM: 약 220
- 키: C major

## 분석 결과

### 1. 리듬 패턴
- 평균 음표 간격: 0.120초
- 주로 8th note와 16th note 사용
- Swing 느낌

### 2. 음계 분포
[히스토그램 이미지]
- 가장 많이 사용된 음: G (sol)
- Bebop scale 특징 확인

### 3. Velocity 패턴
[그래프 이미지]
- 평균 velocity: 95
- 강약 변화가 큼 (70-110 범위)

## 인사이트
- Charlie Parker는 코드 톤을 중심으로...
- 크로매틱 어프로치를 자주 사용...
```

### 과제 2: 나만의 멜로디 생성기 개선

`SimpleMelodyGenerator`를 개선하여:
1. 이전 음과의 간격 제한 (melodic contour)
2. 리듬 패턴 다양화
3. 코드 진행 기반 음 선택

---

## ✅ Week별 체크리스트

### Week 1-2
- [ ] Reddit 프로젝트 테스트 완료
- [ ] Docker 배포 성공
- [ ] README 작성
- [ ] GitHub 프로필 정리

### Week 3-4
- [ ] MIDI 기초 이해
- [ ] pretty_midi 실습 5회
- [ ] 재즈 이론 학습
- [ ] Charlie Parker 3곡 분석

### Week 5-6
- [ ] music21 실습
- [ ] mido 실습
- [ ] MIDI 시각화 도구 완성

### Week 7-8
- [ ] 멜로디 생성기 완성
- [ ] 과제 1 완료
- [ ] 과제 2 완료
- [ ] Phase 2 준비

---

## 📚 추가 학습 자료

### 책
- "The Jazz Theory Book" (Mark Levine)
- "Bebop Bible" (Les Wise)

### 온라인 강의
- Coursera: "Audio Signal Processing for Music Applications"
- YouTube: "Music Theory for Computer Musicians"

### 논문
- "A First Look at Music Representation Learning" (arXiv)

---

**Phase 1 완료 후 Phase 2로 넘어가세요!** 🎵
