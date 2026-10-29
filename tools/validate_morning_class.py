"""Checks for the delivered classes' practice part, cool-downs and the motion contacts of the animations they use.

Round 9 (tools/build_curriculum.py) writes each class's practice itself; this checks what the athlete sees: the practice
part holds short, easy handstand and balance work inside the level's set budget, every movement has its animation, and
the motion templates keep their contacts.
"""
import json
import math
from pathlib import Path
from preview_animations import Figure, Template

ROOT = Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / "generated/curriculum_v2.json").read_text(encoding="utf-8"))
asset = json.loads((ROOT / "app/src/main/assets/exercise_animations.json").read_text(encoding="utf-8"))
movements = {m["id"]: m for m in catalog["movement_library"]}
progression = json.loads((ROOT / "curriculum/class_progression.json").read_text(encoding="utf-8"))
limits_by_level = {p["level"]: p for p in progression["levels"]}
assert list(limits_by_level) == list(range(1, 11))
budgets = [p["main_sets_including_practice"] for p in progression["levels"]]
assert budgets == sorted(budgets) and budgets[0] < budgets[-1]

practice_classes = 0
for level in catalog["levels"]:
    limits = limits_by_level[level["number"]]
    for p in level["programs"]:
        assert p["title"]["en"].strip() and not p["title"]["en"].startswith("Workout "), p["id"]
        assert p["readiness"]["en"] and p["safety"]["en"], p["id"]
        assert "training_main" not in p and "training_content" not in p and "morning_recovery" not in p, p["id"]
        practice = p["practice"]
        # Easy classes and exam days carry no practice part: an exam tests the handstand in its main work.
        if p["schedule"]["rpe"] <= 4 or p["schedule"]["day_type"].startswith("exam"):
            assert practice == [], p["id"]
        practice_classes += bool(practice)
        # The Full Battle (L10:100) tests the basics, outside the class budget.
        assert p["schedule"]["day_type"] == "exam_level_full_battle" or sum(s[1] for s in practice + p["main"]) <= limits["main_sets_including_practice"], p["id"]
        assert sum(s[1] for s in practice) <= 4, p["id"]
        for step in practice + p["main"] + p["warmup"]:
            assert step[0] in movements and step[0] in asset["movements"], (p["id"], step[0])
        for step in practice:
            mid, sets, reps, seconds, rest, tempo, regression = step
            # The practice part is the handstand line: timed, easy holds with short rests.
            assert "handstand" in movements[mid]["patterns"] and tempo == "easy technique", (p["id"], step)
            assert reps is None and 0 < int(str(seconds).split("/")[0]) <= 90 and 1 <= sets <= 3 and rest <= 60, (p["id"], step)
        for step in p["main"]:
            if "human_flag" in movements[step[0]]["patterns"] and step[3] is not None:
                assert str(step[3]).endswith("/side"), (p["id"], step)
        assert len(p["cooldown"]) >= 3
        assert sum(movements[s[0]]["category"] in ("static_stretch", "static_cooldown") for s in p["cooldown"]) >= 2, p["id"]
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
assert session_count == 1000, session_count
print(f"Class checks passed: {session_count} classes, {practice_classes} with a practice part, every movement animated, one-sided stretches, handstand supports and sampled motion contacts.")

