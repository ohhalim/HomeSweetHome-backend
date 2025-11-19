# Phase 2: 데이터 준비 (Data Preparation)

**기간**: 1-2개월
**목표**: Charlie Parker 스타일 학습을 위한 고품질 MIDI 데이터셋 구축
**시간 배분**: Backend 30% : Music AI 70%

---

## 📋 Phase 2 개요

이 단계는 Music AI 프로젝트의 핵심 기반입니다. 좋은 데이터 없이는 좋은 모델을 만들 수 없습니다.

### 목표
1. Charlie Parker MIDI 파일 100곡 이상 수집
2. 데이터 품질 검증 및 정제
3. 학습용 데이터셋 구조화
4. 데이터 증강(Augmentation) 기법 적용
5. 분석 도구 개발

---

## 🗓️ 주차별 학습 계획

### Week 1-2: MIDI 데이터 수집 및 분석

#### 학습 목표
- Charlie Parker 음원의 MIDI 변환 방법 이해
- 기존 MIDI 데이터베이스 탐색
- 데이터 품질 평가 기준 수립

#### 1. MIDI 데이터 소스

**무료 소스**:
```python
# MIDI 데이터 소스 목록
MIDI_SOURCES = {
    # 1. The Jazz MIDI Collection
    "jazz_midi": {
        "url": "https://www.jazzmidi.eu/",
        "artists": ["Charlie Parker", "Dizzy Gillespie", "Bud Powell"],
        "quality": "medium",
        "count": "~50곡"
    },

    # 2. FreeMIDI
    "freemidi": {
        "url": "https://freemidi.org/",
        "search": "Charlie Parker bebop",
        "quality": "varies",
        "count": "~30곡"
    },

    # 3. MIDI World
    "midiworld": {
        "url": "http://www.midiworld.com/jazz.htm",
        "category": "Jazz",
        "quality": "low-medium",
        "count": "~20곡"
    }
}
```

**상업적 소스** (예산 있을 경우):
```python
COMMERCIAL_SOURCES = {
    # Yamaha MusicSoft
    "yamaha": {
        "url": "https://www.yamaha.com/musicsoft/",
        "price": "$2-5 per song",
        "quality": "high",
        "professional": True
    }
}
```

#### 2. 오디오 → MIDI 변환

Charlie Parker 원곡을 MIDI로 변환하는 방법:

**설치**:
```bash
# Basic MIDI Tools
pip install pretty_midi music21 mido

# Audio to MIDI conversion
pip install basic-pitch spotify-downloader

# Audio processing
pip install librosa pydub
```

**Basic Pitch 사용** (Spotify의 오픈소스):
```python
import basic_pitch
import numpy as np
from pathlib import Path

class AudioToMIDIConverter:
    """
    오디오 파일을 MIDI로 변환

    Note: 변환 품질은 100% 완벽하지 않음
    수동 검증 및 수정 필요
    """

    def __init__(self, output_dir="midi_converted"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)

    def convert_audio_to_midi(self, audio_file, song_name):
        """
        오디오 파일을 MIDI로 변환

        Args:
            audio_file: 입력 오디오 파일 (.wav, .mp3)
            song_name: 곡 이름

        Returns:
            변환된 MIDI 파일 경로
        """
        from basic_pitch.inference import predict_and_save

        print(f"Converting {song_name}...")

        # Basic Pitch로 변환
        output_path = self.output_dir / song_name

        predict_and_save(
            [audio_file],
            str(output_path),
            save_midi=True,
            sonify_midi=False,
            save_model_outputs=False,
            save_notes=False
        )

        midi_file = output_path / f"{Path(audio_file).stem}_basic_pitch.mid"

        if midi_file.exists():
            print(f"✓ Converted: {midi_file}")
            return str(midi_file)
        else:
            print(f"✗ Conversion failed")
            return None

    def batch_convert(self, audio_files):
        """여러 오디오 파일 일괄 변환"""
        results = []

        for audio_file in audio_files:
            song_name = Path(audio_file).stem
            midi_path = self.convert_audio_to_midi(audio_file, song_name)

            if midi_path:
                results.append({
                    'audio': audio_file,
                    'midi': midi_path,
                    'status': 'success'
                })
            else:
                results.append({
                    'audio': audio_file,
                    'midi': None,
                    'status': 'failed'
                })

        return results

# 사용 예시
converter = AudioToMIDIConverter()

# Charlie Parker 음원 변환
audio_files = [
    "charlie_parker/ornithology.wav",
    "charlie_parker/anthropology.wav",
    "charlie_parker/koko.wav"
]

results = converter.batch_convert(audio_files)

for result in results:
    print(f"{result['audio']}: {result['status']}")
```

#### 3. MIDI 데이터 품질 검증

