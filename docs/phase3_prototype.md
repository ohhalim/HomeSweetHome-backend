# Phase 3: 프로토타입 개발 (Prototype Development)

**기간**: 2-3개월
**목표**: Charlie Parker 스타일 즉흥 연주를 생성하는 AI 모델 구축
**시간 배분**: Backend 40% : Music AI 60%

---

## 📋 Phase 3 개요

이 단계는 실제 AI 모델을 학습하고 Charlie Parker 스타일의 즉흥 연주를 생성하는 핵심 단계입니다.

### 목표
1. Music Transformer 모델 이해 및 구현
2. Charlie Parker 데이터셋으로 Fine-tuning
3. 생성 품질 평가 시스템 구축
4. Bebop 스타일 검증
5. 간단한 CLI 도구 개발

---

## 🗓️ 주차별 학습 계획

### Week 1-2: Transformer 아키텍처 이해

#### 학습 목표
- Attention mechanism 이해
- Music Transformer 논문 읽기
- 기본 Transformer 구현

#### 1. Self-Attention 구현

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
import math

class ScaledDotProductAttention(nn.Module):
    """
    Scaled Dot-Product Attention

    Attention(Q, K, V) = softmax(QK^T / sqrt(d_k))V
    """

    def __init__(self, d_k: int):
        super().__init__()
        self.d_k = d_k

    def forward(self, Q, K, V, mask=None):
        """
        Args:
            Q: Query (batch_size, n_heads, seq_len, d_k)
            K: Key (batch_size, n_heads, seq_len, d_k)
            V: Value (batch_size, n_heads, seq_len, d_v)
            mask: Attention mask (batch_size, 1, seq_len, seq_len)

        Returns:
            output: (batch_size, n_heads, seq_len, d_v)
            attention_weights: (batch_size, n_heads, seq_len, seq_len)
        """
        # Q·K^T
        scores = torch.matmul(Q, K.transpose(-2, -1))  # (bs, n_heads, seq_len, seq_len)

        # Scaling
        scores = scores / math.sqrt(self.d_k)

        # Masking (optional)
        if mask is not None:
            scores = scores.masked_fill(mask == 0, -1e9)

        # Softmax
        attention_weights = F.softmax(scores, dim=-1)

        # Attention · V
        output = torch.matmul(attention_weights, V)

        return output, attention_weights


class MultiHeadAttention(nn.Module):
    """Multi-Head Attention"""

    def __init__(self, d_model: int, n_heads: int):
        super().__init__()

        assert d_model % n_heads == 0, "d_model must be divisible by n_heads"

        self.d_model = d_model
        self.n_heads = n_heads
        self.d_k = d_model // n_heads

        # Q, K, V projection
        self.W_q = nn.Linear(d_model, d_model)
        self.W_k = nn.Linear(d_model, d_model)
        self.W_v = nn.Linear(d_model, d_model)

        # Output projection
        self.W_o = nn.Linear(d_model, d_model)

        self.attention = ScaledDotProductAttention(self.d_k)

    def forward(self, query, key, value, mask=None):
        """
        Args:
            query: (batch_size, seq_len, d_model)
            key: (batch_size, seq_len, d_model)
            value: (batch_size, seq_len, d_model)

        Returns:
            output: (batch_size, seq_len, d_model)
        """
        batch_size = query.size(0)

        # Linear projection and split into heads
        Q = self.W_q(query).view(batch_size, -1, self.n_heads, self.d_k).transpose(1, 2)
        K = self.W_k(key).view(batch_size, -1, self.n_heads, self.d_k).transpose(1, 2)
        V = self.W_v(value).view(batch_size, -1, self.n_heads, self.d_k).transpose(1, 2)

        # Self-attention
        output, attention_weights = self.attention(Q, K, V, mask)

        # Concatenate heads
        output = output.transpose(1, 2).contiguous().view(batch_size, -1, self.d_model)

        # Output projection
        output = self.W_o(output)

        return output, attention_weights


# 테스트
d_model = 512
n_heads = 8
seq_len = 100
batch_size = 4

mha = MultiHeadAttention(d_model, n_heads)

x = torch.randn(batch_size, seq_len, d_model)
output, attn = mha(x, x, x)

