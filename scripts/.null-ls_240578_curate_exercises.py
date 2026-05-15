#!/usr/bin/env python3
"""
Curate free-exercise-db for GymVoice.
Filters to gym-relevant exercises, downloads 0.jpg for each,
writes exercises_seed.json to app/src/main/assets/.

Run from project root:
  python3 scripts/curate_exercises.py
"""

import json
import os
import sys
import urllib.request

BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main"
EXERCISES_URL = f"{BASE_URL}/dist/exercises.json"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "app/src/main/assets")
IMAGES_DIR = os.path.join(OUTPUT_DIR, "exercises")
SEED_FILE = os.path.join(OUTPUT_DIR, "exercises_seed.json")

INCLUDE_CATEGORIES = {"strength", "powerlifting", "olympic weightlifting", "strongman"}

INCLUDE_EQUIPMENT = {
    "barbell", "dumbbell", "cable", "machine",
    "body only", "kettlebells", "bands", "e-z curl bar",
}

MUSCLE_GROUP_MAP = {
    "chest": "Chest",
    "biceps": "Arms",
    "triceps": "Arms",
    "forearms": "Arms",
    "shoulders": "Shoulders",
    "traps": "Back",
    "lats": "Back",
    "middle back": "Back",
    "lower back": "Back",
    "neck": "Back",
    "quadriceps": "Legs",
    "hamstrings": "Legs",
    "glutes": "Legs",
    "calves": "Legs",
    "adductors": "Legs",
    "abductors": "Legs",
    "abdominals": "Core",
}


def download_file(url, dest):
    try:
        urllib.request.urlretrieve(url, dest)
        return True
    except Exception as e:
        print(f"  SKIP {url}: {e}", file=sys.stderr)
        return False


def main():
    os.makedirs(IMAGES_DIR, exist_ok=True)

    print("Downloading exercises.json...")
    with urllib.request.urlopen(EXERCISES_URL) as resp:
        exercises = json.loads(resp.read())
    print(f"Total: {len(exercises)} exercises")

    filtered = [
        ex for ex in exercises
        if ex.get("category", "") in INCLUDE_CATEGORIES
        and (ex.get("equipment") or "") in INCLUDE_EQUIPMENT
        and ex.get("primaryMuscles")
    ]
    print(f"After filter: {len(filtered)} exercises")

    curated = []
    for i, ex in enumerate(filtered, 1):
        ex_id = ex["id"]
        primary_muscle = ex["primaryMuscles"][0]
        muscle_group = MUSCLE_GROUP_MAP.get(primary_muscle.lower(), primary_muscle.title())
        image_filename = f"{ex_id}.jpg"
        image_dest = os.path.join(IMAGES_DIR, image_filename)

        if not os.path.exists(image_dest):
            image_url = f"{BASE_URL}/exercises/{ex_id}/0.jpg"
            ok = download_file(image_url, image_dest)
            if not ok:
                image_filename = ""
            else:
                print(f"  [{i}/{len(filtered)}] {ex['name']}")
        else:
            print(f"  [{i}/{len(filtered)}] {ex['name']} (cached)")

        curated.append({
            "sourceId": ex_id,
            "name": ex["name"],
            "muscleGroup": muscle_group,
            "equipment": ex.get("equipment", ""),
            "level": ex.get("level", ""),
            "imageName": image_filename,
        })

    with open(SEED_FILE, "w") as f:
        json.dump(curated, f, indent=2)

    total_image_size = sum(
        os.path.getsize(os.path.join(IMAGES_DIR, e["imageName"]))
        for e in curated if e["imageName"]
    )
    print(f"\nDone: {len(curated)} exercises")
    print(f"Seed file: {SEED_FILE}")
    print(f"Images dir: {IMAGES_DIR}")
    print(f"Total image size: {total_image_size / 1_048_576:.1f} MB")


if __name__ == "__main__":
    main()
