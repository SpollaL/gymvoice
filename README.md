# GymVoice

Offline Android workout logger. Speak your set, GymVoice logs it — no internet, no cloud, no subscriptions.

```
"bench press 3 sets 8 reps 80 kilos"  →  ✓ Logged
```

## How it works

```
Mic → Whisper (STT) → Gemma 3 (NLP) → Room DB
```

All inference runs on-device. Your data stays on your phone.

## Features

- **Voice logging** — speak naturally, app parses exercise, sets, reps, weight
- **Manual entry** — tap to log without speaking
- **Clone** — repeat a set in one tap from the edit dialog
- **Progress tab** — sparkline chart, PR tracking, weight/reps/volume modes
- **Correction learning** — fix a misheard name once, remembered forever
- **Past-date logging** — log missed sessions via calendar picker

## Requirements

- Android 10+
- ~500 MB free storage (models)
- Whisper Tiny TFLite model
- Gemma 3 270M model (pushed via adb)

## Setup

```bash
# 1. Generate Gradle wrapper (first time only)
./bootstrap.sh

# 2. Convert Whisper model
pip install -r scripts/requirements-convert.txt
python scripts/convert_whisper.py

# 3. Push Gemma model to device
adb push gemma3_270m_int4.bin /data/local/tmp/

# 4. Build and install
./gradlew installDebug
```

See [CLAUDE.md](CLAUDE.md) for full model setup and architecture details.

## Tech stack

| Layer | Library |
|-------|---------|
| STT | Whisper Tiny via LiteRT 1.0 |
| NLP | Gemma 3 270M via MediaPipe |
| DB | Room 2.6 |
| UI | ViewBinding, RecyclerView, Material 3 |
| Theme | Catppuccin Mocha |
