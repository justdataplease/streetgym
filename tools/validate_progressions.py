"""Coaching-progression checks for the effective daily classes (October 2026 revision).

Run after tools/assemble_curriculum.ps1. These rules keep the fixed sequence aimed at its
long-term skills: patient lever rungs, no back-to-back-to-back straight-arm loading, daily
handstand practice, and weekly overhead pressing, compression and hinge work.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / "generated/curriculum_v2.json").read_text(encoding="utf-8"))
movements = {m["id"]: m for m in catalog["movement_library"]}
errors = []

GAP = 42
LADDERS = {
    "front lever": ["tuck_front_hold", "advanced_tuck_front", "one_leg_front", "straddle_front"],
    "back lever": ["tuck_back_hold", "advanced_tuck_back", "one_leg_back", "straddle_back"],
    "planche": ["planche_lean", "tuck_planche", "advanced_tuck_planche"],
    "dragon flag": ["bent_dragon_negative", "one_leg_dragon_negative", "full_dragon_negative", "dragon_flag"],
}
READINESS = {"full_front": "straddle_front", "full_back": "straddle_back",
             "straddle_planche": "advanced_tuck_planche", "full_planche": "advanced_tuck_planche"}
STRAIGHT_ARM = {m for ladder in ("front lever", "back lever", "planche") for m in LADDERS[ladder]} | set(READINESS)
PLANCHE_HOLDS = set(LADDERS["planche"])
OVERHEAD = {"pike_pushup", "elevated_pike_pushup", "wall_hspu_negative", "wall_hspu"}
LSITS = {"tuck_lsit", "one_leg_lsit", "lsit"}
HINGE = {"glute_bridge", "single_leg_bridge", "sliding_leg_curl", "nordic_negative", "single_leg_rdl"}
WRIST = {"wrist_circles", "wrist_rocks"}


def effective(p):
    """(movement_id, sets, step) for the work the app actually prescribes."""
    if p.get("morning_recovery"):
        return [(s[0], s[1], s) for s in p["main"]]
    if p.get("training_main") is not None:
        return [(s[0], s[1], s) for s in p["training_main"]]
    rows = []
    for e in (p.get("training_content") or p["content"])["exercises"]:
        rows.append((e.get("movement_id") or "", len(e["sets"]), None))
    return rows


def warmup(p):
    return (p.get("coaching_overlay") or p)["warmup"]


classes = [p for level in catalog["levels"] for p in level["programs"]]
index = {p["id"]: i + 1 for i, p in enumerate(classes)}

# 1. Rung pacing: each lever rung first appears at least six weeks after the previous one.
first = {}
for p in classes:
    for mid, _, _ in effective(p):
        first.setdefault(mid, index[p["id"]])
for name, ladder in LADDERS.items():
    for easier, harder in zip(ladder, ladder[1:]):
        if harder not in first or easier not in first:
            errors.append(f"{name}: {easier} or {harder} is never prescribed")
        elif first[harder] < first[easier] + GAP:
            errors.append(f"{name}: {harder} starts at class {first[harder]}, only {first[harder] - first[easier]} after {easier}")
for mid in ("tuck_back_hold", "strict_muscleup"):
    if first.get(mid, 0) <= 200:
        errors.append(f"{mid} must not be prescribed before Level 3 (first at class {first.get(mid)})")

# 2. Top shapes are single readiness checks next to their previous rung.
for p in classes:
    rows = effective(p)
    present = {mid for mid, _, _ in rows}
    for mid, sets, step in rows:
        if mid in READINESS:
            if sets != 1 or step[5] != "readiness_check" or step[6] != READINESS[mid]:
                errors.append(f"{p['id']} {mid} must be one readiness_check set falling back to {READINESS[mid]}")
            if READINESS[mid] not in present:
                errors.append(f"{p['id']} {mid} readiness check lacks its working rung {READINESS[mid]}")

# 3. Never three consecutive classes with real straight-arm hold loading (three or more sets).
loaded = [sum(sets for mid, sets, _ in effective(p) if mid in STRAIGHT_ARM) >= 3 for p in classes]
for i in range(len(classes) - 2):
    if all(loaded[i:i + 3]):
        errors.append(f"three straight-arm classes in a row from {classes[i]['id']}")

# 4. Planche is a supporting goal: at most five planche hold sets per class from Level 4
#    (assigned checks and baselines keep their authored test content).
TEST_DAY = re.compile(r"(check|assessment|audit|gate|baseline|benchmark)")
for p in classes:
    if p["level"] >= 4 and not TEST_DAY.search(p["schedule"]["day_type"]):
        sets = sum(n for mid, n, _ in effective(p) if mid in PLANCHE_HOLDS)
        if sets > 5:
            errors.append(f"{p['id']} has {sets} planche hold sets")

# 5. Weekly overhead pressing and compression from Level 3, hinge work from Level 2.
for level in catalog["levels"]:
    authored = [p for p in level["programs"] if p["origin"] != "apk_authentic"]
    for w in range(0, len(authored) - 6, 7):
        week = authored[w:w + 7]
        ids = {mid for p in week for mid, _, _ in effective(p)}
        label = f"Level {level['number']} classes {week[0]['number']}-{week[-1]['number']}"
        training = [p for p in week if (p["schedule"]["rpe"] or 9) > 4]
        tests = sum(bool(re.search(r"(check|assessment|audit|gate|baseline|benchmark)", p["schedule"]["day_type"])) for p in training)
        if 2 * tests >= len(training):
            continue  # test weeks keep their authored content
        if level["number"] >= 3 and not ids & OVERHEAD:
            errors.append(f"{label} has no overhead pressing")
        if level["number"] >= 3 and not ids & LSITS:
            errors.append(f"{label} has no L-sit rung")
        if level["number"] >= 2 and not ids & HINGE:
            errors.append(f"{label} has no hinge or hamstring work")

# 6. Wrist preparation before any class that loads the wrists.
WRIST_LOAD = {"planche", "handstand", "support", "horizontal_push", "vertical_push", "straight_arm_push"}
for p in classes:
    practice = {pt for s in p["practice"] for pt in movements[s[0]]["patterns"]}
    work = {pt for mid, _, _ in effective(p) if mid in movements for pt in movements[mid]["patterns"]}
    if ((practice | work) & WRIST_LOAD) and not p.get("morning_recovery") and not {s[0] for s in warmup(p)} & WRIST:
        if p["origin"] == "apk_authentic" or (p["schedule"]["rpe"] or 9) > 4:
            errors.append(f"{p['id']} loads the wrists without wrist preparation")

# 7. Everyday handstand practice: every class except recovery classes.
for p in classes:
    recovery = p.get("morning_recovery") or (p["schedule"]["rpe"] is not None and p["schedule"]["rpe"] <= 4)
    if not recovery and not any("handstand" in movements[s[0]]["patterns"] for s in p["practice"]):
        errors.append(f"{p['id']} is a training class without handstand practice")

# 8. Harder basics keep strength rising with the skills.
by_level = {lv["number"]: {mid for p in lv["programs"] for mid, _, _ in effective(p)} for lv in catalog["levels"]}
for level, required in {4: {"archer_pullup", "archer_pushup", "elevated_pike_pushup", "one_leg_lsit", "bulgarian_split_squat"},
                        5: {"archer_pullup", "archer_pushup", "wall_hspu_negative", "lsit", "nordic_negative", "pistol_squat"},
                        6: {"archer_pullup", "archer_pushup", "wall_hspu", "lsit", "nordic_negative", "pistol_squat"}}.items():
    for mid in sorted(required - by_level[level]):
        errors.append(f"Level {level} never prescribes {mid}")

if errors:
    print(f"Progression checks failed with {len(errors)} error(s):")
    for e in errors[:60]:
        print(" -", e)
    sys.exit(1)
gaps = ", ".join(f"{h} +{first[h] - first[e]}" for ladder in LADDERS.values() for e, h in zip(ladder, ladder[1:]))
print(f"Progression checks passed: rung gaps {gaps}; no straight-arm triple days; weekly overhead, L-sit and hinge work; wrist prep; daily handstand practice.")