print(f"Input shape: {x.shape}")
print(f"Output shape: {output.shape}")
print(f"Attention weights shape: {attn.shape}")
```

#### 2. Relative Position Encoding (Music Transformer의 핵심)

```python
class RelativePositionEncoding(nn.Module):
    """
    Relative Position Encoding for Music Transformer

    "Music Transformer: Generating Music with Long-Term Structure"
    (Huang et al., 2018)
    """

    def __init__(self, d_model: int, max_len: int = 2048):
        super().__init__()

        self.d_model = d_model
        self.max_len = max_len

        # Relative position embeddings
        # Er_k: relative distance k에 대한 임베딩
        self.rel_embeddings = nn.Parameter(
            torch.randn(2 * max_len - 1, d_model)
        )

    def forward(self, length: int):
        """
        Args:
            length: sequence length

        Returns:
            relative_positions: (length, length, d_model)
        """
        # 각 위치 i, j 사이의 상대 거리 계산
        positions = torch.arange(length, device=self.rel_embeddings.device)
        relative_positions = positions.unsqueeze(0) - positions.unsqueeze(1)

        # Relative position을 임베딩 인덱스로 변환
        # -max_len+1 ~ max_len-1 범위를 0 ~ 2*max_len-2로 변환
        relative_indices = relative_positions + self.max_len - 1
        relative_indices = torch.clamp(relative_indices, 0, 2 * self.max_len - 2)

        # 임베딩 lookup
        return self.rel_embeddings[relative_indices]


class RelativeMultiHeadAttention(nn.Module):
    """
    Multi-Head Attention with Relative Position Encoding

    핵심 아이디어: Absolute position 대신 Relative position 사용
    → 긴 시퀀스에서도 위치 정보 효과적 전달
    """

    def __init__(self, d_model: int, n_heads: int, max_len: int = 2048):
        super().__init__()

        self.d_model = d_model
        self.n_heads = n_heads
        self.d_k = d_model // n_heads

        self.W_q = nn.Linear(d_model, d_model)
        self.W_k = nn.Linear(d_model, d_model)
        self.W_v = nn.Linear(d_model, d_model)
        self.W_o = nn.Linear(d_model, d_model)

        # Relative position encoding
        self.rel_pos_encoding = RelativePositionEncoding(d_model, max_len)

        # Position-aware bias
        self.u = nn.Parameter(torch.randn(n_heads, self.d_k))  # content-based
        self.v = nn.Parameter(torch.randn(n_heads, self.d_k))  # position-based

    def forward(self, x, mask=None):
        batch_size, seq_len, _ = x.size()

        # Q, K, V projection
        Q = self.W_q(x).view(batch_size, seq_len, self.n_heads, self.d_k).transpose(1, 2)
        K = self.W_k(x).view(batch_size, seq_len, self.n_heads, self.d_k).transpose(1, 2)
        V = self.W_v(x).view(batch_size, seq_len, self.n_heads, self.d_k).transpose(1, 2)

        # Relative position embeddings
        R = self.rel_pos_encoding(seq_len)  # (seq_len, seq_len, d_model)
        R = R.view(seq_len, seq_len, self.n_heads, self.d_k).permute(2, 0, 1, 3)

        # Compute attention with relative positions
        # AC term: content-based attention
        AC = torch.matmul(Q + self.u.unsqueeze(0).unsqueeze(2), K.transpose(-2, -1))

        # BD term: position-based attention
        BD = torch.matmul(Q + self.v.unsqueeze(0).unsqueeze(2), R.transpose(-2, -1))

        # Combined attention scores
        scores = (AC + BD) / math.sqrt(self.d_k)

        if mask is not None:
            scores = scores.masked_fill(mask == 0, -1e9)

        attention_weights = F.softmax(scores, dim=-1)

        # Apply attention to values
        output = torch.matmul(attention_weights, V)

        # Concatenate and project
        output = output.transpose(1, 2).contiguous().view(batch_size, seq_len, self.d_model)
        output = self.W_o(output)

        return output, attention_weights

# 테스트
rel_mha = RelativeMultiHeadAttention(d_model=512, n_heads=8)
x = torch.randn(4, 100, 512)
output, attn = rel_mha(x)

print(f"Relative MHA output: {output.shape}")
```

---

### Week 3-4: Music Transformer 구현

#### 학습 목표
- Full Music Transformer 모델 구현
- MIDI 토크나이저 개발
- 학습 파이프라인 구축

#### 1. MIDI Tokenizer

```python
from dataclasses import dataclass
from typing import List, Tuple
import pretty_midi

@dataclass
class MIDIEvent:
    """MIDI 이벤트"""
    type: str  # NOTE_ON, NOTE_OFF, TIME_SHIFT, VELOCITY
    value: int
    time: float = 0.0

