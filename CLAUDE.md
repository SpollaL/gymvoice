# GymVoice

Offline Android workout logger. Voice input → Whisper STT → Gemma NLP → Room DB. No internet required.

## Build

```bash
./bootstrap.sh          # first time only — generates Gradle wrapper
./gradlew assembleDebug # build APK
./gradlew installDebug  # build + install on connected device/emulator
```

## Lint & Format

```bash
# Format all Kotlin files (auto-fix)
./gradlew ktlintFormat

# Check formatting (no fix, fail on violation)
./gradlew ktlintCheck

# Static analysis
./gradlew detekt

# Run everything before committing
./gradlew ktlintFormat detekt assembleDebug
```

Tools: **ktlint 12.1.0** (formatting) + **detekt 1.23.6** (static analysis).
Config: `.editorconfig` (line length 120, 4-space indent), `detekt.yml`.

## Architecture

```
MainActivity
└── MainViewModel
    ├── AudioRecorder       — 16kHz mono PCM via AudioRecord
    ├── WhisperInference    — LiteRT interpreter, whisper-tiny-en.tflite
    ├── GemmaInference      — MediaPipe LLM API, gemma-3-270m-it-int4.bin
    ├── WorkoutParser       — JSON parse from Gemma + regex fallback
    └── AppDatabase (Room)
        └── WorkoutLogDao
```

## Model Files (required before app runs)

### Whisper Tiny (STT)

Converts `openai/whisper-tiny.en` PyTorch model → TFLite via `ai-edge-torch`.
Output lands at `app/src/main/assets/models/whisper-tiny-en.tflite`.

```bash
pip install -r scripts/requirements-convert.txt
python scripts/convert_whisper.py
```

Model input:  `float32[1, 80, 3000]` — mel spectrogram (30s audio @ 16kHz)
Model output: `int32[1, 448]`        — Whisper BPE token IDs

### Gemma 3 1B (NLP)

```bash
kaggle models instances versions download keras/gemma3/keras/gemma3_1b/3
adb push <downloaded_file> /data/local/tmp/gemma3_1b.bin
```

Requires Kaggle account + license acceptance at https://www.kaggle.com/models/google/gemma-3

## Key Dependencies

| Dep | Purpose |
|-----|---------|
| `com.google.ai.edge.litert:litert:1.0.1` | Whisper TFLite inference |
| `com.google.mediapipe:tasks-genai:0.10.14` | Gemma LLM inference |
| `androidx.room:room-runtime:2.6.1` | SQLite via Room |

## Package Structure

```
com.gymvoice/
├── audio/       AudioRecorder
├── data/        WorkoutLog, WorkoutLogDao, AppDatabase
├── ml/          WhisperInference, GemmaInference, WorkoutParser
├── ui/          MainViewModel, WorkoutLogAdapter
└── MainActivity
```

## Notes

- Gemma loads on-demand per recording (model offloading) to save RAM
- WorkoutParser tries JSON first, falls back to regex if LLM output is malformed
- WhisperInference.decodeTokens() is a stub — needs real BPE tokenizer
- Model files are gitignored; push to device separately via adb
