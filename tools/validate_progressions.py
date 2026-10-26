"""Coaching-progression checks for the effective daily classes (October 2026 revision).

Run after tools/assemble_curriculum.ps1. These rules keep the fixed sequence aimed at its
long-term skills: patient lever rungs, no back-to-back-to-back straight-arm loading, daily
handstand practice, and weekly overhead pressing, compression and hinge work. Rules 26-31
(round 3 of the pro reviews) guard the original-source projection, recovery spacing and the
Level 6-7 weekly load.
"""
import json
import math
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
# Level 7 "Mastery" alone trains the full levers as brief holds; everywhere else they stay readiness checks.
FULL_HOLDS = {"full_front": "straddle_front", "full_back": "straddle_back"}
STRAIGHT_ARM = {m for ladder in ("front lever", "back lever", "planche") for m in LADDERS[ladder]} | set(READINESS)
PLANCHE_HOLDS = set(LADDERS["planche"])
OVERHEAD = {"pike_pushup", "elevated_pike_pushup", "wall_hspu_negative", "wall_hspu", "deficit_wall_hspu", "freestanding_hspu_negative"}
WALL_HSPU = {"wall_hspu", "wall_hspu_negative", "deficit_wall_hspu", "freestanding_hspu_negative"}
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


def numbers(value):
    if value is None:
        return []
    return [int(x) for x in re.findall(r"\d+", str(value))]


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

# 2. Top shapes are single readiness checks next to their previous rung. Only Level 7 trains the full levers, as
#    brief holds: 1-3 s, at most 3 sets, the straddle trained in the same class as the fallback, and only after
#    an earlier class has checked the shape. (Round 2 of the pro reviews moved every check behind its own gate,
#    so one check, at L7:030 and L7:033, now precedes the first brief holds; before, four early checks did.)
checks_before = {mid: 0 for mid in FULL_HOLDS}
first_full_hold = {}
for p in classes:
    rows = effective(p)
    present = {mid for mid, _, _ in rows}
    trained_here = {mid for mid, _, s in rows if s is not None and s[5] != "readiness_check"}
    for mid, sets, step in rows:
        if mid not in READINESS:
            continue
        if mid in FULL_HOLDS and p["level"] == 7 and step[5] != "readiness_check":
            secs = numbers(step[3])
            if sets > 3 or step[2] is not None or not secs or not 1 <= min(secs) <= max(secs) <= 3 or step[6] != FULL_HOLDS[mid]:
                errors.append(f"{p['id']} {mid} must be at most 3 brief 1-3 s holds falling back to {FULL_HOLDS[mid]}")
            if FULL_HOLDS[mid] not in trained_here:
                errors.append(f"{p['id']} trains {mid} without {FULL_HOLDS[mid]} in the same class")
            if mid not in first_full_hold:
                first_full_hold[mid] = index[p["id"]]
                if checks_before[mid] < 1:
                    errors.append(f"{p['id']} trains {mid} after only {checks_before[mid]} readiness-check classes")
            continue
        if sets != 1 or step[5] != "readiness_check" or step[6] != READINESS[mid]:
            errors.append(f"{p['id']} {mid} must be one readiness_check set falling back to {READINESS[mid]}")
        if READINESS[mid] not in present:
            errors.append(f"{p['id']} {mid} readiness check lacks its working rung {READINESS[mid]}")
        if mid in checks_before and mid not in first_full_hold:
            checks_before[mid] += 1
# The full holds keep the six-week rung gap after the first trained straddle.
first_trained = {}
for p in classes:
    for mid, _, s in effective(p):
        if s is not None and s[5] != "readiness_check":
            first_trained.setdefault(mid, index[p["id"]])
for mid, straddle in FULL_HOLDS.items():
    if mid not in first_full_hold:
        errors.append(f"Level 7 never trains a brief {mid} hold")
    elif first_full_hold[mid] < first_trained[straddle] + GAP:
        errors.append(f"{mid} holds start {first_full_hold[mid] - first_trained[straddle]} classes after {straddle}; at least {GAP}")

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
                        6: {"archer_pullup", "archer_pushup", "wall_hspu", "lsit", "nordic_negative", "pistol_squat"},
                        7: {"typewriter_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative", "archer_pushup",
                            "deficit_wall_hspu", "freestanding_hspu_negative", "lsit", "nordic_negative", "pistol_squat",
                            "bulgarian_split_squat", "toes_to_bar", "dragon_flag", "full_front", "full_back"}}.items():
    for mid in sorted(required - by_level[level]):
        errors.append(f"Level {level} never prescribes {mid}")

# ---- October 2026 pro-athlete reviews ----
RC = "readiness_check"
authored = [p for p in classes if p["origin"] != "apk_authentic"]


def units(value, sets):
    """Total repetitions or seconds of a step, counted like validate_curriculum.ps1 (ladders sum, clusters repeat)."""
    values = numbers(value)
    if not values:
        return 0
    text = str(value)
    total = sum(values) if "-" in text else (sum(values) * sets if "+" in text else values[0] * sets)
    return total * (2 if text.endswith("/side") else 1)


def work(p):
    """Effective main plus practice tuples (authored step tuples only)."""
    return [s for _, _, s in effective(p) if s is not None] + list(p["practice"])


def recovery(p):
    return p.get("morning_recovery") or (p["schedule"]["rpe"] is not None and p["schedule"]["rpe"] <= 4)


def weeks(level):
    """Stored seven-class authored weeks of a level, skipping test and deload weeks."""
    rows = [p for p in level["programs"] if p["origin"] != "apk_authentic"]
    for w in range(0, len(rows) - 6, 7):
        week = rows[w:w + 7]
        training = [p for p in week if not recovery(p)]
        tests = sum(bool(TEST_DAY.search(p["schedule"]["day_type"])) for p in training)
        deloads = sum("deload" in p["schedule"]["day_type"] for p in training)
        if training and 2 * tests < len(training) and 2 * deloads < len(training):
            yield week