class MIDITokenizer:
    """
    MIDI를 토큰 시퀀스로 변환

    Vocabulary:
    - NOTE_ON: 0-127 (128 tokens)
    - NOTE_OFF: 128-255 (128 tokens)
    - TIME_SHIFT: 256-355 (100 tokens, 각 10ms)
    - VELOCITY: 356-387 (32 tokens, 4씩 그룹)
    - SPECIAL: PAD=388, START=389, END=390
    """

    def __init__(self):
        # Token ranges
        self.NOTE_ON_OFFSET = 0
        self.NOTE_OFF_OFFSET = 128
        self.TIME_SHIFT_OFFSET = 256
        self.VELOCITY_OFFSET = 356

        # Special tokens
        self.PAD_TOKEN = 388
        self.START_TOKEN = 389
        self.END_TOKEN = 390

        self.VOCAB_SIZE = 391

        # Time quantization (10ms units)
        self.TIME_UNIT = 0.01  # 10ms
        self.MAX_TIME_SHIFT = 100  # 1초

    def encode(self, midi_file: str) -> List[int]:
        """
        MIDI 파일을 토큰 시퀀스로 변환

        Returns:
            List of token IDs
        """
        midi = pretty_midi.PrettyMIDI(midi_file)

        events = []

        # 모든 노트 수집
        for instrument in midi.instruments:
            if not instrument.is_drum:
                for note in instrument.notes:
                    events.append(MIDIEvent(
                        type='NOTE_ON',
                        value=note.pitch,
                        time=note.start
                    ))
                    events.append(MIDIEvent(
                        type='NOTE_OFF',
                        value=note.pitch,
                        time=note.end
                    ))
                    events.append(MIDIEvent(
                        type='VELOCITY',
                        value=note.velocity,
                        time=note.start
                    ))

        # 시간순 정렬
        events.sort(key=lambda e: e.time)

        # 이벤트를 토큰으로 변환
        tokens = [self.START_TOKEN]
        current_time = 0.0

        for event in events:
            # Time shift 추가
            time_delta = event.time - current_time
            if time_delta > 0:
                # 10ms 단위로 양자화
                time_steps = int(time_delta / self.TIME_UNIT)

                while time_steps > 0:
                    shift = min(time_steps, self.MAX_TIME_SHIFT)
                    tokens.append(self.TIME_SHIFT_OFFSET + shift - 1)
                    time_steps -= shift
                    current_time += shift * self.TIME_UNIT

            # 이벤트 토큰 추가
            if event.type == 'NOTE_ON':
                tokens.append(self.NOTE_ON_OFFSET + event.value)
            elif event.type == 'NOTE_OFF':
                tokens.append(self.NOTE_OFF_OFFSET + event.value)
            elif event.type == 'VELOCITY':
                # Velocity를 32 bins으로 그룹화 (0-127 → 0-31)
                velocity_bin = event.value // 4
                tokens.append(self.VELOCITY_OFFSET + velocity_bin)

        tokens.append(self.END_TOKEN)

        return tokens

    def decode(self, tokens: List[int], output_file: str):
        """
        토큰 시퀀스를 MIDI 파일로 변환

        Args:
            tokens: List of token IDs
            output_file: Output MIDI file path
        """
        midi = pretty_midi.PrettyMIDI()
        instrument = pretty_midi.Instrument(program=0)  # Acoustic Grand Piano

        current_time = 0.0
        current_velocity = 64
        note_on_times = {}  # {pitch: start_time}

        for token in tokens:
            if token == self.START_TOKEN or token == self.END_TOKEN or token == self.PAD_TOKEN:
                continue

            # Time shift
            elif self.TIME_SHIFT_OFFSET <= token < self.VELOCITY_OFFSET:
                shift_steps = token - self.TIME_SHIFT_OFFSET + 1
                current_time += shift_steps * self.TIME_UNIT

            # Velocity
            elif self.VELOCITY_OFFSET <= token < self.PAD_TOKEN:
                velocity_bin = token - self.VELOCITY_OFFSET
                current_velocity = velocity_bin * 4 + 2  # bin 중앙값

            # Note ON
            elif self.NOTE_ON_OFFSET <= token < self.NOTE_OFF_OFFSET:
                pitch = token - self.NOTE_ON_OFFSET
                note_on_times[pitch] = current_time

            # Note OFF
            elif self.NOTE_OFF_OFFSET <= token < self.TIME_SHIFT_OFFSET:
                pitch = token - self.NOTE_OFF_OFFSET

                if pitch in note_on_times:
                    start_time = note_on_times[pitch]
                    note = pretty_midi.Note(
                        velocity=current_velocity,
                        pitch=pitch,
                        start=start_time,
                        end=current_time
                    )
                    instrument.notes.append(note)
                    del note_on_times[pitch]

        midi.instruments.append(instrument)
        midi.write(output_file)