```python
import pretty_midi
import numpy as np
from dataclasses import dataclass
from typing import List, Optional

@dataclass
class MIDIQualityReport:
    """MIDI 파일 품질 리포트"""
    file_path: str
    is_valid: bool
    duration: float
    note_count: int
    tempo: float
    time_signature: Optional[str]
    has_multiple_instruments: bool
    issues: List[str]
    quality_score: float  # 0-100

class MIDIQualityChecker:
    """MIDI 파일 품질 검증"""

    # 품질 기준
    MIN_DURATION = 30.0  # 최소 30초
    MAX_DURATION = 600.0  # 최대 10분
    MIN_NOTES = 50  # 최소 음표 수
    MIN_TEMPO = 60  # 최소 BPM
    MAX_TEMPO = 400  # 최대 BPM

    def check_file(self, midi_file: str) -> MIDIQualityReport:
        """MIDI 파일 품질 검사"""
        issues = []

        try:
            midi = pretty_midi.PrettyMIDI(midi_file)
        except Exception as e:
            return MIDIQualityReport(
                file_path=midi_file,
                is_valid=False,
                duration=0,
                note_count=0,
                tempo=0,
                time_signature=None,
                has_multiple_instruments=False,
                issues=[f"파일 로드 실패: {str(e)}"],
                quality_score=0
            )

        # 기본 정보 추출
        duration = midi.get_end_time()

        # 모든 악기의 노트 수집
        all_notes = []
        for instrument in midi.instruments:
            if not instrument.is_drum:
                all_notes.extend(instrument.notes)

        note_count = len(all_notes)

        # 템포 추출
        tempo_changes = midi.get_tempo_changes()
        tempo = np.mean(tempo_changes[1]) if len(tempo_changes[1]) > 0 else 120

        # Time signature
        time_sig = midi.time_signature_changes
        time_signature = f"{time_sig[0].numerator}/{time_sig[0].denominator}" if time_sig else None

        # 품질 검사
        if duration < self.MIN_DURATION:
            issues.append(f"너무 짧음: {duration:.1f}초 (최소 {self.MIN_DURATION}초)")

        if duration > self.MAX_DURATION:
            issues.append(f"너무 김: {duration:.1f}초 (최대 {self.MAX_DURATION}초)")

        if note_count < self.MIN_NOTES:
            issues.append(f"음표 부족: {note_count}개 (최소 {self.MIN_NOTES}개)")

        if tempo < self.MIN_TEMPO or tempo > self.MAX_TEMPO:
            issues.append(f"비정상 템포: {tempo:.0f} BPM")

        if len(midi.instruments) == 0:
            issues.append("악기 정보 없음")

        if len(midi.instruments) > 1:
            issues.append(f"다중 악기: {len(midi.instruments)}개 (솔로 데이터 권장)")

        # 리듬 다양성 검사
        if len(all_notes) > 0:
            note_durations = [note.end - note.start for note in all_notes]
            duration_std = np.std(note_durations)

            if duration_std < 0.01:
                issues.append("리듬 다양성 부족 (기계적 연주 의심)")

        # 품질 점수 계산 (0-100)
        quality_score = 100.0
        quality_score -= len(issues) * 15  # 문제 당 -15점
        quality_score = max(0, quality_score)

        is_valid = len(issues) == 0

        return MIDIQualityReport(
            file_path=midi_file,
            is_valid=is_valid,
            duration=duration,
            note_count=note_count,
            tempo=tempo,
            time_signature=time_signature,
            has_multiple_instruments=len(midi.instruments) > 1,
            issues=issues,
            quality_score=quality_score
        )

    def batch_check(self, midi_files: List[str]) -> List[MIDIQualityReport]:
        """여러 MIDI 파일 일괄 검사"""
        reports = []

        for midi_file in midi_files:
            report = self.check_file(midi_file)
            reports.append(report)

        return reports

    def generate_summary(self, reports: List[MIDIQualityReport]):
        """검사 결과 요약"""
        total = len(reports)
        valid = sum(1 for r in reports if r.is_valid)
        avg_score = np.mean([r.quality_score for r in reports])

        print(f"\n{'='*60}")
        print(f"MIDI 품질 검사 결과")
        print(f"{'='*60}")
        print(f"총 파일 수: {total}")
        print(f"유효 파일: {valid} ({valid/total*100:.1f}%)")
        print(f"평균 품질: {avg_score:.1f}/100")
        print(f"{'='*60}\n")

        # 문제별 통계
        all_issues = {}
        for report in reports:
            for issue in report.issues:
                issue_type = issue.split(':')[0]
                all_issues[issue_type] = all_issues.get(issue_type, 0) + 1

        if all_issues:
            print("발견된 문제:")
            for issue, count in sorted(all_issues.items(), key=lambda x: -x[1]):
                print(f"  - {issue}: {count}건")

        return {
            'total': total,
            'valid': valid,
            'average_score': avg_score,
            'issues': all_issues
        }

# 사용 예시
from pathlib import Path

checker = MIDIQualityChecker()

# 모든 MIDI 파일 검사
midi_files = list(Path("midi_data/charlie_parker").glob("*.mid"))
reports = checker.batch_check([str(f) for f in midi_files])

# 결과 출력
for report in reports:
    print(f"\n{Path(report.file_path).name}")
    print(f"  Duration: {report.duration:.1f}s")
    print(f"  Notes: {report.note_count}")
    print(f"  Tempo: {report.tempo:.0f} BPM")
    print(f"  Quality: {report.quality_score:.0f}/100")

    if report.issues:
        print(f"  Issues:")
        for issue in report.issues:
            print(f"    - {issue}")

# 요약
summary = checker.generate_summary(reports)

# 고품질 파일만 필터링
high_quality = [r for r in reports if r.quality_score >= 70]
print(f"\n고품질 파일 ({len(high_quality)}개):")
for report in high_quality:
    print(f"  - {Path(report.file_path).name} ({report.quality_score:.0f}점)")
```

