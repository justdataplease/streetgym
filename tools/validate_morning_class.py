"""Behavioral checks for the effective morning classes and corrected motion contacts."""
import json
import math
import re
from pathlib import Path
from preview_animations import Figure, Template

ROOT = Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / "generated/curriculum_v2.json").read_text(encoding="utf-8"))
asset = json.loads((ROOT / "app/src/main/assets/exercise_animations.json").read_text(encoding="utf-8"))
plan = json.loads((ROOT / "curriculum/morning_class.json").read_text(encoding="utf-8"))
movements = {m["id"]: m for m in catalog["movement_library"]}
progression = json.loads((ROOT / "curriculum/class_progression.json").read_text(encoding="utf-8"))
limits_by_level = {p["level"]: p for p in progression["levels"]}
assert list(limits_by_level) == list(range(1, 8))
assert [lv["level"] for lv in json.loads((ROOT / "curriculum/morning_class.json").read_text(encoding="utf-8"))["levels"]] == list(range(1, 8))
assert [lv["number"] for lv in catalog["levels"]] == list(range(1, 8))
budgets = [p["main_sets_including_practice"] for p in progression["levels"]]
assert budgets == sorted(budgets) and budgets[0] < budgets[-1]
changed_workouts = 0

def dose_values(value, sets):
    if not isinstance(value, str):
        return [value] * sets
    match = re.fullmatch(r"(\d+(?:-\d+)*)(/side)?", value)
    if not match:
        return [value] * sets
    values = match[1].split("-")
    assert len(values) in (1, sets), (value, sets)
    return [(v, bool(match[2])) for v in (values * sets if len(values) == 1 else values)]
new_ids = {"overhead_line", "kneeling_side_plank", "pike_handstand_hold", "wall_handstand", "handstand_toe_pull", "handstand_balance", "flag_ground_support", "assisted_tuck_flag", "tuck_flag"}
FLAG_PRACTICE_IDS = {"flag_ground_support", "assisted_tuck_flag", "tuck_flag", "straddle_flag", "full_flag"}
LEVER_AND_MUSCLE_UP_IDS = {"tuck_front_hold", "advanced_tuck_front", "one_leg_front", "straddle_front", "full_front", "tuck_back_hold",
                           "advanced_tuck_back", "one_leg_back", "straddle_back", "full_back", "strict_muscleup", "band_muscleup",
                           "negative_muscleup", "jump_muscleup"}
seen = set()
recovery_count = 0
def phase_for(level_plan, number):
    return next(ph for ph in level_plan["phases"] if number <= ph["through_program"])


def weekly_practice_seconds(phase, goal):
    total = 0
    for day in phase["days"]:
        for mid, sets, _, seconds, *_ in day["practice"]:
            # Rep-based drills (handstand exits) add skill work, not hold time.
            if goal in movements[mid]["patterns"] and seconds is not None:
                total += sets * int(str(seconds).split("/")[0])
    return total


def practice_rungs(phase, goal):
    # A readiness check tests the next rung; it is trained only once it appears without the check tempo.
    return {s[0] for d in phase["days"] for s in d["practice"] if goal in movements[s[0]]["patterns"] and s[5] != "readiness_check"}


def easier_chain(mid):
    chain = []
    while movements[mid]["regression_id"]:
        mid = movements[mid]["regression_id"]
        chain.append(mid)
    return chain


def rung_seconds(phase, rung):
    return sum(s[1] * int(str(s[3]).split("/")[0]) for d in phase["days"] for s in d["practice"] if s[0] == rung and s[3] is not None)


def progresses(earlier, later, goal):
    """One variable at a time: more practice time, a harder rung of the same ladder, or (round 5b) more time on the
    hardest shared rung when an easier rung drops out."""
    harder = any(set(easier_chain(m)) & practice_rungs(earlier, goal) for m in practice_rungs(later, goal) - practice_rungs(earlier, goal))
    shared = practice_rungs(earlier, goal) & practice_rungs(later, goal)
    top = max(shared, key=lambda m: len(easier_chain(m)), default=None)
    more_on_top = top is not None and rung_seconds(later, top) > rung_seconds(earlier, top)
    return harder or more_on_top or weekly_practice_seconds(later, goal) >= weekly_practice_seconds(earlier, goal)