# 사용 예시
tokenizer = MIDITokenizer()

# Encoding
tokens = tokenizer.encode("charlie_parker_dataset/augmented/train/ornithology.mid")
print(f"Encoded to {len(tokens)} tokens")
print(f"Sample tokens: {tokens[:20]}")

# Decoding
tokenizer.decode(tokens, "reconstructed.mid")
print("Decoded to MIDI file")
```

#### 2. Music Transformer 모델

```python
class TransformerBlock(nn.Module):
    """Transformer Encoder Block"""

    def __init__(self, d_model: int, n_heads: int, d_ff: int, dropout: float = 0.1):
        super().__init__()

        # Multi-head attention
        self.attention = RelativeMultiHeadAttention(d_model, n_heads)

        # Feed-forward network
        self.ffn = nn.Sequential(
            nn.Linear(d_model, d_ff),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(d_ff, d_model)
        )

        # Layer normalization
        self.norm1 = nn.LayerNorm(d_model)
        self.norm2 = nn.LayerNorm(d_model)

        self.dropout = nn.Dropout(dropout)

    def forward(self, x, mask=None):
        # Multi-head attention with residual connection
        attn_output, _ = self.attention(self.norm1(x), mask)
        x = x + self.dropout(attn_output)

        # Feed-forward with residual connection
        ffn_output = self.ffn(self.norm2(x))
        x = x + self.dropout(ffn_output)

        return x


class MusicTransformer(nn.Module):
    """
    Music Transformer for Charlie Parker Style Generation

    Based on: "Music Transformer: Generating Music with Long-Term Structure"
    """

    def __init__(
        self,
        vocab_size: int,
        d_model: int = 512,
        n_heads: int = 8,
        n_layers: int = 6,
        d_ff: int = 2048,
        max_seq_len: int = 2048,
        dropout: float = 0.1
    ):
        super().__init__()

        self.d_model = d_model
        self.vocab_size = vocab_size

        # Token embedding
        self.token_embedding = nn.Embedding(vocab_size, d_model)

        # Transformer blocks
        self.blocks = nn.ModuleList([
            TransformerBlock(d_model, n_heads, d_ff, dropout)
            for _ in range(n_layers)
        ])

        # Output projection
        self.output_projection = nn.Linear(d_model, vocab_size)

        self.dropout = nn.Dropout(dropout)

    def forward(self, x, mask=None):
        """
        Args:
            x: (batch_size, seq_len) - token IDs
            mask: (batch_size, 1, seq_len, seq_len) - attention mask

        Returns:
            logits: (batch_size, seq_len, vocab_size)
        """
        # Token embedding
        x = self.token_embedding(x) * math.sqrt(self.d_model)
        x = self.dropout(x)

        # Transformer blocks
        for block in self.blocks:
            x = block(x, mask)

        # Output projection
        logits = self.output_projection(x)

        return logits

    def generate(
        self,
        start_tokens: torch.Tensor,
        max_length: int = 1000,
        temperature: float = 1.0,
        top_k: int = 0,
        top_p: float = 0.9
    ):
        """
        자기회귀적 생성 (Autoregressive Generation)

        Args:
            start_tokens: (batch_size, start_len)
            max_length: 최대 생성 길이
            temperature: 샘플링 온도 (높을수록 다양함)
            top_k: Top-K 샘플링
            top_p: Nucleus 샘플링 (Top-P)

        Returns:
            generated: (batch_size, max_length)
        """
        self.eval()
        device = start_tokens.device

        generated = start_tokens

        with torch.no_grad():
            for _ in range(max_length - start_tokens.size(1)):
                # Forward pass
                logits = self.forward(generated)  # (batch, seq_len, vocab_size)

                # 마지막 토큰의 logits
                next_token_logits = logits[:, -1, :] / temperature

                # Top-K filtering
                if top_k > 0:
                    indices_to_remove = next_token_logits < torch.topk(next_token_logits, top_k)[0][..., -1, None]
                    next_token_logits[indices_to_remove] = -float('Inf')

                # Top-P (nucleus) filtering
                if top_p < 1.0:
                    sorted_logits, sorted_indices = torch.sort(next_token_logits, descending=True)
                    cumulative_probs = torch.cumsum(F.softmax(sorted_logits, dim=-1), dim=-1)

                    # Remove tokens with cumulative probability above the threshold
                    sorted_indices_to_remove = cumulative_probs > top_p
                    sorted_indices_to_remove[..., 1:] = sorted_indices_to_remove[..., :-1].clone()
                    sorted_indices_to_remove[..., 0] = 0

                    indices_to_remove = sorted_indices_to_remove.scatter(1, sorted_indices, sorted_indices_to_remove)
                    next_token_logits[indices_to_remove] = -float('Inf')

                # Sample
                probs = F.softmax(next_token_logits, dim=-1)
                next_token = torch.multinomial(probs, num_samples=1)

                # Append
                generated = torch.cat([generated, next_token], dim=1)

                # Stop if END_TOKEN
                if (next_token == 390).all():  # END_TOKEN
                    break

        return generated


