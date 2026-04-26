# Project: GymVoice - Local AI Workout Logger

## 1. Overview
Build an Android application that allows for hands-free workout logging. The user should be able to say: *"Benchpress set 1 reps 10 weight 100 kg"* and have the app parse this into a structured database entry.

**Constraint:** The app must run **entirely offline** using local Google-based models (Gemma-3 270M or similar) via the LiteRT (formerly TensorFlow Lite) framework. It must be optimized for mid-range/low-power hardware.

## 2. Core Architecture
- **Transcription (STT):** Local **Whisper Tiny (Quantized)** running via LiteRT.
- **NLP (Structuring):** **Gemma-3 270M (4-bit Quantized)** running via LiteRT-LM.
- **Database:** Local **SQLite** (Room Persistence Library).
- **Frontend:** Android Native (Kotlin) or Flutter.

## 3. The Local AI Pipeline
1. **Audio Capture:** Implement a "Push-to-Talk" button. Record audio in 16kHz mono.
2. **Transcription:** - Input: Raw Audio
   - Model: `whisper-tiny-en.tflite`
   - Output: Plain text string (e.g., "bench press 100 kg 10 reps")
3. **Information Extraction (The LLM):**
   - Input: Transcribed text.
   - Prompt: `Input: "{text}" | Output JSON: {"exercise": "", "set": int, "reps": int, "weight": float, "unit": "kg/lbs"}`
   - Model: `gemma-3-270m-int4.bin`
4. **Data Insertion:** Parse the JSON and insert it into the `workout_sessions` table.

## 4. Database Schema (SQLite)
- **Table: exercises** (id, name, muscle_group)
- **Table: logs** (id, session_id, exercise_id, set_number, reps, weight, unit, timestamp)

## 5. Technical Implementation Steps for Claude Code
- **Step 1:** Set up an Android project with the `google.ai.edge:writer-lite-rt-lm` dependency.
- **Step 2:** Integrate Whisper Tiny for ASR (Automatic Speech Recognition).
- **Step 3:** Implement the LiteRT-LM inference for Gemma-3. Create a strict system prompt to ensure JSON output.
- **Step 4:** Build a simple UI showing a list of logs for the current day and a large "Hold to Record" button.
- **Step 5:** Add a "Manual Correction" feature where clicking a log allows the user to edit values (in case the AI misinterprets the weight).

## 6. Performance Optimization for Low-End Devices
- Use **4-bit quantization** for all models.
- Implement **Model Offloading**: Only load the LLM into RAM when the transcription is complete to save memory.
- Use a **Hybrid Parsing Approach**: If the LLM output is malformed, fallback to a Regex pattern matcher to find numbers (reps/weight).