# 9. A rung starts only after a class has prescribed the library's "Start when" dose of the rung below it.
GATES = {
    "one_leg_front": ("advanced_tuck_front", 3, 10), "straddle_front": ("one_leg_front", 3, 8),
    "one_leg_back": ("advanced_tuck_back", 3, 10), "straddle_back": ("one_leg_back", 3, 8),
    "advanced_tuck_planche": ("tuck_planche", 3, 8), "one_leg_dragon_negative": ("bent_dragon_negative", 3, 3),
    "full_dragon_negative": ("one_leg_dragon_negative", 3, 2), "dragon_flag": ("full_dragon_negative", 3, 2),
    "strict_muscleup": ("chest_to_bar", 3, 5), "pistol_squat": ("assisted_pistol_squat", 3, 8),
    "handstand_toe_pull": ("wall_handstand", 3, 40), "assisted_tuck_flag": ("flag_ground_support", 3, 15),
    "tuck_flag": ("assisted_tuck_flag", 3, 8), "straddle_flag": ("tuck_flag", 3, 5),
    "wall_hspu": ("wall_hspu_negative", 3, 5), "lsit": ("one_leg_lsit", 3, 10),
    # Level 7 "Mastery" rungs (readiness checks never open a gate or start a rung).
    "full_front": ("straddle_front", 3, 8), "full_back": ("straddle_back", 3, 8), "full_flag": ("straddle_flag", 3, 5),
    "deficit_wall_hspu": ("wall_hspu", 3, 5), "freestanding_hspu_negative": ("deficit_wall_hspu", 3, 3),
    "typewriter_pullup": ("archer_pullup", 3, 5), "assisted_one_arm_pullup": ("typewriter_pullup", 3, 3),
    "one_arm_pullup_negative": ("assisted_one_arm_pullup", 3, 3),
    # Round 2 of the pro reviews (25 October 2026).
    "straddle_planche": ("advanced_tuck_planche", 3, 8), "advanced_tuck_back": ("tuck_back_hold", 3, 10),
    "wall_kickup": ("wall_handstand", 2, 45),
}
gate_at, started = {}, set()
for p in classes:
    rows = [s for s in work(p) if s[5] != RC]
    for s in rows:
        if s[0] in GATES and s[0] not in started:
            started.add(s[0])
            if s[0] not in gate_at:
                below, sets, dose = GATES[s[0]]
                errors.append(f"{p['id']} starts {s[0]} before an earlier class prescribed {below} {sets} x {dose}")
    for s in rows:
        for rung, (below, sets, dose) in GATES.items():
            if s[0] == below and rung not in gate_at and s[1] >= sets and max(numbers(s[2] if s[3] is None else s[3]) or [0]) >= dose:
                gate_at[rung] = index[p["id"]]

# 9b. End shapes wait for their gates even as readiness checks: the first check of the full levers, the straddle
#     planche and the full flag comes after their gate dose, in an earlier class or earlier in the same class.
#     The advanced-tuck back lever needs two earlier 3 x 10 s tuck sessions, and kick-ups to the wall come before
#     any handstand balance attempt.
END_GATES = {"full_front": ("straddle_front", 3, 8), "full_back": ("straddle_back", 3, 8),
             "straddle_planche": ("advanced_tuck_planche", 3, 8), "full_flag": ("straddle_flag", 3, 5)}


def gate_step(s, below, sets, dose):
    return s[0] == below and s[5] != RC and s[1] >= sets and max(numbers(s[2] if s[3] is None else s[3]) or [0]) >= dose


for mid, (below, sets, dose) in END_GATES.items():
    met = False
    for p in classes:
        rows = list(p["practice"]) + [s for _, _, s in effective(p) if s is not None]  # practice comes before main
        hits = [i for i, s in enumerate(rows) if s[0] == mid]
        if hits:
            if not (met or any(gate_step(r, below, sets, dose) for r in rows[:hits[0]])):
                errors.append(f"{p['id']} uses {mid} before {below} {sets} x {dose} s was prescribed")
            break
        met = met or any(gate_step(r, below, sets, dose) for r in rows)
sessions = 0
for p in classes:
    rows = work(p)
    if any(s[0] == "advanced_tuck_back" for s in rows):
        if sessions < 2:
            errors.append(f"{p['id']} starts advanced_tuck_back after {sessions} classes of tuck_back_hold 3 x 10 s; two needed")
        break
    sessions += any(gate_step(s, "tuck_back_hold", 3, 10) for s in rows)
kickup_first = min((index[p["id"]] for p in classes for s in p["practice"] if s[0] == "wall_kickup"), default=None)
balance_start = min((index[p["id"]] for p in classes for s in p["practice"] if s[0] == "handstand_balance"), default=None)
if kickup_first is None or balance_start is None or kickup_first >= balance_start:
    errors.append("kick-ups to the wall must be practised before the first handstand balance attempt")

# 10. Every readiness check falls back to a rung trained in the same class.
for p in classes:
    trained = {s[0] for s in work(p) if s[5] != RC}
    for s in work(p):
        if s[5] == RC and s[6] not in trained:
            errors.append(f"{p['id']} readiness check {s[0]} has no trained fallback {s[6]}")

# 11. Human-flag practice never shares a class with lever or muscle-up work, and its ladder goes past the tuck.
FLAG = {"flag_ground_support", "assisted_tuck_flag", "tuck_flag", "straddle_flag", "full_flag"}
LEVER_MU = set(LADDERS["front lever"]) | set(LADDERS["back lever"]) | set(FULL_HOLDS) | {"strict_muscleup", "band_muscleup", "negative_muscleup", "jump_muscleup"}
for p in authored:
    if {s[0] for s in p["practice"]} & FLAG and {mid for mid, _, _ in effective(p)} & LEVER_MU:
        errors.append(f"{p['id']} pairs flag practice with lever or muscle-up work")
