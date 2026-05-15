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

# Only "strength" — powerlifting/olympic/strongman have odd primary-muscle assignments
# (e.g. bench press variants classified as triceps → appear in Arms)
INCLUDE_CATEGORIES = {"strength"}

INCLUDE_EQUIPMENT = {
    "barbell", "dumbbell", "cable", "machine",
    "body only", "kettlebells", "e-z curl bar",
}

# Machines always get reserved slots so they can't be squeezed out
MACHINE_MAX_PER_GROUP = 8
# Non-machine cap per group (applied after machines are placed)
OTHER_MAX_PER_GROUP = 22

# These exercises are always included regardless of cap (name substring, case-insensitive)
MUST_INCLUDE_PATTERNS = [
    "lat pulldown",
    "barbell curl",
    "dumbbell curl",
    "cable curl",
    "preacher curl",
    "hammer curl",
    "concentration curl",
    "leg press",
    "leg extension",
    "leg curl",
    "seated cable row",
    "cable row",
    "face pull",
    "tricep pushdown",
    "cable pushdown",
    "romanian deadlift",
    "hip thrust",
    "pull-up",
    "pullup",
    "chin-up",
    "chinup",
    "dip",
    "chest fly",
    "cable fly",
    "lateral raise",
    "front raise",
    "shrug",
    "calf raise",
    "lunge",
    "step-up",
    "glute bridge",
]

EQUIPMENT_PRIORITY = ["barbell", "dumbbell", "cable", "machine", "body only", "e-z curl bar", "kettlebells"]
LEVEL_PRIORITY = ["beginner", "intermediate", "expert"]

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


def sort_key(ex):
    lvl = LEVEL_PRIORITY.index(ex.get("level", "intermediate")) if ex.get("level") in LEVEL_PRIORITY else 99
    equip = EQUIPMENT_PRIORITY.index(ex.get("equipment", "")) if ex.get("equipment") in EQUIPMENT_PRIORITY else 99
    return (lvl, equip, ex["name"])


def is_must_include(name):
    lower = name.lower()
    return any(p in lower for p in MUST_INCLUDE_PATTERNS)


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

    filtered.sort(key=sort_key)

    # Tag each exercise with its muscle group
    for ex in filtered:
        primary = ex["primaryMuscles"][0]
        ex["_group"] = MUSCLE_GROUP_MAP.get(primary.lower(), primary.title())

    # Pass 1: must-include exercises (always in, don't count against cap)
    must_set = set()
    must_list = []
    for ex in filtered:
        if is_must_include(ex["name"]):
            must_set.add(ex["id"])
            must_list.append(ex)
    print(f"Must-include: {len(must_list)}")

    # Pass 2: machine exercises not already in must-include (reserved quota)
    machine_counts = {}
    machine_list = []
    for ex in filtered:
        if ex["id"] in must_set:
            continue
        if ex.get("equipment") == "machine":
            group = ex["_group"]
            if machine_counts.get(group, 0) < MACHINE_MAX_PER_GROUP:
                machine_list.append(ex)
                machine_counts[group] = machine_counts.get(group, 0) + 1
    print(f"Machine exercises: {len(machine_list)}")

    # Pass 3: remaining non-machine exercises up to OTHER_MAX_PER_GROUP
    selected_ids = must_set | {ex["id"] for ex in machine_list}
    other_counts = {}
    other_list = []
    for ex in filtered:
        if ex["id"] in selected_ids:
            continue
        if ex.get("equipment") == "machine":
            continue
        group = ex["_group"]
        if other_counts.get(group, 0) < OTHER_MAX_PER_GROUP:
            other_list.append(ex)
            other_counts[group] = other_counts.get(group, 0) + 1
    print(f"Other exercises: {len(other_list)}")

    combined = must_list + machine_list + other_list
    combined.sort(key=sort_key)
    print(f"Total after cap: {len(combined)}")

    curated = []
    for i, ex in enumerate(combined, 1):
        ex_id = ex["id"]
        muscle_group = ex["_group"]
        image_filename = f"{ex_id}.jpg"
        image_dest = os.path.join(IMAGES_DIR, image_filename)

        if not os.path.exists(image_dest):
            image_url = f"{BASE_URL}/exercises/{ex_id}/0.jpg"
            ok = download_file(image_url, image_dest)
            if not ok:
                image_filename = ""
            else:
                print(f"  [{i}/{len(combined)}] {ex['name']}")
        else:
            print(f"  [{i}/{len(combined)}] {ex['name']} (cached)")

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
    print(f"\nDone: {len(curated)} exercises, {total_image_size / 1_048_576:.1f} MB images")

    by_group = {}
    for e in curated:
        by_group.setdefault(e["muscleGroup"], []).append(e["name"])
    for g in sorted(by_group):
        print(f"  {g}: {len(by_group[g])}")


if __name__ == "__main__":
    main()