for level in catalog["levels"]:
    limits = limits_by_level[level["number"]]
    counts = {"handstand": 0, "human_flag": 0}
    level_plan = plan["levels"][level["number"]-1]
    assert [ph["through_program"] for ph in level_plan["phases"]] == [35, 70, 100]
    # Everyday small practice: handstand in every class (recovery classes drop it), flag twice
    # a week (once in Levels 4-5, beside the planche work: round 5b), and both doses rise from phase to phase.
    for goal, days in (("handstand", 7), ("human_flag", 1 if level["number"] in (4, 5) else 2)):
        phases = level_plan["phases"]
        for earlier, later in zip(phases, phases[1:]):
            assert progresses(earlier, later, goal), (level["number"], goal, later["through_program"])
        assert weekly_practice_seconds(phases[0], goal) < weekly_practice_seconds(phases[1], goal), (level["number"], goal)
        for ph in level_plan["phases"]:
            assert sum(any(goal in movements[s[0]]["patterns"] for s in d["practice"]) for d in ph["days"]) == days
    for p in level["programs"]:
        assert p["title"]["en"].strip() and not p["title"]["en"].startswith("Workout "), p["id"]
        assert p["readiness"]["en"] and p["safety"]["en"], p["id"]
        if p["origin"] != "apk_authentic" and level["number"] <= 4:
            assert p["schedule"]["rpe"] <= (6 if level["number"] == 1 else 7)
        practice = p["practice"]
        day = (p["number"] - 1) % 7 + 1
        expected = phase_for(level_plan, p["number"])["days"][day-1]["practice"]
        if (p["schedule"]["rpe"] is not None and p["schedule"]["rpe"] <= 4) or p.get("morning_recovery"):
            expected = []
        # Authored classes that train levers or muscle-ups leave flag practice out (pulling-tendon load).
        if p["origin"] != "apk_authentic" and {s[0] for s in p.get("main", [])} & LEVER_AND_MUSCLE_UP_IDS:
            expected = [s for s in expected if s[0] not in FLAG_PRACTICE_IDS]
        # Deload weeks halve the practice: one set per side of flag work, half the other sets (round 5b).
        if p["origin"] != "apk_authentic" and "deload" in p["schedule"]["day_type"]:
            expected = [[s[0], 1 if s[0] in FLAG_PRACTICE_IDS else math.ceil(s[1] / 2)] + s[2:] for s in expected]
        # Original-source training classes add the level's source practice (pike push-ups, round 5b).
        if p["origin"] == "apk_authentic" and expected:
            expected = expected + next((d["practice"] for d in level_plan.get("source_practice", []) if d["day"] == day), [])
        assert practice == expected, p["id"]
        assert sum(s[1] for s in practice) <= 8, p["id"]
        for step in practice:
            mid, sets, reps, seconds, rest, tempo, regression = step
            assert mid in movements and mid in asset["movements"]
            # Near-maximal straddle and full flag holds get longer rests.
            assert 1 <= sets <= 5 and rest in ((60, 75, 90) if mid in ("straddle_flag", "full_flag") else (45, 60)), (p["id"], step)
            if mid in ("handstand_exit", "wall_kickup"):
                # Practice is timed holds, except the cartwheel exit and the kick-up to the wall: a few easy reps per side.
                assert seconds is None and str(reps).endswith("/side") and 0 < int(str(reps).split("/")[0]) <= 3, p["id"]
            elif mid == "pike_pushup":
                # The source blocks' pressing practice: a few easy pike push-ups.
                assert p["origin"] == "apk_authentic" and seconds is None and 0 < int(reps) <= 10, p["id"]
            else:
                assert reps is None and 0 < int(str(seconds).split("/")[0]) <= 45, p["id"]
            if "human_flag" in movements[mid]["patterns"]:
                assert str(seconds).endswith("/side"), p["id"]
            fallback = regression or movements[mid]["regression_id"]
            if fallback in new_ids:
                assert fallback in seen, (p["id"], mid, fallback)
            seen.add(mid)
            for goal in counts:
                if goal in movements[mid]["patterns"]:
                    counts[goal] += 1
        if p["origin"] == "apk_authentic" and day in (4, 7):
            assert p["morning_recovery"] is True
            hinge = plan["early_recovery_hinge"]["steps"] if level["number"] >= plan["early_recovery_hinge"]["from_level"] else []
            assert p["main"] == plan["early_recovery"] + hinge and not practice
            assert p["content"]["exercises"], "Original source must stay auditable"
            recovery_count += 1
        else:
            assert not p.get("morning_recovery", False)
            if p["origin"] == "apk_authentic":
                working = p["training_content"]["exercises"]
                assert len(working) == len(p["content"]["exercises"])
                # Kipping comes only after strict strength, which Foundations has not built yet.
                assert not any("kipping" in json.dumps(e).lower() for e in working), p["id"]
                assert sum(len(e["sets"]) for e in working) + sum(s[1] for s in practice) <= limits["main_sets_including_practice"]
                assert p["training_rpe"] == (6 if level["number"] == 1 else 7)
                for e in working:
                    assert 1 <= len(e["sets"]) <= 3
                    for dose in e["sets"]:
                        assert not dose["repetitions_max"]
                        assert (dose["repetitions"] or 0) <= 12
                        assert (dose["duration_seconds"] or 0) <= 30
                        assert dose["break_seconds"] >= 60

        if p["origin"] != "apk_authentic":
            working = p["training_main"]
            original = p["main"]
            assert len(working) == len(original), p["id"]
            assert sum(s[1] for s in working + practice) <= limits["main_sets_including_practice"], p["id"]
            for source, actual in zip(original, working):
                assert len(actual) == 7 and actual[0] == source[0], p["id"]
                assert 1 <= actual[1] <= min(source[1], limits["sets_per_exercise"]), p["id"]
                assert actual[4:] == source[4:], "Rest, tempo and regression must survive the projection"
                for column in (2, 3):
                    assert dose_values(actual[column], actual[1]) == dose_values(source[column], source[1])[:actual[1]], (p["id"], source, actual)
            changed_workouts += working != original
        overlay = p.get("coaching_overlay") or p
        assert len(overlay["cooldown"]) >= 3
        assert sum(movements[s[0]]["category"] in ("static_stretch", "static_cooldown") for s in overlay["cooldown"]) >= 2

    assert min(counts.values()) >= 10, (level["number"], counts)