# 모델 생성
model = MusicTransformer(
    vocab_size=391,
    d_model=512,
    n_heads=8,
    n_layers=6,
    d_ff=2048,
    max_seq_len=2048,
    dropout=0.1
)

print(f"Model parameters: {sum(p.numel() for p in model.parameters()):,}")

# 테스트
batch_size = 4
seq_len = 100

x = torch.randint(0, 391, (batch_size, seq_len))
logits = model(x)

print(f"Input: {x.shape}")
print(f"Output logits: {logits.shape}")

# Generate
start_tokens = torch.tensor([[389]])  # START_TOKEN
generated = model.generate(start_tokens, max_length=200, temperature=1.0)
print(f"Generated: {generated.shape}")
print(f"Sample: {generated[0, :20]}")
```

---

### Week 5-6: 모델 학습

#### 학습 목표
- Google Colab에서 학습 환경 구축
- 학습 루프 구현
- Checkpoint 관리

#### 1. 학습 스크립트

```python
import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torch.optim import Adam
from torch.optim.lr_scheduler import CosineAnnealingLR
import wandb  # Weights & Biases for experiment tracking
from tqdm import tqdm
import os

class MusicTransformerTrainer:
    """Music Transformer 학습 클래스"""

    def __init__(
        self,
        model: MusicTransformer,
        train_loader: DataLoader,
        val_loader: DataLoader,
        device: str = "cuda",
        lr: float = 1e-4,
        weight_decay: float = 0.01,
        warmup_steps: int = 4000,
        max_epochs: int = 100,
        checkpoint_dir: str = "checkpoints"
    ):
        self.model = model.to(device)
        self.train_loader = train_loader
        self.val_loader = val_loader
        self.device = device

        # Optimizer
        self.optimizer = Adam(
            model.parameters(),
            lr=lr,
            betas=(0.9, 0.98),
            eps=1e-9,
            weight_decay=weight_decay
        )

        # Learning rate scheduler (with warmup)
        self.scheduler = CosineAnnealingLR(
            self.optimizer,
            T_max=max_epochs,
            eta_min=1e-6
        )

        self.warmup_steps = warmup_steps
        self.max_epochs = max_epochs

        # Loss function
        self.criterion = nn.CrossEntropyLoss(ignore_index=388)  # PAD_TOKEN

        # Checkpoint
        self.checkpoint_dir = checkpoint_dir
        os.makedirs(checkpoint_dir, exist_ok=True)

        self.best_val_loss = float('inf')
        self.global_step = 0

    def train_epoch(self, epoch):
        """한 에폭 학습"""
        self.model.train()

        total_loss = 0
        progress_bar = tqdm(self.train_loader, desc=f"Epoch {epoch}")

        for batch_idx, (inputs, targets) in enumerate(progress_bar):
            inputs = inputs.to(self.device)  # (batch, seq_len)
            targets = targets.to(self.device)  # (batch, seq_len)

            # Forward
            logits = self.model(inputs)  # (batch, seq_len, vocab_size)

            # Loss
            loss = self.criterion(
                logits.view(-1, logits.size(-1)),
                targets.view(-1)
            )

            # Backward
            self.optimizer.zero_grad()
            loss.backward()

            # Gradient clipping
            torch.nn.utils.clip_grad_norm_(self.model.parameters(), max_norm=1.0)

            # Optimizer step
            self.optimizer.step()

            # Warmup learning rate
            if self.global_step < self.warmup_steps:
                lr_scale = min(1.0, float(self.global_step + 1) / self.warmup_steps)
                for pg in self.optimizer.param_groups:
                    pg['lr'] = lr_scale * 1e-4

            # Logging
            total_loss += loss.item()
            self.global_step += 1

            progress_bar.set_postfix({
                'loss': loss.item(),
                'lr': self.optimizer.param_groups[0]['lr']
            })

            # Log to wandb
            if self.global_step % 10 == 0:
                wandb.log({
                    'train/loss': loss.item(),
                    'train/lr': self.optimizer.param_groups[0]['lr'],
                    'train/step': self.global_step
                })

        avg_loss = total_loss / len(self.train_loader)
        return avg_loss

    @torch.no_grad()
    def validate(self):
        """검증"""
        self.model.eval()

        total_loss = 0

        for inputs, targets in tqdm(self.val_loader, desc="Validation"):
            inputs = inputs.to(self.device)
            targets = targets.to(self.device)

            logits = self.model(inputs)

            loss = self.criterion(
                logits.view(-1, logits.size(-1)),
                targets.view(-1)
            )

            total_loss += loss.item()

        avg_loss = total_loss / len(self.val_loader)
        return avg_loss

    def save_checkpoint(self, epoch, val_loss, is_best=False):
        """체크포인트 저장"""
        checkpoint = {
            'epoch': epoch,
            'model_state_dict': self.model.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
            'scheduler_state_dict': self.scheduler.state_dict(),
            'val_loss': val_loss,
            'global_step': self.global_step
        }

        # 정기 체크포인트
        checkpoint_path = os.path.join(
            self.checkpoint_dir,
            f'checkpoint_epoch_{epoch}.pt'
        )
        torch.save(checkpoint, checkpoint_path)

        # Best 모델
        if is_best:
            best_path = os.path.join(self.checkpoint_dir, 'best_model.pt')
            torch.save(checkpoint, best_path)
            print(f"✓ Best model saved (val_loss: {val_loss:.4f})")

    def train(self):
        """전체 학습 루프"""
        print("Starting training...")
        print(f"Device: {self.device}")
        print(f"Model parameters: {sum(p.numel() for p in self.model.parameters()):,}")

        for epoch in range(1, self.max_epochs + 1):
            print(f"\n{'='*60}")
            print(f"Epoch {epoch}/{self.max_epochs}")
            print(f"{'='*60}")

            # Train
            train_loss = self.train_epoch(epoch)

            # Validate
            val_loss = self.validate()

            # Learning rate scheduling
            self.scheduler.step()

            # Logging
            print(f"\nEpoch {epoch} Summary:")
            print(f"  Train Loss: {train_loss:.4f}")
            print(f"  Val Loss: {val_loss:.4f}")

            wandb.log({
                'epoch': epoch,
                'train/epoch_loss': train_loss,
                'val/epoch_loss': val_loss
            })

            # Save checkpoint
            is_best = val_loss < self.best_val_loss
            if is_best:
                self.best_val_loss = val_loss

            self.save_checkpoint(epoch, val_loss, is_best)

        print("\n✓ Training completed!")
        print(f"Best validation loss: {self.best_val_loss:.4f}")