---

### Week 3-4: 데이터 정제 및 전처리

#### 학습 목표
- MIDI 데이터 정규화
- 잘못된 음표 수정
- 트랙 분리 및 정리

#### 1. MIDI 데이터 정제

```python
import pretty_midi
import numpy as np
from typing import List, Tuple

class MIDICleaner:
    """MIDI 데이터 정제 도구"""

    def __init__(self):
        self.min_note_duration = 0.05  # 50ms 미만 노트 제거
        self.min_note_gap = 0.01  # 10ms 미만 간격 병합

    def clean_file(self, input_file: str, output_file: str) -> dict:
        """
        MIDI 파일 정제

        수행 작업:
        1. 너무 짧은 음표 제거
        2. 중복 음표 제거
        3. 드럼 트랙 제거
        4. 템포 정규화
        5. 볼륨 정규화
        """
        midi = pretty_midi.PrettyMIDI(input_file)

        stats = {
            'original_notes': 0,
            'removed_notes': 0,
            'merged_notes': 0,
            'instruments_removed': 0
        }

        # 멜로디 악기만 유지 (드럼 제거)
        melody_instruments = []
        for instrument in midi.instruments:
            if not instrument.is_drum:
                melody_instruments.append(instrument)
            else:
                stats['instruments_removed'] += 1

        midi.instruments = melody_instruments

        # 각 악기 정제
        for instrument in midi.instruments:
            original_count = len(instrument.notes)
            stats['original_notes'] += original_count

            # 1. 짧은 음표 제거
            valid_notes = [
                note for note in instrument.notes
                if (note.end - note.start) >= self.min_note_duration
            ]

            removed = original_count - len(valid_notes)
            stats['removed_notes'] += removed

            # 2. 중복 제거 및 정렬
            valid_notes = self._remove_duplicates(valid_notes)
            valid_notes.sort(key=lambda n: n.start)

            # 3. 볼륨 정규화 (60-100)
            for note in valid_notes:
                note.velocity = max(60, min(100, note.velocity))

            instrument.notes = valid_notes

        # 4. 템포 정규화 (Charlie Parker 평균: 180-240 BPM)
        # 기존 템포를 bebop 범위로 조정
        tempo_changes = midi.get_tempo_changes()
        if len(tempo_changes[1]) > 0:
            avg_tempo = np.mean(tempo_changes[1])

            # 너무 느리면 2배속, 너무 빠르면 1/2배속
            if avg_tempo < 120:
                # 템포 2배로 조정하려면 모든 시간을 절반으로
                for instrument in midi.instruments:
                    for note in instrument.notes:
                        note.start /= 2
                        note.end /= 2
            elif avg_tempo > 300:
                # 템포 절반으로 조정하려면 모든 시간을 2배로
                for instrument in midi.instruments:
                    for note in instrument.notes:
                        note.start *= 2
                        note.end *= 2

        # 저장
        midi.write(output_file)

        return stats

    def _remove_duplicates(self, notes: List[pretty_midi.Note]) -> List[pretty_midi.Note]:
        """중복 음표 제거"""
        if not notes:
            return []

        unique_notes = []
        seen = set()

        for note in notes:
            # (pitch, start_time) 조합으로 중복 판단
            key = (note.pitch, round(note.start, 3))

            if key not in seen:
                seen.add(key)
                unique_notes.append(note)

        return unique_notes

    def batch_clean(self, input_dir: str, output_dir: str):
        """디렉토리 내 모든 MIDI 파일 정제"""
        from pathlib import Path

        input_path = Path(input_dir)
        output_path = Path(output_dir)
        output_path.mkdir(exist_ok=True)

        midi_files = list(input_path.glob("*.mid")) + list(input_path.glob("*.midi"))

        print(f"Found {len(midi_files)} MIDI files")

        total_stats = {
            'files_processed': 0,
            'files_failed': 0,
            'total_notes_removed': 0
        }

        for midi_file in midi_files:
            output_file = output_path / midi_file.name

            try:
                stats = self.clean_file(str(midi_file), str(output_file))
                total_stats['files_processed'] += 1
                total_stats['total_notes_removed'] += stats['removed_notes']

                print(f"✓ {midi_file.name}: {stats['removed_notes']} notes removed")

            except Exception as e:
                total_stats['files_failed'] += 1
                print(f"✗ {midi_file.name}: {str(e)}")

        print(f"\n{'='*60}")
        print(f"정제 완료:")
        print(f"  처리 성공: {total_stats['files_processed']}")
        print(f"  처리 실패: {total_stats['files_failed']}")
        print(f"  제거된 음표: {total_stats['total_notes_removed']}")
        print(f"{'='*60}")

        return total_stats

# 사용 예시
cleaner = MIDICleaner()

# 단일 파일 정제
stats = cleaner.clean_file(
    "raw_midi/ornithology.mid",
    "clean_midi/ornithology.mid"
)

# 일괄 정제
cleaner.batch_clean("raw_midi", "clean_midi")
```