# The 2.4.0 pro-athlete reviews added the straight side plank, cartwheel exits, the straddle and full flag and
# (round 2) the kick-up to the wall; round 5b the source blocks' pike push-ups.
assert seen == new_ids | {"straight_side_plank", "handstand_exit", "straddle_flag", "full_flag", "wall_kickup", "pike_pushup"}, seen ^ new_ids
prescribed = {step[0] for level in catalog["levels"] for p in level["programs"] for step in p.get("main", [])}
# Since the 2.4.0 reviews the full planche lies beyond Level 6: its readiness check became a straddle planche check.
assert {"straddle_planche", "full_front", "straddle_front", "full_back", "straddle_back", "dragon_flag"} <= prescribed
for level in catalog["levels"]:
    for p in level["programs"]:
        for step in p.get("main", []):
            if step[0] in {"full_planche", "straddle_planche", "full_front", "full_back"}:
                assert step[1] <= 3 and isinstance(step[3], int) and step[3] <= 8, p["id"]
# Source workouts on weekdays 4 and 7 run the recovery routine: 11 in Level 1, 9 in Level 2 (its source block runs
# as classes 41-70 since round 2 of the pro-athlete reviews) and 5 in Level 3.
assert recovery_count == 25, recovery_count

assert changed_workouts > 0
# These are one-sided stretches: their visible instructions must disambiguate the dose.
for mid in ("forearm_stretch", "chest_stretch", "hip_flexor_stretch", "hamstring_stretch", "quad_stretch", "calf_stretch", "supine_twist", "adductor_stretch", "ninety_ninety_hip_stretch"):
    assert "Each timed set is for one side" in movements[mid]["description"]["en"], mid