if not any(s[0] == "straddle_flag" for p in classes for s in p["practice"]) or \
        not any(s[0] == "full_flag" and s[5] == RC for p in classes for s in p["practice"]):
    errors.append("the human-flag ladder must train the straddle flag and check the full flag")
for easier, harder in zip(["assisted_tuck_flag", "tuck_flag"], ["tuck_flag", "straddle_flag"]):
    starts = {m: min((index[p["id"]] for p in classes for s in p["practice"] if s[0] == m), default=None) for m in (easier, harder)}
    if None in starts.values() or starts[harder] < starts[easier] + GAP:
        errors.append(f"human flag: {harder} must start at least {GAP} classes after {easier}")
# The full flag is a readiness check until the last phase of Level 7, which trains it as brief holds (at most
# 3 sets of 1-3 s per side) with the straddle flag trained in the same class as the fallback.
full_flag_first = None
for p in classes:
    trained_flags = {s[0] for s in p["practice"] if s[5] != RC}
    for s in p["practice"]:
        if s[0] != "full_flag" or s[5] == RC:
            continue
        secs = numbers(s[3])
        if not (p["level"] == 7 and p["number"] > 70):
            errors.append(f"{p['id']} trains the full flag before the last phase of Level 7")
        if s[1] > 3 or not secs or not 1 <= max(secs) <= 3 or not str(s[3]).endswith("/side") or s[6] != "straddle_flag":
            errors.append(f"{p['id']} full flag must be at most 3 brief 1-3 s holds per side falling back to straddle_flag")
        if "straddle_flag" not in trained_flags:
            errors.append(f"{p['id']} trains the full flag without the straddle flag in the same class")
        if full_flag_first is None:
            full_flag_first = index[p["id"]]
straddle_flag_first = min((index[p["id"]] for p in classes for s in p["practice"] if s[0] == "straddle_flag"), default=None)
if full_flag_first is None or straddle_flag_first is None or full_flag_first < straddle_flag_first + GAP:
    errors.append(f"human flag: trained full flags must start at least {GAP} classes after the straddle flag")
for level in catalog["levels"]:
    if level["number"] >= 3:
        for week in weeks(level):
            if not any(s[0] == "straight_side_plank" for p in week for s in work(p)):
                errors.append(f"Level {level['number']} classes {week[0]['number']}-{week[-1]['number']} have no straight side plank")

# 12. Shoulder extension directly before the first back-lever hold, and the strict muscle-up before any lever hold
#     (round 2 of the pro reviews: the explosive transition comes while fresh, no longer after 6-9 straight-arm
#     sets; this replaces the October rule that put the back lever first). Test, relay and five-skill classes keep
#     their authored order.
BL_HOLDS = set(LADDERS["back lever"]) | {"full_back"}
LEVER_HOLDS = BL_HOLDS | set(LADDERS["front lever"]) | {"full_front"}
MU = {"strict_muscleup", "band_muscleup", "negative_muscleup", "jump_muscleup"}
for p in authored:
    ids = [mid for mid, _, s in effective(p) if s is None or s[5] != RC]
    holds = [i for i, mid in enumerate(ids) if mid in BL_HOLDS]
    if holds and not recovery(p):
        first_hold = min(holds)
        if not (first_hold > 0 and ids[first_hold - 1] in ("german_hang", "skin_cat_partial")) and \
                not {"german_hang", "skin_cat_partial"} & {s[0] for s in warmup(p)}:
            errors.append(f"{p['id']} holds a back lever without shoulder-extension preparation directly before it")
    levers = [i for i, mid in enumerate(ids) if mid in LEVER_HOLDS]
    strict = [i for i, mid in enumerate(ids) if mid == "strict_muscleup"]
    exempt = TEST_DAY.search(p["schedule"]["day_type"]) or "relay" in p["schedule"]["day_type"] or p["schedule"]["day_type"].startswith("five_")
    if levers and strict and min(strict) > min(levers) and not exempt:
        errors.append(f"{p['id']} puts the strict muscle-up after lever holds")

# 13. From Level 4 the front lever and planche come at least twice a week and the back lever weekly; Nordic negatives
#     weekly from L4:58; toes-to-bar weekly from Level 5; overhead pressing twice a week; no wall HSPU with planche holds.
for level in catalog["levels"]:
    for week in weeks(level):
        label = f"Level {level['number']} classes {week[0]['number']}-{week[-1]['number']}"
        count = lambda ids: sum(bool({mid for mid, _, _ in effective(p)} & ids) for p in week)
        if level["number"] >= 4:
            for name, ids, need in (("front lever", set(LADDERS["front lever"]), 2), ("planche", PLANCHE_HOLDS | {"planche_lean"}, 2),
                                    ("back lever", BL_HOLDS, 1), ("overhead pressing", OVERHEAD, 2)):
                if count(ids) < need:
                    errors.append(f"{label} has {name} in {count(ids)} classes; at least {need}")
        if (level["number"], week[0]["number"]) >= (4, 58) and not count({"nordic_negative"}):
            errors.append(f"{label} has no Nordic negatives")
        if level["number"] >= 5 and not count({"toes_to_bar"}):
            errors.append(f"{label} has no toes-to-bar")
for p in classes:
    ids = {mid for mid, _, _ in effective(p)}
    if ids & {"tuck_planche", "advanced_tuck_planche"} and ids & WALL_HSPU:
        errors.append(f"{p['id']} combines wall handstand push-ups with planche holds")