#### 2. 데이터 증강 (Data Augmentation)

```python
import pretty_midi
import numpy as np
from pathlib import Path

class MIDIAugmenter:
    """
    MIDI 데이터 증강

    목표: 100곡 → 500곡 이상으로 확장
    """

    def __init__(self):
        # Bebop에서 자주 사용하는 키
        self.common_keys = [-2, -1, 0, 1, 2]  # Bb, B, C, Db, D

    def transpose(self, midi: pretty_midi.PrettyMIDI, semitones: int) -> pretty_midi.PrettyMIDI:
        """
        조옮김 (Transposition)

        Args:
            midi: 원본 MIDI
            semitones: 반음 단위 (-12 ~ +12)

        Returns:
            조옮김된 MIDI
        """
        new_midi = pretty_midi.PrettyMIDI()

        for instrument in midi.instruments:
            new_instrument = pretty_midi.Instrument(
                program=instrument.program,
                is_drum=instrument.is_drum,
                name=instrument.name
            )

            for note in instrument.notes:
                new_pitch = note.pitch + semitones

                # 유효 범위 체크 (MIDI: 0-127)
                if 0 <= new_pitch <= 127:
                    new_note = pretty_midi.Note(
                        velocity=note.velocity,
                        pitch=new_pitch,
                        start=note.start,
                        end=note.end
                    )
                    new_instrument.notes.append(new_note)

            new_midi.instruments.append(new_instrument)

        # 템포, 박자 복사
        for tempo_change in midi.get_tempo_changes()[0]:
            new_midi.tempo_changes.append(tempo_change)

        return new_midi

    def time_stretch(self, midi: pretty_midi.PrettyMIDI, factor: float) -> pretty_midi.PrettyMIDI:
        """
        템포 변경 (Time Stretching)

        Args:
            factor: 시간 배율 (0.9 = 10% 빠르게, 1.1 = 10% 느리게)
        """
        new_midi = pretty_midi.PrettyMIDI()

        for instrument in midi.instruments:
            new_instrument = pretty_midi.Instrument(
                program=instrument.program,
                is_drum=instrument.is_drum,
                name=instrument.name
            )

            for note in instrument.notes:
                new_note = pretty_midi.Note(
                    velocity=note.velocity,
                    pitch=note.pitch,
                    start=note.start * factor,
                    end=note.end * factor
                )
                new_instrument.notes.append(new_note)

            new_midi.instruments.append(new_instrument)

        return new_midi

    def velocity_variation(self, midi: pretty_midi.PrettyMIDI, variance: int = 10) -> pretty_midi.PrettyMIDI:
        """
        벨로시티 변화 (다이나믹 변화)

        Args:
            variance: 벨로시티 변화 범위 (±variance)
        """
        new_midi = pretty_midi.PrettyMIDI()

        for instrument in midi.instruments:
            new_instrument = pretty_midi.Instrument(
                program=instrument.program,
                is_drum=instrument.is_drum,
                name=instrument.name
            )

            for note in instrument.notes:
                # 랜덤 벨로시티 변화
                delta = np.random.randint(-variance, variance + 1)
                new_velocity = np.clip(note.velocity + delta, 1, 127)

                new_note = pretty_midi.Note(
                    velocity=new_velocity,
                    pitch=note.pitch,
                    start=note.start,
                    end=note.end
                )
                new_instrument.notes.append(new_note)

            new_midi.instruments.append(new_instrument)

        return new_midi

    def augment_file(self, input_file: str, output_dir: str, num_variations: int = 5):
        """
        단일 파일에서 여러 변형 생성

        Args:
            input_file: 입력 MIDI 파일
            output_dir: 출력 디렉토리
            num_variations: 생성할 변형 수
        """
        input_path = Path(input_file)
        output_path = Path(output_dir)
        output_path.mkdir(exist_ok=True)

        midi = pretty_midi.PrettyMIDI(input_file)
        base_name = input_path.stem

        variations = []

        # 원본 저장
        original_output = output_path / f"{base_name}_original.mid"
        midi.write(str(original_output))
        variations.append(('original', str(original_output)))

        # 조옮김 변형
        for semitones in self.common_keys:
            if semitones == 0:
                continue  # 원본과 동일

            transposed = self.transpose(midi, semitones)
            output_file = output_path / f"{base_name}_transpose{semitones:+d}.mid"
            transposed.write(str(output_file))
            variations.append((f'transpose{semitones:+d}', str(output_file)))

        # 템포 변형
        for factor in [0.95, 1.05]:
            stretched = self.time_stretch(midi, factor)
            tempo_pct = int((factor - 1) * 100)
            output_file = output_path / f"{base_name}_tempo{tempo_pct:+d}pct.mid"
            stretched.write(str(output_file))
            variations.append((f'tempo{tempo_pct:+d}%', str(output_file)))

        # 벨로시티 변형
        for i in range(2):
            varied = self.velocity_variation(midi, variance=15)
            output_file = output_path / f"{base_name}_velocity{i+1}.mid"
            varied.write(str(output_file))
            variations.append((f'velocity{i+1}', str(output_file)))

        return variations

    def batch_augment(self, input_dir: str, output_dir: str):
        """디렉토리 내 모든 파일 증강"""
        input_path = Path(input_dir)
        midi_files = list(input_path.glob("*.mid"))

        print(f"Augmenting {len(midi_files)} files...")

        total_generated = 0

        for midi_file in midi_files:
            try:
                variations = self.augment_file(str(midi_file), output_dir)
                total_generated += len(variations)
                print(f"✓ {midi_file.name}: {len(variations)} variations")
            except Exception as e:
                print(f"✗ {midi_file.name}: {str(e)}")

        print(f"\n총 {total_generated}개 파일 생성")
        print(f"증강 비율: {total_generated / len(midi_files):.1f}x")

        return total_generated

# 사용 예시
augmenter = MIDIAugmenter()

# 단일 파일 증강
variations = augmenter.augment_file(
    "clean_midi/ornithology.mid",
    "augmented_midi"
)

print(f"Generated {len(variations)} variations:")
for var_type, var_file in variations:
    print(f"  - {var_type}: {Path(var_file).name}")

# 전체 디렉토리 증강
augmenter.batch_augment("clean_midi", "augmented_midi")
```