figure = Figure(asset["figure"])
cache = {}
def template(name):
    return Template(name, asset["templates"][name], asset["poses"], cache)
def distance(a, b):
    return math.hypot(a[0]-b[0], a[1]-b[1])
# Sample between keyframes too: endpoints alone missed floating feet in the old asset.
contacts = {
    # Round 5 of the pro reviews: push-up hands under the shoulders, rows that reach the bar, the tuck row's feet
    # leaving the floor as the hips rise, and the low-bar transition from a step under the bar.
    "pushup": {"foot_near": (146,101), "hand_near": (101,105)},
    "incline_pushup": {"foot_near": (105,101), "hand_near": (60,82)},
    "assisted_dip": {"foot_near": (106,74), "hand_near": (100,50)},
    "low_bar_transition": {"foot_near": (104,86), "hand_near": (100,66)},
    "row": {"foot_near": (106,104), "hand_near": (80,66)},
    "high_row": {"foot_near": (113,104), "hand_near": (100,60)},
    "feet_elevated_row": {"foot_near": (120,90), "hand_near": (80,66)},
    "assisted_pullup": {"foot_near": (108,104), "hand_near": (100,58)},
    "tuck_front_row": {"hand_near": (80,66)},
}
for name, pins in contacts.items():
    motion = template(name)
    for i in range(201):
        joints = motion.pose_at(i/200, figure)
        for joint, target in pins.items():
            assert distance(joints[joint], target) < 0.2, (name, i, joint, joints[joint])
# Handstand guides retain loaded contacts throughout interpolation, with straight
# supporting arms, head clearance, and an actual release from the wall.
for name in ("pike_handstand_hold", "wall_handstand", "handstand_toe_pull", "handstand_balance"):
    motion = template(name)
    for i in range(201):
        joints = motion.pose_at(i / 200, figure)
        hand = joints["hand_near"]
        assert abs(hand[1] - 105) < 0.05, (name, i, hand)
        assert abs(distance(joints["shoulder"], hand) - 26) < 0.05, (name, i)
        assert joints["head"][1] + figure.head_radius < 105, (name, i)
        if name == "pike_handstand_hold":
            assert distance(joints["foot_near"], (121, 59)) < 0.05
            assert abs(joints["hip"][0] - hand[0]) < 0.05
        elif name == "wall_handstand":
            assert abs(joints["toe_near"][0] - 150) < 0.05
        else:
            assert joints["toe_near"][0] <= 150.05
assert template("handstand_toe_pull").pose_at(0.5, figure)["toe_near"][0] < 148
side = template("kneeling_side_plank").pose_at(0, figure)
assert abs(side["shoulder"][0] - side["elbow_near"][0]) < 0.05
assert abs(side["elbow_near"][1] - 104) < 0.1
assert abs(side["hand_near"][1] - 104) < 0.1
assert side["shoulder"][1] < side["hip"][1] < side["knee_near"][1]

for name in ("dragon_flag", "dragon_negative"):
    motion = template(name)
    for i in range(101):
        j = motion.pose_at(i/100, figure)
        body = distance(j["neck"],j["foot_near"])
        assert abs(body-51) < 0.05, (name, i, body)
for i in range(101):
    j = template("planche_lean").pose_at(i/100,figure)
    assert abs(distance(j["shoulder"],j["hand_near"])-26) < 0.01
    assert abs(j["toe_near"][1]-106) < 0.1
session_count = sum(len(level["programs"]) for level in catalog["levels"])
assert session_count == 700, session_count
print(f"Morning-class checks passed: {session_count} named sessions, {recovery_count} early recovery classes, {changed_workouts} authored workload projections, flexibility, handstand supports and sampled motion contacts.")