# 14. Strict muscle-ups stay at six repetitions or fewer per class before Level 5; warm-ups only prepare.
HARD = {"skill", "skill_strength", "advanced_skill", "advanced_strength", "advanced_fundamental", "mastery_skill"}
for p in authored:
    if p["level"] < 5:
        reps = sum(units(s[2], s[1]) for _, _, s in effective(p) if s is not None and s[0] == "strict_muscleup")
        if reps > 6:
            errors.append(f"{p['id']} prescribes {reps} strict muscle-ups; at most 6 before Level 5")
    for s in p["warmup"]:
        if movements[s[0]]["category"] in HARD:
            errors.append(f"{p['id']} warm-up holds {s[0]}")

# 15. Recovery classes (RPE 4 or lower) hold no skill or advanced work.
for p in authored:
    if recovery(p):
        for mid, _, _ in effective(p):
            if movements[mid]["category"] in HARD:
                errors.append(f"{p['id']} is a recovery class with {mid}")

# 16. Straight-body tension is trained before straight-body rungs.
hollow = min((index[p["id"]] for p in classes for _, n, s in effective(p)
              if s is not None and s[0] == "hollow_body_hold" and n >= 2 and max(numbers(s[3]) or [0]) >= 20), default=None)
for mid in ("straddle_front", "straddle_back", "full_dragon_negative", "dragon_flag"):
    if hollow is None or first.get(mid, 0) <= hollow:
        errors.append(f"{mid} starts before hollow body holds reach 2 x 20 s")

# 16b. Small daily doses in Levels 5-6 as in Level 7: at most two RPE 8 classes in a week, outside the gate weeks.
for level in catalog["levels"]:
    if level["number"] in (5, 6):
        rows = level["programs"]
        for w in range(0, len(rows), 7):
            week = rows[w:w + 7]
            if any(78 <= p["number"] <= 83 for p in week):
                continue
            if sum((p["schedule"]["rpe"] or 0) >= 8 for p in week) > 2:
                errors.append(f"Level {level['number']} classes {week[0]['number']}-{week[-1]['number']} have more than two RPE 8 classes")

# 17. Handstand exits come at least 20 classes before balance attempts; Level 1 ends ready for Level 2.
exit_first = min((index[p["id"]] for p in classes for s in p["practice"] if s[0] == "handstand_exit"), default=None)
balance_first = min((index[p["id"]] for p in classes for s in p["practice"] if s[0] == "handstand_balance"), default=None)
if exit_first is None or balance_first is None or balance_first < exit_first + 20:
    errors.append("handstand exits must be practised at least 20 classes before balance attempts")
tail = {mid for p in catalog["levels"][0]["programs"][-30:] for mid, _, _ in effective(p)}
for need in ({"chinup", "pullup"}, {"pushup"}, {"parallel_dip", "eccentric_dip"}, {"pike_pushup"}, {"tuck_lsit"}):
    if not tail & need:
        errors.append(f"the last 30 Level 1 classes never prescribe {' or '.join(sorted(need))}")

# 18. Listed minutes cover the class as prescribed, practice included, and stay at 55 or less outside test days.
def step_seconds(s):
    holds = numbers(s[3])
    reps = numbers(s[2])
    per_side = "/side" in str(s[3] if s[3] is not None else s[2])
    one = (sum(holds) / len(holds)) if holds else (3.5 * sum(reps) / len(reps) if reps else 20)
    return s[1] * (one * (2 if per_side else 1) + (s[4] or 0)) + 20