---

### Week 5-6: 데이터셋 구조화

#### 학습 목표
- 학습/검증/테스트 데이터 분할
- 메타데이터 관리
- 데이터 로더 구현

#### 1. 데이터셋 구조 설계

```
charlie_parker_dataset/
├── raw/                    # 원본 MIDI
│   ├── ornithology.mid
│   ├── anthropology.mid
│   └── ...
├── clean/                  # 정제된 MIDI
│   ├── ornithology.mid
│   └── ...
├── augmented/             # 증강된 MIDI
│   ├── train/             # 학습용 (70%)
│   ├── validation/        # 검증용 (15%)
│   └── test/              # 테스트용 (15%)
├── metadata.json          # 메타데이터
└── statistics.json        # 통계 정보
```

#### 2. 메타데이터 생성

```python
import json
import pretty_midi
import numpy as np
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import List, Dict
import hashlib

@dataclass
class MIDIMetadata:
    """MIDI 파일 메타데이터"""
    file_path: str
    file_hash: str
    song_name: str
    duration: float
    tempo_avg: float
    tempo_std: float
    note_count: int
    pitch_min: int
    pitch_max: int
    pitch_mean: float
    pitch_std: float
    time_signature: str
    key_signature: str  # 예: "C", "Bb", "F#"
    instrument: str
    data_split: str  # train/validation/test
    augmentation_type: str  # original/transpose/tempo/velocity
    source: str  # 출처 (jazz_midi, converted, etc)

class DatasetBuilder:
    """Charlie Parker 데이터셋 빌더"""

    def __init__(self, base_dir: str):
        self.base_dir = Path(base_dir)
        self.metadata_list: List[MIDIMetadata] = []

    def analyze_midi(self, file_path: str, data_split: str,
                     augmentation_type: str, source: str) -> MIDIMetadata:
        """MIDI 파일 분석 및 메타데이터 생성"""

        midi = pretty_midi.PrettyMIDI(file_path)

        # 파일 해시 (중복 체크용)
        with open(file_path, 'rb') as f:
            file_hash = hashlib.md5(f.read()).hexdigest()

        # 기본 정보
        duration = midi.get_end_time()
        tempo_changes = midi.get_tempo_changes()
        tempo_avg = np.mean(tempo_changes[1]) if len(tempo_changes[1]) > 0 else 120
        tempo_std = np.std(tempo_changes[1]) if len(tempo_changes[1]) > 0 else 0

        # 음표 정보
        all_notes = []
        instrument_name = "unknown"

        for instrument in midi.instruments:
            if not instrument.is_drum:
                all_notes.extend(instrument.notes)
                instrument_name = pretty_midi.program_to_instrument_name(instrument.program)

        pitches = [note.pitch for note in all_notes]

        # Time signature
        time_sig = midi.time_signature_changes
        time_signature = f"{time_sig[0].numerator}/{time_sig[0].denominator}" if time_sig else "4/4"

        # Key signature 추정 (간단한 방법)
        key_signature = self._estimate_key(pitches)

        return MIDIMetadata(
            file_path=str(Path(file_path).relative_to(self.base_dir)),
            file_hash=file_hash,
            song_name=Path(file_path).stem,
            duration=duration,
            tempo_avg=tempo_avg,
            tempo_std=tempo_std,
            note_count=len(all_notes),
            pitch_min=min(pitches) if pitches else 0,
            pitch_max=max(pitches) if pitches else 0,
            pitch_mean=np.mean(pitches) if pitches else 0,
            pitch_std=np.std(pitches) if pitches else 0,
            time_signature=time_signature,
            key_signature=key_signature,
            instrument=instrument_name,
            data_split=data_split,
            augmentation_type=augmentation_type,
            source=source
        )

    def _estimate_key(self, pitches: List[int]) -> str:
        """음계 추정 (Krumhansl-Schmuckler 알고리즘 간소화)"""
        if not pitches:
            return "C"

        # 각 음(C=0, C#=1, ..., B=11)의 빈도 계산
        pitch_classes = [p % 12 for p in pitches]
        histogram = np.bincount(pitch_classes, minlength=12)

        # 가장 많이 등장하는 음 = Tonic 후보
        tonic = np.argmax(histogram)

        note_names = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B']

        return note_names[tonic]

    def split_dataset(self, files: List[str], train_ratio=0.7, val_ratio=0.15):
        """데이터셋 분할 (train/validation/test)"""
        np.random.seed(42)
        np.random.shuffle(files)

        n = len(files)
        n_train = int(n * train_ratio)
        n_val = int(n * val_ratio)

        train_files = files[:n_train]
        val_files = files[n_train:n_train + n_val]
        test_files = files[n_train + n_val:]

        return {
            'train': train_files,
            'validation': val_files,
            'test': test_files
        }

    def build(self, augmented_dir: str, source: str = "charlie_parker"):
        """데이터셋 빌드"""

        augmented_path = Path(augmented_dir)
        midi_files = list(augmented_path.glob("*.mid"))

        print(f"Found {len(midi_files)} MIDI files")

        # 데이터 분할
        splits = self.split_dataset([str(f) for f in midi_files])

        # 각 파일 분석
        for split_name, file_list in splits.items():
            print(f"\nProcessing {split_name} set ({len(file_list)} files)...")

            # 분할별 디렉토리 생성
            split_dir = self.base_dir / "augmented" / split_name
            split_dir.mkdir(parents=True, exist_ok=True)

            for file_path in file_list:
                # 파일명에서 증강 타입 추출
                file_name = Path(file_path).stem

                if "_transpose" in file_name:
                    aug_type = "transpose"
                elif "_tempo" in file_name:
                    aug_type = "tempo"
                elif "_velocity" in file_name:
                    aug_type = "velocity"
                elif "_original" in file_name:
                    aug_type = "original"
                else:
                    aug_type = "unknown"

                try:
                    # 메타데이터 생성
                    metadata = self.analyze_midi(
                        file_path,
                        split_name,
                        aug_type,
                        source
                    )

                    self.metadata_list.append(metadata)

                    # 파일 복사
                    import shutil
                    dest = split_dir / Path(file_path).name
                    shutil.copy2(file_path, dest)

                    print(f"  ✓ {Path(file_path).name}")

                except Exception as e:
                    print(f"  ✗ {Path(file_path).name}: {str(e)}")

        # 메타데이터 저장
        self.save_metadata()

        # 통계 생성
        self.generate_statistics()

        print(f"\n✓ Dataset built successfully!")
        print(f"  Total files: {len(self.metadata_list)}")
        print(f"  Train: {sum(1 for m in self.metadata_list if m.data_split == 'train')}")
        print(f"  Validation: {sum(1 for m in self.metadata_list if m.data_split == 'validation')}")
        print(f"  Test: {sum(1 for m in self.metadata_list if m.data_split == 'test')}")

    def save_metadata(self):
        """메타데이터 JSON 저장"""
        metadata_file = self.base_dir / "metadata.json"

        with open(metadata_file, 'w', encoding='utf-8') as f:
            json.dump(
                [asdict(m) for m in self.metadata_list],
                f,
                indent=2,
                ensure_ascii=False
            )

        print(f"\n✓ Metadata saved: {metadata_file}")

    def generate_statistics(self):
        """데이터셋 통계 생성"""

        stats = {
            'dataset_info': {
                'total_files': len(self.metadata_list),
                'train': sum(1 for m in self.metadata_list if m.data_split == 'train'),
                'validation': sum(1 for m in self.metadata_list if m.data_split == 'validation'),
                'test': sum(1 for m in self.metadata_list if m.data_split == 'test')
            },
            'duration': {
                'total_seconds': sum(m.duration for m in self.metadata_list),
                'mean': np.mean([m.duration for m in self.metadata_list]),
                'std': np.std([m.duration for m in self.metadata_list]),
                'min': min(m.duration for m in self.metadata_list),
                'max': max(m.duration for m in self.metadata_list)
            },
            'tempo': {
                'mean': np.mean([m.tempo_avg for m in self.metadata_list]),
                'std': np.std([m.tempo_avg for m in self.metadata_list]),
                'min': min(m.tempo_avg for m in self.metadata_list),
                'max': max(m.tempo_avg for m in self.metadata_list)
            },
            'pitch_range': {
                'overall_min': min(m.pitch_min for m in self.metadata_list),
                'overall_max': max(m.pitch_max for m in self.metadata_list),
                'mean_range': np.mean([m.pitch_max - m.pitch_min for m in self.metadata_list])
            },
            'augmentation_distribution': self._count_by_field('augmentation_type'),
            'key_distribution': self._count_by_field('key_signature'),
            'source_distribution': self._count_by_field('source')
        }

        stats_file = self.base_dir / "statistics.json"

        with open(stats_file, 'w', encoding='utf-8') as f:
            json.dump(stats, f, indent=2)

        print(f"✓ Statistics saved: {stats_file}")

        # 콘솔 출력
        print(f"\n{'='*60}")
        print("Dataset Statistics")
        print(f"{'='*60}")
        print(f"Total Duration: {stats['duration']['total_seconds']/60:.1f} minutes")
        print(f"Average Tempo: {stats['tempo']['mean']:.0f} BPM")
        print(f"Pitch Range: {stats['pitch_range']['overall_min']} - {stats['pitch_range']['overall_max']}")
        print(f"\nKey Distribution:")
        for key, count in sorted(stats['key_distribution'].items(), key=lambda x: -x[1])[:5]:
            print(f"  {key}: {count}")
        print(f"{'='*60}")

        return stats

    def _count_by_field(self, field_name: str) -> Dict[str, int]:
        """특정 필드별 개수 세기"""
        counts = {}
        for metadata in self.metadata_list:
            value = getattr(metadata, field_name)
            counts[value] = counts.get(value, 0) + 1
        return counts

# 사용 예시
builder = DatasetBuilder("charlie_parker_dataset")

# 데이터셋 빌드
builder.build(
    augmented_dir="augmented_midi",
    source="mixed"  # jazz_midi + converted
)

# 메타데이터 로드 및 확인
with open("charlie_parker_dataset/metadata.json") as f:
    metadata = json.load(f)

print(f"\nLoaded {len(metadata)} files")
print(f"First entry: {json.dumps(metadata[0], indent=2)}")
```

