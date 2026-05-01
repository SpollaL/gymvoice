# GymVoice

Offline Android workout logger. Voice input → Android STT → Gemma NLP → Room DB. No internet required.

## Build

Always use the Makefile — do not call `./gradlew` directly.

```bash
./bootstrap.sh   # first time only — generates Gradle wrapper
make build       # assemble APK
make flash       # build + install on connected device/emulator
make push-model  # adb push Gemma model to device
make pre-commit  # format + lint + build (run before every commit)
make clean       # gradle clean
```

## Lint & Format

```bash
make format  # ktlint autofix
make check   # ktlint verify (no fix)
make lint    # detekt static analysis
```

Tools: **ktlint 12.1.0** (formatting) + **detekt 1.23.6** (static analysis).
Config: `.editorconfig` (line length 120, 4-space indent), `detekt.yml`.

## Architecture

```
MainActivity
└── MainViewModel
    ├── AudioRecorder       — 16kHz mono PCM via AudioRecord
    ├── SpeechRecognizerSTT — Android SpeechRecognizer (on-device)
    ├── GemmaInference      — LiteRT-LM, gemma-3-270m-it-int8.litertlm
    ├── WorkoutParser       — JSON parse from Gemma + regex fallback
    └── AppDatabase (Room)
        └── WorkoutLogDao
```

## Model Files (required before app runs)

### Gemma 3 270M (NLP)

```bash
adb push gemma-3-270m-it-int8.litertlm /data/local/tmp/gemma-3-270m-it-int8.litertlm
```

Model path expected by app: `/data/local/tmp/gemma-3-270m-it-int8.litertlm`

## Key Dependencies

| Dep | Purpose |
|-----|---------|
| `com.google.ai.edge.litertlm:litertlm-android:0.10.2` | Gemma LiteRT-LM inference |
| `androidx.room:room-runtime:2.6.1` | SQLite via Room |

## Package Structure

```
com.gymvoice/
├── audio/       AudioRecorder, SpeechRecognizerSTT
├── data/        WorkoutLog, WorkoutLogDao, AppDatabase
├── ml/          GemmaInference, WorkoutParser
├── ui/          MainViewModel, WorkoutLogAdapter, ProgressViewModel
└── MainActivity
```

## Notes

- Gemma loads on-demand per recording (model offloading) to save RAM
- WorkoutParser tries JSON first, falls back to regex if LLM output is malformed
- Model file is gitignored; push to device separately via adb