for p in authored:
    parts = list(warmup(p)) + list(p["practice"]) + [s for _, _, s in effective(p)] + list(p["cooldown"])
    estimate = -(-sum(step_seconds(s) for s in parts) // 60)
    minutes = p["schedule"]["minutes"]
    if minutes < estimate:
        errors.append(f"{p['id']} lists {minutes} minutes for about {int(estimate)} minutes of work")
    showcase = p["schedule"]["day_type"].startswith("five_")  # level-end five-goal integration and relay classes
    if minutes > 55 and not TEST_DAY.search(p["schedule"]["day_type"]) and not showcase:
        errors.append(f"{p['id']} lists {minutes} minutes; training classes stay at 55 or less")

# ---- Level 7 "Mastery" ----
level7 = next((lv["programs"] for lv in catalog["levels"] if lv["number"] == 7), [])
if len(level7) != 100:
    errors.append(f"Level 7 must hold 100 classes, found {len(level7)}")
stored = [level7[w:w + 7] for w in range(0, len(level7) - 6, 7)]


def week_kind(week):
    training = [p for p in week if not recovery(p)]
    if 2 * sum(bool(TEST_DAY.search(p["schedule"]["day_type"])) for p in training) >= len(training):
        return "test"
    return "deload" if 2 * sum("deload" in p["schedule"]["day_type"] for p in training) >= len(training) else "build"


def main_sets(p):
    return sum(n for _, n, _ in effective(p))


# 19. Deload weeks come every third or fourth week and keep at most 70% of the previous build week's main sets.
kinds = [week_kind(w) for w in stored]
run, build_sets = 0, None
for week, kind in zip(stored, kinds):
    label = f"Level 7 classes {week[0]['number']}-{week[-1]['number']}"
    total = sum(main_sets(p) for p in week if not recovery(p))
    if kind == "build":
        run, build_sets = run + 1, total
        if run > 3:
            errors.append(f"{label} make a fourth build week in a row without a deload")
    else:
        run = 0
    if kind == "deload" and (build_sets is None or total > 0.7 * build_sets):
        errors.append(f"{label} deload keeps {total} of {build_sets} main sets; at most 70%")
    # 20. Small daily doses: at most two RPE 8 classes a week.
    if sum(p["schedule"]["rpe"] >= 8 for p in week) > 2:
        errors.append(f"{label} have more than two RPE 8 classes")
    # 21. The one-arm pull-up and handstand push-up paths each come at least twice a week outside the test week.
    if kind != "test":
        for name, ids in (("one-arm pull-up groundwork", {"archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative"}),
                          ("handstand push-up work", {"wall_hspu", "deficit_wall_hspu", "freestanding_hspu_negative"})):
            if sum(bool({mid for mid, _, _ in effective(p)} & ids) for p in week) < 2:
                errors.append(f"{label} have {name} in fewer than two classes")
if kinds.count("deload") < 3:
    errors.append("Level 7 needs a deload every third or fourth week")
# 22. The level ends with a benchmark week and a five-skill showcase, and every class, those included, stays within 55 minutes.
if not kinds or kinds[-1] != "test":
    errors.append("Level 7 must end with a test week")
if not any(p["schedule"]["day_type"].startswith("five_") for p in level7[-3:]):
    errors.append("Level 7 must close with a five-skill showcase class")
for p in level7:
    if p["schedule"]["minutes"] > 55:
        errors.append(f"{p['id']} lists {p['schedule']['minutes']} minutes; Level 7 classes stay at 55 or less")
# 23. The new pulling and pressing rungs start at least three weeks apart, each after its gate.
PATHS = {"one-arm pull-up": ["archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative"],
         "handstand push-up": ["wall_hspu", "deficit_wall_hspu", "freestanding_hspu_negative"]}
for name, path in PATHS.items():
    for easier, harder in zip(path, path[1:]):
        if harder not in first_trained or easier not in first_trained or first_trained[harder] < first_trained[easier] + 21:
            errors.append(f"{name}: {harder} must start at least 21 classes after {easier}")
# 24. Freestanding negatives wait for calm 20-second balance attempts in practice.
balance_ready = min((index[p["id"]] for p in classes for s in p["practice"]
                     if s[0] == "handstand_balance" and s[1] >= 3 and max(numbers(s[3]) or [0]) >= 20), default=None)
if balance_ready is None or first_trained.get("freestanding_hspu_negative", 0) <= balance_ready:
    errors.append("freestanding_hspu_negative starts before 3 x 20 s handstand balance practice")
# 25. Every Level 7 rung, and the kick-up to the wall, states its start dose and has an easier fallback.
for mid in ("full_front", "full_back", "full_flag", "deficit_wall_hspu", "freestanding_hspu_negative", "typewriter_pullup",
            "assisted_one_arm_pullup", "one_arm_pullup_negative", "wall_kickup"):
    if "Start when" not in movements[mid]["safety"]["en"] or not movements[mid]["regression_id"]:
        errors.append(f"{mid} needs a 'Start when' gate in its safety text and a regression")

# ---- Round 3 of the pro-athlete reviews (26 October 2026) ----
plan = json.loads((ROOT / "curriculum/morning_class.json").read_text(encoding="utf-8"))
rules = plan["early_projection"]
guided = [p for p in classes if p["origin"] != "apk_authentic"]


def rule_field(rule, name, actual):
    if name not in rule:
        return True
    wanted = rule[name] if isinstance(rule[name], list) else [rule[name]]
    return (actual or "").strip() in [w.strip() for w in wanted]


def rule_where(rule, level, number):
    if "where" not in rule:
        return True
    for span in (rule["where"] if isinstance(rule["where"], list) else [rule["where"]]):
        m = re.fullmatch(r"L(\d):(\d{3})(?:-L(\d):(\d{3}))?", span)
        lo = int(m[1]) * 1000 + int(m[2])
        hi = int(m[3] or m[1]) * 1000 + int(m[4] or m[2])
        if lo <= level * 1000 + number <= hi:
            return True
    return False


def library_steps(p):
    return (p["main"] if p.get("morning_recovery") else (p.get("training_main") or [])) + list(p.get("practice") or [])


# 26. Every original-source step the app shows in L1:001-040, L2:041-070 and L3:001-020 is a library movement with an
#     easier rung, or a source step kept by an early_projection keep rule: an entry-level step in the first 40 Level 1
#     classes, or a step whose library movement an earlier guided class prescribed at no less than the kept dose minus
#     two (repetitions) or minus five seconds. Steps the reviewers showed come before their ladder may never be kept.
NEVER_KEPT = re.compile(r"(muscle up|head banger|korean|l-sit|toes to bar|inverted hang|inverted l|single leg squat|"
                        r"behind the neck|kipping|jump to hang|reverse typewriter|windshield|skin the cat|pull over)", re.I)
projected_steps = kept_steps = 0
for p in classes:
    if p["origin"] != "apk_authentic" or p.get("morning_recovery"):
        continue
    here = index[p["id"]]
    for e in p["training_content"]["exercises"]:
        name, desc = e["name"]["en"] or "", e["description"]["en"] or ""
        for s in e["sets"]:
            mid = e.get("movement_id") or s.get("movement_id")
            if mid:
                projected_steps += 1
                if mid not in movements or movements[mid]["regression_id"] not in movements:
                    errors.append(f"{p['id']} projects '{name}' to {mid}, which needs a library movement with a regression")
                continue
            kept_steps += 1
            sname, sdesc = s["name"]["en"] or "", s["description"]["en"] or ""
            label = " / ".join(x for x in (name, desc, sname, sdesc) if x)
            hit = next((r for r in rules if r["action"] != "project_exercise" and rule_where(r, p["level"], p["number"])
                        and rule_field(r, "exercise", name) and rule_field(r, "exercise_description", desc)
                        and rule_field(r, "set", sname) and rule_field(r, "set_description", sdesc)), None)
            if hit is None or hit["action"] != "keep":
                errors.append(f"{p['id']} shows the source step '{label}' without a library movement or a keep rule")
                continue
            if NEVER_KEPT.search(f"{name if name != 'Compound Exercise' else ''} {sname} {sdesc} {desc}"):
                errors.append(f"{p['id']} keeps '{label}', which must become a library movement")
            prepared = hit["prepared_by"]
            if prepared == "entry":
                if not (p["level"] == 1 and p["number"] <= 40):
                    errors.append(f"{p['id']} keeps '{label}' as an entry-level step outside L1:001-040")
                continue
            earlier = [t for q in guided if index[q["id"]] < here for t in library_steps(q) if t[0] == prepared]
            if not earlier:
                errors.append(f"{p['id']} keeps '{label}' before any guided class prescribed {prepared}")
                continue
            reps = [max(numbers(t[2])) for t in earlier if t[3] is None and numbers(t[2])]
            secs = [max(numbers(t[3])) for t in earlier if t[3] is not None and numbers(t[3])]
            if s["repetitions"] is not None and reps and s["repetitions"] > max(reps) + 2:
                errors.append(f"{p['id']} keeps '{label}' at {s['repetitions']} reps; {prepared} reached {max(reps)} before")
            if s["duration_seconds"] is not None and secs and s["duration_seconds"] > max(secs) + 5:
                errors.append(f"{p['id']} keeps '{label}' at {s['duration_seconds']} s; {prepared} reached {max(secs)} s before")


def rpe_of(p):
    return 3 if p.get("morning_recovery") else (p.get("training_rpe") or p["schedule"]["rpe"])


# 27. No run of more than six training classes without an RPE 4 or easier class, anywhere in the catalog order.
run, start = 0, None
for p in classes:
    if rpe_of(p) <= 4:
        run = 0
        continue
    run, start = run + 1, (start if run else p["id"])
    if run == 7:
        errors.append(f"seven or more training classes in a row without an RPE 4 class from {start}")

# 28. Deload blocks (three or more 'deload' classes within a few days of each other) come no more than 42 classes
#     apart, across level ends too.
blocks = []
for i, p in enumerate(classes):
    if "deload" in p["schedule"]["day_type"]:
        if blocks and i - blocks[-1][-1] <= 3:
            blocks[-1].append(i)
        else:
            blocks.append([i])
blocks = [b for b in blocks if len(b) >= 3]
deload_gaps = [b2[0] - b1[-1] - 1 for b1, b2 in zip(blocks, blocks[1:])]
for b1, b2, gap in zip(blocks, blocks[1:], deload_gaps):
    if gap > 42:
        errors.append(f"{gap} classes without a deload between {classes[b1[-1]]['id']} and {classes[b2[0]]['id']}")

# 29. Levels 6-7 (R3 elbow.py, R2 wrist.py definitions): no more than three heavy elbow-flexor days in a row (ten or
#     more strict pull repetitions, per-side work counted twice; a lever, German-hang or tuck-or-harder planche hold; or
#     human-flag work), and no more than three heavy wrist days in a row (two or more sets of planche-family work or of
#     overhead pressing).
STRICT_PULL = {"pullup", "chinup", "eccentric_pullup", "chest_to_bar", "strict_muscleup", "band_muscleup", "negative_muscleup",
               "jump_muscleup", "archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative"}
ELBOW_HOLDS = set(LADDERS["front lever"]) | set(LADDERS["back lever"]) | {"full_front", "full_back", "german_hang",
              "tuck_planche", "advanced_tuck_planche", "straddle_planche", "pseudo_planche_pushup"}
FLAGS = {"assisted_tuck_flag", "tuck_flag", "straddle_flag", "full_flag"}
PLANCHE_FAMILY = {"planche_lean", "frog_stand", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "pseudo_planche_pushup"}
PRESSING = {"wall_hspu_negative", "wall_hspu", "deficit_wall_hspu", "freestanding_hspu_negative", "elevated_pike_pushup", "pike_pushup"}


def all_steps(p):
    return [s for _, _, s in effective(p) if s is not None] + list(p["practice"])


def heavy_elbow(p):
    steps = all_steps(p)
    pulls = sum(units(s[2], s[1]) for s in steps if s[0] in STRICT_PULL and s[3] is None)
    return pulls >= 10 or bool({s[0] for s in steps} & (ELBOW_HOLDS | FLAGS))


def heavy_wrist(p):
    steps = all_steps(p)
    return sum(s[1] for s in steps if s[0] in PLANCHE_FAMILY) >= 2 or sum(s[1] for s in steps if s[0] in PRESSING) >= 2


late = [p for p in classes if p["level"] >= 6]
for name, test in (("elbow-flexor", heavy_elbow), ("wrist", heavy_wrist)):
    run, start = 0, None
    for p in late:
        run, start = (run + 1, start if run else p["id"]) if test(p) else (0, None)
        if run == 4:
            errors.append(f"four heavy {name} days in a row from {start}")

# 30. From Level 6 the straddle and full back-lever classes carry no strict vertical pulling (showcases excepted), every
#     front-lever class keeps an advanced-tuck set, and every Level 7 week keeps the plain wall handstand push-up.
VERTICAL = {"pullup", "chinup", "archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative"}
for p in late:
    work_ids = {mid for mid, _, _ in effective(p)}
    showcase = p["schedule"]["day_type"].startswith("five_")
    if work_ids & {"straddle_back", "full_back"} and work_ids & VERTICAL and not showcase:
        errors.append(f"{p['id']} pairs straddle or full back-lever holds with strict pulling")
    if re.search(r"front_lever|full_front|straddle_front", p["schedule"]["day_type"]) and not showcase \
            and "advanced_tuck_front" not in work_ids:
        errors.append(f"{p['id']} is a front-lever class without an advanced-tuck front-lever set")
for w in range(0, len(level7) - 6, 7):
    if not any(mid == "wall_hspu" for p in level7[w:w + 7] for mid, _, _ in effective(p)):
        errors.append(f"Level 7 classes {w + 1}-{w + 7} drop the wall handstand push-up")

# 31. The full-flag readiness checks start at L7:071, after twice-weekly straddle-flag work.
first_flag = next((p for p in classes for s in p["practice"] if s[0] == "full_flag"), None)
if first_flag is None or (first_flag["level"], first_flag["number"]) < (7, 71) or \
        next(s for s in first_flag["practice"] if s[0] == "full_flag")[5] != RC:
    errors.append("the first full flag must be a readiness check at L7:071 or later")

# ---- Calibration (26 October 2026): progression from easiest to hardest, and visible milestones ----
# 32. Along the 700 classes no build dose of a basic or skill movement falls more than 20% below that movement's best
#     build dose so far (deliberate wave days stay inside the 20%); benchmark sets form their own series. Excepted: recovery
#     days, deload weeks, readiness checks, showcases and relays, and the planned easy re-entry class L2:071. The
#     original-source blocks after Level 1 set the best so far for the pull-ups, dips and push-ups they show. Strict
#     muscle-ups before Level 5 stop at rule 14's six repetitions a class. Paused and slow-tempo sets (a 3+ second
#     lowering or a 2+ second pause, such as 22X2, 31X1 or 41X1) are a different stimulus at fewer repetitions by
#     design (round 4: paused pull-ups 3 x 6-7), so they neither set nor meet the best so far.
RATCHET_SKIP = {"dynamic_warmup", "mobility", "static_stretch", "static_cooldown", "recovery", "activation",
                "mobility_strength", "strength_regression"}
REENTRY = {"sg:l02:p0071": "the planned easy re-entry class after the Level 2 original-source block"}
SOURCE_NAMES = {"Pull Up": "pullup", "Push Up": "pushup", "Dips": "parallel_dip", "Bar Dips": "straight_bar_dip"}


def day_of(p):
    return (p["number"] - 1) % 7 + 1


def per_set(value):
    if value is None:
        return None
    text = str(value).replace("/side", "")
    vals = [int(x) for x in re.findall(r"\d+", text)]
    return (sum(vals) if "+" in text else max(vals)) if vals else None


def slow_tempo(tempo):
    m = re.fullmatch(r"(\d)(\d)[\dX](\d)", str(tempo or ""))
    return bool(m) and (int(m[1]) >= 3 or int(m[2]) >= 2)


best_dose = {}
for p in classes:
    if p["origin"] == "apk_authentic":
        if p.get("morning_recovery") or p["level"] == 1:
            continue
        for e in p["training_content"]["exercises"]:
            for s in e["sets"]:
                mid = e.get("movement_id") or s.get("movement_id") or SOURCE_NAMES.get(s["name"]["en"] or e["name"]["en"])
                if mid in {"pullup", "chinup", "parallel_dip", "straight_bar_dip", "pushup"} and s["repetitions"] \
                        and "each" not in (s["description"]["en"] or ""):
                    best_dose[("build", mid, 2)] = max(best_dose.get(("build", mid, 2), 0), s["repetitions"])
        continue
    dt = p["schedule"]["day_type"]
    if recovery(p) or "deload" in dt or p["id"] in REENTRY or dt.startswith("five_") or "relay" in dt:
        continue
    for s in [s for _, _, s in effective(p)] + list(p["practice"]):
        if s[5] == RC or movements[s[0]]["category"] in RATCHET_SKIP or slow_tempo(s[5]):
            continue
        # Benchmarks (assessment sets, single sets on benchmark, audit and checkpoint days) form their own series.
        series = "test" if s[5] == "assessment" or (re.search(r"(benchmark|assessment|audit|checkpoint)", dt) and s[1] == 1)             else "build"
        k = 3 if s[3] is not None else 2
        dose = per_set(s[k])
        if dose is None:
            continue
        floor = math.ceil(0.8 * best_dose.get((series, s[0], k), 0))
        if s[0] == "strict_muscleup" and p["level"] < 5:
            floor = min(floor, 6 // s[1])
        if dose < floor:
            errors.append(f"{p['id']} {s[0]} {s[1]}x{s[k]} falls below 80% of its best {series} dose so far "
                          f"({best_dose[(series, s[0], k)]})")
        best_dose[(series, s[0], k)] = max(best_dose.get((series, s[0], k), 0), dose)

# 33. The mid-program benchmarks test above the level before: L4:083 above L3's checks, L5:083 above L4:083.
def bench(pid):
    return {s[0]: per_set(s[2]) for _, _, s in effective(next(p for p in classes if p["id"] == pid))
            if s is not None and s[3] is None}


for later, earlier in (("sg:l04:p0083", ("sg:l03:p0065", "sg:l03:p0068")), ("sg:l05:p0083", ("sg:l04:p0083",))):
    before = {}
    for pid in earlier:
        for mid, v in bench(pid).items():
            before[mid] = max(before.get(mid, 0), v or 0)
    for mid in ("pullup", "parallel_dip", "pushup"):
        if mid in before and bench(later).get(mid, 0) <= before[mid]:
            errors.append(f"{later} {mid} benchmark {bench(later).get(mid)} does not test above {before[mid]}")

# 34. A test, readiness check or assessment set at least every 45 classes (original-source classes counted), and a
#     first strict pull-up readiness check before the first pull-up sets.
marks = [0]
for i, p in enumerate(classes, start=1):
    steps = ([s for _, _, s in effective(p) if s is not None] if p["origin"] != "apk_authentic" else []) + list(p["practice"])
    if TEST_DAY.search(p["schedule"]["day_type"]) or any(s[5] in (RC, "assessment") for s in steps):
        marks.append(i)
marks.append(len(classes) + 1)
checkpoint_gaps = [b - a - 1 for a, b in zip(marks, marks[1:])]
for a, b in zip(marks, marks[1:]):
    if b - a - 1 > 45:
        errors.append(f"{b - a - 1} classes without a checkpoint after class {a}")
first_pull_check = min((index[p["id"]] for p in guided for _, _, s in effective(p) if s and s[0] == "pullup" and s[5] == RC), default=None)
first_pull_sets = min((index[p["id"]] for p in guided for _, _, s in effective(p) if s and s[0] == "pullup" and s[5] != RC), default=None)
if first_pull_check is None or first_pull_sets is None or first_pull_check >= first_pull_sets:
    errors.append("a strict pull-up readiness check must come before the first guided pull-up sets")

# ---- Round 4 (26 October 2026) ----
# 35. No training class runs past 55 minutes by the length estimate of rule 18 (warm-up, practice, work, rests and
#     set-up), original-source classes included; test, gate and showcase days are excepted outside Level 7.
def class_seconds(p):
    parts = list(warmup(p)) + list(p["practice"]) + list((p.get("coaching_overlay") or p)["cooldown"])
    secs = sum(step_seconds(s) for s in parts)
    if p["origin"] == "apk_authentic":
        for e in p["training_content"]["exercises"]:
            secs += 20 + sum((s["duration_seconds"] or 3.5 * (s["repetitions"] or 0)) + (s["break_seconds"] or 0)
                             for s in e["sets"])
    else:
        secs += sum(step_seconds(s) for _, _, s in effective(p))
    return secs


for p in classes:
    if p.get("morning_recovery"):
        continue
    dt = p["schedule"]["day_type"]
    exempt = p["level"] < 7 and (TEST_DAY.search(dt) or dt.startswith("five_") or "relay" in dt)
    if not exempt and class_seconds(p) > 55 * 60:
        errors.append(f"{p['id']} runs about {math.ceil(class_seconds(p) / 60)} minutes; at most 55")

# 36. Level 4-5 pull-up ramp: plain pull-ups at a normal tempo climb 3 x 8 -> 3 x 9 -> 3 x 10 through Level 4 and
#     3 x 10 -> 3 x 11 through Level 5, never above the stage, and sets of 10 or more rest at least 120 s.
def pull_stage(p):
    if p["level"] == 4:
        return 8 if p["number"] < 29 else (9 if p["number"] < 57 else 10)
    return 10 if p["number"] < 29 else 11


for p in classes:
    if p["level"] not in (4, 5) or p["origin"] == "apk_authentic" or recovery(p) or "deload" in p["schedule"]["day_type"] \
            or TEST_DAY.search(p["schedule"]["day_type"]) or p["schedule"]["day_type"].startswith("five_"):
        continue
    for _, _, s in effective(p):
        if s is None or s[0] != "pullup" or s[3] is not None or s[5] == RC or slow_tempo(s[5]):
            continue
        dose = per_set(s[2])
        if dose > pull_stage(p):
            errors.append(f"{p['id']} pull-ups {s[1]}x{s[2]} run ahead of the Level {p['level']} ramp ({pull_stage(p)})")
        if dose >= 10 and (s[4] or 0) < 120:
            errors.append(f"{p['id']} pull-ups {s[1]}x{s[2]} rest {s[4]} s; sets of 10 or more rest at least 120 s")

# 37. Levels 6-7 keep a second easy day: day 7 of every build week is RPE 5 or easier, ten main sets or fewer, with no
#     strict pulling.
for p in classes:
    dt = p["schedule"]["day_type"]
    if p["level"] < 6 or day_of(p) != 7 or recovery(p) or "deload" in dt or TEST_DAY.search(dt) or dt.startswith("five_") \
            or "dragon" in dt or "checkpoint" in dt:
        continue
    rows = [s for _, _, s in effective(p)]
    if p["schedule"]["rpe"] > 5 or sum(s[1] for s in rows) > 10 or any(s[0] in STRICT_PULL for s in rows):
        errors.append(f"{p['id']} is day 7 of a Level {p['level']} build week but not an easy day")

if errors:
    print(f"Progression checks failed with {len(errors)} error(s):")
    for e in errors[:60]:
        print(" -", e)
    sys.exit(1)
gaps = ", ".join(f"{h} +{first[h] - first[e]}" for ladder in LADDERS.values() for e, h in zip(ladder, ladder[1:]))
print(f"Progression checks passed: rung gaps {gaps}; {len(GATES)} start gates; no straight-arm triple days; weekly overhead, "
      "L-sit and hinge work; wrist prep; daily handstand practice; flag off lever days and past the tuck; shoulder extension "
      "directly before back levers and strict muscle-ups before lever holds; end-shape checks, the advanced-tuck back lever and "
      "balance attempts behind their gates; at most two RPE 8 classes a week in Levels 5-6; statics twice weekly from Level 4; "
      "weekly Nordic, toes-to-bar and pressing; easy recovery classes; "
      f"class length within 55 minutes; Level 7 full holds from {', '.join(f'{m} +{first_full_hold[m] - first_trained[s]}' for m, s in FULL_HOLDS.items())}, "
      f"full flag +{full_flag_first - straddle_flag_first}, deloads at 70% or less every third or fourth week, "
      "one-arm and handstand push-up paths twice weekly, benchmark week and showcase. "
      f"Round 3: {projected_steps} original-source sets shown as library movements and {kept_steps} kept as prepared steps; "
      f"no run of more than six training classes without an easy one; deloads at most {max(deload_gaps)} classes apart; "
      "no more than three heavy elbow-flexor or wrist days in a row in Levels 6-7; no strict pulling beside straddle or "
      "full back levers; advanced-tuck front levers on every front-lever day; weekly wall HSPU in Level 7; full-flag "
      "checks from L7:071. "
      f"Calibration: no build dose more than 20% below its best so far; L4:083 and L5:083 test above the level before; "
      f"a checkpoint at least every {max(checkpoint_gaps)} classes and a first strict pull-up check at class {first_pull_check}. "
      "Round 4: every class within 55 minutes by the length estimate; the Level 4-5 pull-up ramp; an easy day 7 in Levels 6-7.")