---

### Week 7-8: 데이터 로더 구현

#### 학습 목표
- PyTorch DataLoader 구현
- 데이터 전처리 파이프라인
- 배치 처리

#### PyTorch Dataset 구현

```python
import torch
from torch.utils.data import Dataset, DataLoader
import pretty_midi
import numpy as np
import json
from pathlib import Path
from typing import List, Tuple, Optional

class CharlieParkerMIDIDataset(Dataset):
    """
    Charlie Parker MIDI Dataset for PyTorch

    Returns:
        sequence: (seq_len, features) tensor
        target: (seq_len, ) tensor (다음 음표 예측)
    """

    def __init__(
        self,
        metadata_file: str,
        base_dir: str,
        split: str = "train",
        seq_length: int = 128,
        vocab_size: int = 128,  # MIDI pitch range
        normalize: bool = True
    ):
        self.base_dir = Path(base_dir)
        self.seq_length = seq_length
        self.vocab_size = vocab_size
        self.normalize = normalize

        # 메타데이터 로드
        with open(metadata_file) as f:
            all_metadata = json.load(f)

        # 특정 split만 필터링
        self.metadata = [m for m in all_metadata if m['data_split'] == split]

        print(f"Loaded {len(self.metadata)} files for {split} set")

        # 전체 데이터 미리 로드 (메모리가 충분하다면)
        self.sequences = []
        self._preload_data()

    def _preload_data(self):
        """모든 MIDI 파일을 시퀀스로 변환하여 메모리에 로드"""
        print("Preloading data...")

        for meta in self.metadata:
            file_path = self.base_dir / meta['file_path']

            try:
                sequences = self._midi_to_sequences(str(file_path))
                self.sequences.extend(sequences)
            except Exception as e:
                print(f"Error loading {file_path}: {e}")

        print(f"Total sequences: {len(self.sequences)}")

    def _midi_to_sequences(self, midi_file: str) -> List[Tuple[np.ndarray, np.ndarray]]:
        """
        MIDI 파일을 시퀀스로 변환

        Returns:
            List of (input_sequence, target_sequence) pairs
        """
        midi = pretty_midi.PrettyMIDI(midi_file)

        # 멜로디 노트 추출
        notes = []
        for instrument in midi.instruments:
            if not instrument.is_drum:
                notes.extend(instrument.notes)

        # 시간순 정렬
        notes.sort(key=lambda n: n.start)

        if len(notes) < self.seq_length + 1:
            return []  # 너무 짧은 곡은 제외

        # 노트를 feature vector로 변환
        # [pitch, duration, velocity, time_since_last]
        features = []

        for i, note in enumerate(notes):
            pitch = note.pitch
            duration = note.end - note.start
            velocity = note.velocity / 127.0  # normalize

            # 이전 노트로부터의 시간 간격
            if i > 0:
                time_since_last = note.start - notes[i-1].start
            else:
                time_since_last = 0

            features.append([pitch, duration, velocity, time_since_last])

        features = np.array(features, dtype=np.float32)

        # Normalization
        if self.normalize:
            # Duration, time_since_last는 log scale
            features[:, 1] = np.log1p(features[:, 1])
            features[:, 3] = np.log1p(features[:, 3])

        # Sliding window로 시퀀스 생성
        sequences = []

        for i in range(len(features) - self.seq_length):
            input_seq = features[i:i + self.seq_length]
            target_pitch = int(features[i + self.seq_length, 0])  # 다음 음표의 pitch

            sequences.append((input_seq, target_pitch))

        return sequences

    def __len__(self) -> int:
        return len(self.sequences)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor]:
        input_seq, target = self.sequences[idx]

        return (
            torch.tensor(input_seq, dtype=torch.float32),
            torch.tensor(target, dtype=torch.long)
        )

# 사용 예시
def create_dataloaders(
    metadata_file: str,
    base_dir: str,
    batch_size: int = 32,
    num_workers: int = 4
):
    """DataLoader 생성"""

    # Dataset 생성
    train_dataset = CharlieParkerMIDIDataset(
        metadata_file=metadata_file,
        base_dir=base_dir,
        split="train"
    )

    val_dataset = CharlieParkerMIDIDataset(
        metadata_file=metadata_file,
        base_dir=base_dir,
        split="validation"
    )

    test_dataset = CharlieParkerMIDIDataset(
        metadata_file=metadata_file,
        base_dir=base_dir,
        split="test"
    )

    # DataLoader 생성
    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        shuffle=True,
        num_workers=num_workers,
        pin_memory=True
    )

    val_loader = DataLoader(
        val_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=num_workers,
        pin_memory=True
    )

    test_loader = DataLoader(
        test_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=num_workers,
        pin_memory=True
    )

    print(f"\nDataLoaders created:")
    print(f"  Train: {len(train_dataset)} sequences, {len(train_loader)} batches")
    print(f"  Validation: {len(val_dataset)} sequences, {len(val_loader)} batches")
    print(f"  Test: {len(test_dataset)} sequences, {len(test_loader)} batches")

    return train_loader, val_loader, test_loader

# 실행
train_loader, val_loader, test_loader = create_dataloaders(
    metadata_file="charlie_parker_dataset/metadata.json",
    base_dir="charlie_parker_dataset",
    batch_size=32
)

# 샘플 확인
for inputs, targets in train_loader:
    print(f"Input shape: {inputs.shape}")  # (batch_size, seq_length, 4)
    print(f"Target shape: {targets.shape}")  # (batch_size,)
    print(f"Sample input: {inputs[0, :5]}")  # 첫 5개 노트
    print(f"Sample target: {targets[0]}")  # 다음 음표의 pitch
    break
```