# Google Colab 학습 스크립트
"""
Google Colab에서 실행:

!pip install wandb torch torchvision torchaudio
!pip install pretty_midi music21

# Weights & Biases 로그인
import wandb
wandb.login()

# Dataset 준비 (Google Drive에서 로드)
from google.colab import drive
drive.mount('/content/drive')

# 데이터셋 경로
dataset_path = '/content/drive/MyDrive/charlie_parker_dataset'

# DataLoader 생성
train_loader, val_loader, test_loader = create_dataloaders(
    metadata_file=f'{dataset_path}/metadata.json',
    base_dir=dataset_path,
    batch_size=32
)

# 모델 생성
model = MusicTransformer(
    vocab_size=391,
    d_model=512,
    n_heads=8,
    n_layers=6,
    d_ff=2048
)

# W&B 초기화
wandb.init(
    project="charlie-parker-transformer",
    config={
        "d_model": 512,
        "n_heads": 8,
        "n_layers": 6,
        "batch_size": 32,
        "lr": 1e-4
    }
)

# 학습
trainer = MusicTransformerTrainer(
    model=model,
    train_loader=train_loader,
    val_loader=val_loader,
    device="cuda" if torch.cuda.is_available() else "cpu",
    max_epochs=50
)

trainer.train()

# Best 모델 저장
!cp checkpoints/best_model.pt /content/drive/MyDrive/charlie_parker_best.pt
"""
```

---

### Week 7-8: 생성 품질 평가 및 CLI 도구

#### 학습 목표
- 생성된 MIDI 품질 평가
- Bebop 스타일 검증
- CLI 도구 개발

#### 1. 생성 품질 평가

```python
import pretty_midi
import numpy as np
from typing import Dict, List

class BebopStyleEvaluator:
    """
    Bebop 스타일 평가기

    Charlie Parker 특징:
    - 빠른 템포 (180-250 BPM)
    - 크로매틱 접근음
    - 복잡한 리듬
    - 확장 코드 톤
    """

    def __init__(self):
        # Bebop scale notes (C bebop dominant)
        self.bebop_scale = [0, 2, 4, 5, 7, 9, 10, 11]  # C, D, E, F, G, A, Bb, B

    def evaluate(self, midi_file: str) -> Dict[str, float]:
        """MIDI 파일 평가"""
        midi = pretty_midi.PrettyMIDI(midi_file)

        notes = []
        for instrument in midi.instruments:
            if not instrument.is_drum:
                notes.extend(instrument.notes)

        notes.sort(key=lambda n: n.start)

        if len(notes) < 10:
            return {'score': 0.0, 'error': 'Too few notes'}

        # 1. 템포 체크
        tempo_score = self._check_tempo(midi)

        # 2. 리듬 복잡도
        rhythm_score = self._check_rhythm_complexity(notes)

        # 3. 멜로디 움직임 (음정 간격)
        melodic_score = self._check_melodic_movement(notes)

        # 4. Bebop scale 사용 빈도
        scale_score = self._check_bebop_scale(notes)

        # 5. 크로매틱 접근음
        chromatic_score = self._check_chromatic_approach(notes)

        # 종합 점수 (가중 평균)
        total_score = (
            tempo_score * 0.2 +
            rhythm_score * 0.25 +
            melodic_score * 0.2 +
            scale_score * 0.2 +
            chromatic_score * 0.15
        )

        return {
            'total_score': total_score,
            'tempo_score': tempo_score,
            'rhythm_score': rhythm_score,
            'melodic_score': melodic_score,
            'scale_score': scale_score,
            'chromatic_score': chromatic_score
        }

    def _check_tempo(self, midi: pretty_midi.PrettyMIDI) -> float:
        """템포 체크 (Bebop: 180-250 BPM)"""
        tempo_changes = midi.get_tempo_changes()
        avg_tempo = np.mean(tempo_changes[1]) if len(tempo_changes[1]) > 0 else 120

        # 180-250 범위면 만점
        if 180 <= avg_tempo <= 250:
            return 1.0
        elif 150 <= avg_tempo < 180:
            return 0.7
        elif 250 < avg_tempo <= 280:
            return 0.7
        else:
            return 0.3

    def _check_rhythm_complexity(self, notes: List[pretty_midi.Note]) -> float:
        """리듬 복잡도 (다양한 음표 길이)"""
        durations = [note.end - note.start for note in notes]

        # 표준편차가 클수록 리듬이 다양함
        std_duration = np.std(durations)

        # 정규화 (0.0 ~ 1.0)
        score = min(1.0, std_duration / 0.2)

        return score

    def _check_melodic_movement(self, notes: List[pretty_midi.Note]) -> float:
        """멜로디 움직임 (적절한 음정 간격)"""
        intervals = []

        for i in range(1, len(notes)):
            interval = abs(notes[i].pitch - notes[i-1].pitch)
            intervals.append(interval)

        if not intervals:
            return 0.0

        # Bebop은 주로 2-7 반음 간격 움직임
        good_intervals = sum(1 for iv in intervals if 2 <= iv <= 7)
        score = good_intervals / len(intervals)

        return score

    def _check_bebop_scale(self, notes: List[pretty_midi.Note]) -> float:
        """Bebop scale 사용 빈도"""
        pitch_classes = [note.pitch % 12 for note in notes]

        bebop_notes = sum(1 for pc in pitch_classes if pc in self.bebop_scale)
        score = bebop_notes / len(notes)

        return score

    def _check_chromatic_approach(self, notes: List[pretty_midi.Note]) -> float:
        """크로매틱 접근음 사용 (반음 접근)"""
        chromatic_approaches = 0

        for i in range(2, len(notes)):
            # 반음 위 또는 아래에서 접근
            interval = notes[i].pitch - notes[i-1].pitch

            if abs(interval) == 1:  # 반음
                chromatic_approaches += 1

        score = chromatic_approaches / max(len(notes) - 2, 1)

        # 정규화 (Bebop은 약 20-40% 크로매틱)
        score = min(1.0, score / 0.3)

        return score

# 사용 예시
evaluator = BebopStyleEvaluator()

# 생성된 MIDI 평가
scores = evaluator.evaluate("generated_samples/sample_001.mid")