---

## 📊 Phase 2 평가 기준

### 데이터 수집 (40%)
- [ ] Charlie Parker MIDI 파일 100곡 이상 수집
- [ ] 품질 점수 70점 이상 파일 비율 80% 이상
- [ ] 다양한 곡 포함 (빠른 곡, 느린 곡, 다양한 키)

### 데이터 정제 (20%)
- [ ] 모든 파일 정제 완료 (드럼 제거, 노이즈 제거)
- [ ] 템포 정규화 (180-240 BPM 범위)
- [ ] 음표 품질 검증

### 데이터 증강 (20%)
- [ ] 원본 대비 5배 이상 증강 (100곡 → 500곡)
- [ ] 조옮김, 템포 변경, 벨로시티 변화 모두 적용

### 데이터셋 구조화 (20%)
- [ ] 메타데이터 JSON 생성 완료
- [ ] Train/Val/Test 분할 (70/15/15)
- [ ] PyTorch DataLoader 구현 및 테스트

---

## 🎯 Phase 2 완료 후 산출물

1. **charlie_parker_dataset/** 디렉토리
   - 500+ MIDI 파일 (augmented)
   - metadata.json
   - statistics.json

2. **Python 스크립트**
   - `audio_to_midi.py` - 오디오 변환 도구
   - `midi_cleaner.py` - 데이터 정제 도구
   - `midi_augmenter.py` - 데이터 증강 도구
   - `dataset_builder.py` - 데이터셋 빌더
   - `charlie_parker_dataset.py` - PyTorch Dataset

3. **문서**
   - `data_collection_report.md` - 데이터 수집 보고서
   - `data_statistics.md` - 데이터셋 통계

---

## 💡 Phase 2 팁

### 데이터 수집 전략
1. **무료 소스부터 시작**: jazzmidi.eu, freemidi.org
2. **품질 > 수량**: 저품질 100곡보다 고품질 50곡이 낫다
3. **수동 검증 필수**: 자동 변환 결과는 반드시 들어보고 확인

### 오디오 → MIDI 변환 주의사항
- Basic Pitch는 완벽하지 않음 (70-80% 정확도)
- 피아노 솔로가 가장 잘 변환됨
- 베이스, 드럼이 섞이면 정확도 하락
- 변환 후 반드시 수동 확인 및 수정

### 데이터 증강 전략
- **조옮김**: Bebop 주요 키 (Bb, F, Eb, C) 중심
- **템포**: ±5-10% 범위 (너무 크면 부자연스러움)
- **벨로시티**: 다이나믹 변화는 소폭으로

### Backend 학습 병행 (30%)
- Reddit 프로젝트 마무리 및 배포
- Docker Compose로 로컬 환경 구축
- 간단한 ML 모델 서빙 API 연습 (Flask)

---

## 🔗 참고 자료

### MIDI 처리
- [pretty_midi documentation](https://craffel.github.io/pretty-midi/)
- [music21 user guide](https://web.mit.edu/music21/doc/index.html)
- [Basic Pitch (Spotify)](https://github.com/spotify/basic-pitch)

### Charlie Parker 분석
- [The Charlie Parker Omnibook](https://www.amazon.com/Charlie-Parker-Omnibook-Instruments/dp/1883217504)
- YouTube: "Charlie Parker Solo Transcriptions"
- [Jazz Standards](http://www.jazzstandards.com/)

### 데이터셋 설계
- [MAESTRO Dataset](https://magenta.tensorflow.org/datasets/maestro)
- [Lakh MIDI Dataset](https://colinraffel.com/projects/lmd/)

---

다음 단계: [Phase 3: 프로토타입 개발](phase3_prototype.md)