print("Bebop Style Evaluation:")
print(f"  Total Score: {scores['total_score']:.2f}")
print(f"  Tempo: {scores['tempo_score']:.2f}")
print(f"  Rhythm: {scores['rhythm_score']:.2f}")
print(f"  Melody: {scores['melodic_score']:.2f}")
print(f"  Scale: {scores['scale_score']:.2f}")
print(f"  Chromatic: {scores['chromatic_score']:.2f}")
```

#### 2. CLI 도구

```python
import argparse
import torch
from pathlib import Path

class CharlieParkerGenerator:
    """Charlie Parker 스타일 생성기 CLI"""

    def __init__(self, model_path: str, device: str = "cuda"):
        self.device = device

        # 모델 로드
        print(f"Loading model from {model_path}...")
        checkpoint = torch.load(model_path, map_location=device)

        self.model = MusicTransformer(vocab_size=391)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        self.model.to(device)
        self.model.eval()

        self.tokenizer = MIDITokenizer()
        self.evaluator = BebopStyleEvaluator()

        print("✓ Model loaded successfully")

    def generate(
        self,
        output_file: str,
        max_length: int = 1000,
        temperature: float = 1.0,
        top_k: int = 40,
        top_p: float = 0.9
    ):
        """즉흥 연주 생성"""
        print(f"\nGenerating Charlie Parker style improvisation...")
        print(f"  Max length: {max_length}")
        print(f"  Temperature: {temperature}")
        print(f"  Top-K: {top_k}")
        print(f"  Top-P: {top_p}")

        # 시작 토큰
        start_tokens = torch.tensor([[self.tokenizer.START_TOKEN]], device=self.device)

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

        print(f"✓ Generated: {output_file}")

        # 평가
        scores = self.evaluator.evaluate(output_file)
        print(f"\nBebop Style Score: {scores['total_score']:.2f}/1.00")

        return output_file, scores

def main():
    parser = argparse.ArgumentParser(
        description="Charlie Parker Style Improvisation Generator"
    )

    parser.add_argument(
        "--model",
        type=str,
        required=True,
        help="Path to trained model checkpoint"
    )

    parser.add_argument(
        "--output",
        type=str,
        default="generated.mid",
        help="Output MIDI file path"
    )

    parser.add_argument(
        "--length",
        type=int,
        default=1000,
        help="Maximum sequence length"
    )

    parser.add_argument(
        "--temperature",
        type=float,
        default=1.0,
        help="Sampling temperature (higher = more random)"
    )

    parser.add_argument(
        "--top-k",
        type=int,
        default=40,
        help="Top-K sampling"
    )

    parser.add_argument(
        "--top-p",
        type=float,
        default=0.9,
        help="Nucleus (top-p) sampling"
    )

    parser.add_argument(
        "--device",
        type=str,
        default="cuda" if torch.cuda.is_available() else "cpu",
        help="Device (cuda/cpu)"
    )

    args = parser.parse_args()

    # 생성기 초기화
    generator = CharlieParkerGenerator(
        model_path=args.model,
        device=args.device
    )

    # 생성
    output_file, scores = generator.generate(
        output_file=args.output,
        max_length=args.length,
        temperature=args.temperature,
        top_k=args.top_k,
        top_p=args.top_p
    )

    print("\n✓ Generation completed!")

if __name__ == "__main__":
    main()

# 사용 예시:
# python generate.py --model checkpoints/best_model.pt --output ornithology_v2.mid --temperature 0.9
```

---

## 📊 Phase 3 평가 기준

### 모델 구현 (30%)
- [ ] Music Transformer 완전 구현
- [ ] Relative Position Encoding 적용
- [ ] 파라미터 수: 50M 이상

### 학습 (40%)
- [ ] 50 에폭 이상 학습
- [ ] Validation loss 안정화
- [ ] Checkpoint 저장 시스템

### 생성 품질 (30%)
- [ ] Bebop 스타일 점수 0.7 이상
- [ ] 멜로디 일관성
- [ ] 리듬 다양성

---

## 🎯 Phase 3 완료 후 산출물

1. **학습된 모델**
   - `best_model.pt` (50M+ parameters)
   - 학습 로그 (W&B)

2. **CLI 도구**
   - `generate.py` - 즉흥 연주 생성
   - `evaluate.py` - 품질 평가

3. **생성 샘플**
   - 10개 이상 MIDI 파일
   - 평가 보고서

---

다음 단계: [Phase 4: 서비스 개발](phase4_service.md)
