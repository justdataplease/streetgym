"""Round 9 plan checks: the program's frame, its load rules and its skill dates (docs/CURRICULUM_PLAN.md).

Reads the assembled catalog and the app's own skill targets (SkillStandards.kt), so the curriculum and the belts the
app awards cannot drift apart. Each rule names the class that breaks it.
"""
import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / "generated/curriculum_v2.json").read_text(encoding="utf-8"))
movements = {m["id"]: m for m in catalog["movement_library"]}
limits = {p["level"]: p for p in json.loads((ROOT / "curriculum/class_progression.json").read_text(encoding="utf-8"))["levels"]}
kotlin = (ROOT / "app/src/main/java/com/justdataplease/streetgym/data/skills/SkillStandards.kt").read_text(encoding="utf-8")
TARGETS = {sid: [int(x) for x in nums.split(",")[1:]]
           for sid, nums in re.findall(r'"(\w+)" to (?:reps|repsSide|hold|holdSide|attempts)\(([\d, ]+)\)', kotlin)}
TARGETS = {k: [int(x) for x in v] for k, v in TARGETS.items()}
assert len(TARGETS) == 37, len(TARGETS)
# The app's belt ladders (SkillBook.DEFAULT_FAMILIES), easiest rung first: a level on a harder rung holds on the easier.
book = (ROOT / "app/src/main/java/com/justdataplease/streetgym/data/skills/SkillBook.kt").read_text(encoding="utf-8")
BELT_LADDERS = [re.findall(r'"(\w+)"', rungs) for rungs in re.findall(r'SkillFamilySpec\("\w+", "[^"]+", SkillGroup\.\w+,\s*listOf\(([^)]*)\)', book)]
assert sorted(r for rungs in BELT_LADDERS for r in rungs) == sorted(TARGETS), BELT_LADDERS
BELT_SETS = [10, 20, 30, 30, 30]

FROZEN_EXAMS = {1: [20, 40, 60, 81, 100], 2: [20, 40, 62, 84, 100], 3: [20, 40, 64, 84, 100]}
FROZEN_EXAMS.update({L: [13, 34, 55, 76, 100] for L in range(4, 11)})
LADDERS = {
    "front": ["tuck_front_hold", "advanced_tuck_front", "straddle_front", "full_front"],
    "back": ["tuck_back_hold", "advanced_tuck_back", "straddle_back", "full_back"],
    "planche": ["tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche"],
    "flag": ["tuck_flag", "straddle_flag", "full_flag"],
}
STRAIGHT_ARM = {r for rungs in LADDERS.values() for r in rungs} | {"planche_lean", "frog_stand", "german_hang"}
TUCK_OR_HARDER_PLANCHE = set(LADDERS["planche"])
FLAGS = set(LADDERS["flag"]) | {"assisted_tuck_flag"}
HEAVY_ELBOW = {"pullup", "archer_pullup", "one_arm_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative", "strict_muscleup",
               "band_muscleup", "chest_to_bar", "negative_muscleup", "parallel_dip", "eccentric_dip"}
EASY_TYPES = re.compile(r"^(recovery|reentry_recovery|primer_easy|easy_)")
REP_CAP = {"pushup": 50, "squat": 40, "parallel_dip": 30, "pullup": 21, "hanging_leg_raise": 15, "toes_to_bar": 15}
FULL_SHAPES = ["full_front", "full_back", "full_planche", "full_flag", "one_arm_pullup"]

classes = [p for level in sorted(catalog["levels"], key=lambda x: x["number"]) for p in sorted(level["programs"], key=lambda x: x["number"])]
assert len(classes) == 1000


def value(step):
    v = step[2] if step[2] is not None else step[3]
    return int(str(v).split("/")[0]) if v is not None and str(v).split("/")[0].isdigit() else 0


def where(p):
    return f"L{p['level']}:{p['number']:03d}"


def work(p):
    return p["practice"] + p["main"]


def hold_sets(p, ids):
    return sum(s[1] for s in work(p) if s[0] in ids)


# 1. The frame: ten levels of 100 classes, the frozen exam days, a re-entry after every exam, at most 55 minutes.
for L in range(1, 11):
    exams = [p["number"] for p in classes if p["level"] == L and p["schedule"]["day_type"].startswith("exam")]
    assert exams == FROZEN_EXAMS[L], (L, exams)
for a, b in zip(classes, classes[1:]):
    if a["schedule"]["day_type"].startswith("exam"):
        assert b["schedule"]["day_type"] == "reentry_recovery", f"{where(b)} must be the re-entry after the exam"
for p in classes:
    s = p["schedule"]
    assert 15 <= s["minutes"] <= 55, f"{where(p)} runs {s['minutes']} minutes"
    assert s["day"] == (p["number"] - 1) % 7 + 1 and s["week"] == (p["number"] - 1) // 7 + 1, where(p)
    lim = limits[p["level"]]
    assert sum(st[1] for st in work(p)) <= lim["main_sets_including_practice"], f"{where(p)} is over the set budget"
    assert all(st[1] <= lim["sets_per_exercise"] for st in work(p)), f"{where(p)} has a step over {lim['sets_per_exercise']} sets"
    for st in work(p):
        cap = REP_CAP.get(st[0])
        if cap and st[2] is not None:
            assert value(st) <= cap, f"{where(p)} {st[0]} {value(st)} is over its rep cap {cap}"

# 2. The fixed week from Level 3: the same weekday keeps the same day role in every build class.
ROLES = {1: ("front_pull", "front_onearm"), 2: ("planche_push",), 3: ("legs_core",), 4: ("back_bar", "back_muscleup"),
         5: ("handstand_flag_press", "handstand_press"), 6: ("basics", "battle", "statics_chain"), 7: ("recovery",)}
for p in classes:
    if p["level"] >= 3:
        t = p["schedule"]["day_type"]
        role = t.removeprefix("deload_").removeprefix("light_")
        if role in {r for rs in ROLES.values() for r in rs}:
            assert role in ROLES[p["schedule"]["day"]], f"{where(p)} is a {role} class on weekday {p['schedule']['day']}"

# 3. Easy classes are easy: RPE 4 or lower, no practice part, no assessment and no more than two sets of anything.
for p in classes:
    if EASY_TYPES.match(p["schedule"]["day_type"]):
        assert p["schedule"]["rpe"] <= 4 and not p["practice"], where(p)
        assert all(st[5] != "assessment" and st[1] <= 2 for st in p["main"]), where(p)

# 4. The lighter exam week: each lighter class keeps at most 70% of the sets of the same day a week earlier.
by = {(p["level"], p["number"]): p for p in classes}
for p in classes:
    if p["schedule"]["day_type"].startswith("deload_"):
        before = by.get((p["level"], p["number"] - 7))
        if before and not before["schedule"]["day_type"].startswith(("deload_", "exam", "reentry", "primer", "light_")):
            assert sum(st[1] for st in p["main"]) <= 0.7 * sum(st[1] for st in before["main"]) + 1, where(p)

# 5. Exams: assessments in the main part only, at most 12 tests (the Full Battle's 20 stations aside).
for p in classes:
    if p["schedule"]["day_type"].startswith("exam"):
        tests = [st for st in p["main"] if st[5] == "assessment"]
        assert not p["practice"] and tests, where(p)
        if p["schedule"]["day_type"] != "exam_level_full_battle":
            assert len(tests) <= 12, f"{where(p)} has {len(tests)} tests"
            assert len({st[0] for st in tests}) == len(tests), f"{where(p)} tests a movement twice"

# 6. Load between days: straight-arm classes never three in a row; heavy elbow days never back to back; the planche and
#    the flag never on consecutive days, and never in one class outside an exam.
def straight_arm_day(p):
    # Easy classes hold one set of each line at half the dose: touches, not straight-arm work.
    return not EASY_TYPES.match(p["schedule"]["day_type"]) and hold_sets(p, STRAIGHT_ARM) >= 3


def heavy_elbow_day(p):
    return hold_sets(p, HEAVY_ELBOW) >= 8


for a, b, c in zip(classes, classes[1:], classes[2:]):
    assert not (straight_arm_day(a) and straight_arm_day(b) and straight_arm_day(c)), f"{where(c)} ends three straight-arm classes"
for a, b in zip(classes, classes[1:]):
    assert not (heavy_elbow_day(a) and heavy_elbow_day(b)), f"{where(b)} follows a heavy elbow class"
    if not a["schedule"]["day_type"].startswith("exam") and not b["schedule"]["day_type"].startswith("exam"):
        assert not (hold_sets(a, TUCK_OR_HARDER_PLANCHE) >= 2 and hold_sets(b, TUCK_OR_HARDER_PLANCHE) >= 2), f"{where(b)} repeats the planche"
        assert not (hold_sets(a, FLAGS) and hold_sets(b, FLAGS)), f"{where(b)} repeats the flag"
for p in classes:
    if not p["schedule"]["day_type"].startswith("exam"):
        assert not (hold_sets(p, FLAGS) and hold_sets(p, TUCK_OR_HARDER_PLANCHE) >= 2), f"{where(p)} holds the flag and the planche"

# 7. Every week covers the hinge, and from Level 2 overhead pressing and compression.
HINGE = {"single_leg_rdl", "glute_bridge", "single_leg_bridge", "nordic_negative", "nordic_curl"}
OVERHEAD = {"pike_pushup", "elevated_pike_pushup", "wall_hspu_negative", "wall_hspu", "deficit_wall_hspu", "freestanding_hspu_negative"}
COMPRESSION = {"pancake_compression", "seated_pike_lift", "hanging_leg_raise", "toes_to_bar", "lsit", "v_sit", "tuck_lsit", "one_leg_lsit"}
for i in range(len(classes) - 6):
    week = classes[i:i + 7]
    if week[0]["level"] != week[-1]["level"]:
        continue
    ids = {st[0] for p in week for st in work(p)}
    for name, group in (("hinge", HINGE), ("overhead press", OVERHEAD), ("compression", COMPRESSION)):
        if week[0]["level"] >= 2 or name == "hinge":  # pressing and compression weekly from Level 2
            assert ids & group, f"{where(week[0])}-{week[-1]['number']:03d} has no {name}"

# 8. The ladders: each rung starts at least 60 classes after the one below, never in a lighter class, never two new
#    straight-arm rungs in one week, at most three in a level; a build dose never falls under 80% of the best so far.
first = {}
for idx, p in enumerate(classes):
    for st in work(p):
        first.setdefault(st[0], (idx, p))
for name, rungs in LADDERS.items():
    starts = [first[r][0] for r in rungs]
    assert starts == sorted(starts), name
    for (a, b), r in zip(zip(starts, starts[1:]), rungs[1:]):
        assert b - a >= 60, f"{r} starts {b - a} classes after the rung below"
debuts = sorted((first[r][0], r) for rs in LADDERS.values() for r in rs)
for (a, ra), (b, rb) in zip(debuts, debuts[1:]):
    pa, pb = classes[a], classes[b]
    assert (pa["level"], pa["schedule"]["week"]) != (pb["level"], pb["schedule"]["week"]), f"{ra} and {rb} start in the same week"
per_level = defaultdict(int)
for i, r in debuts:
    p = classes[i]
    per_level[p["level"]] += 1
    assert not re.match(r"^(deload_|light_|exam|reentry|primer|easy_)", p["schedule"]["day_type"]), f"{r} starts in {where(p)}"
assert max(per_level.values()) <= 3, dict(per_level)
best = {}
for p in classes:
    if re.match(r"^(deload_|light_|exam|reentry|primer|easy_|recovery)", p["schedule"]["day_type"]):
        continue
    top = defaultdict(int)
    for st in work(p):
        if st[5] != "assessment":  # a record set is not a working dose
            top[st[0]] = max(top[st[0]], value(st))
    for mid, v in top.items():
        # Compared within the same day role: the basics day and the bar day run their lever touches at 60% on purpose.
        key = (p["schedule"]["day_type"], mid)
        if mid in TARGETS and v and best.get(key) and v < 0.8 * best[key]:
            raise AssertionError(f"{where(p)} doses {mid} at {v}, under 80% of its best {best[key]}")
        if mid in TARGETS:
            best[key] = max(best.get(key, 0), v)

# 9. Skills: every one of the 37 belted skills is trained and reaches its Elite target at least once inside the
#    program, or holds Elite through a harder rung of its ladder there; a lever, planche or flag progression peaks
#    while it is trained, never as a record beside a harder rung; every full shape is first tested by the Level 9 exam;
#    Level 10 holds the records.
reach = defaultdict(int)
at_target = {sid: [0] * 5 for sid in TARGETS}
for p in classes:
    for st in work(p):
        if st[0] in TARGETS and st[5] != "readiness_check":
            reach[st[0]] = max(reach[st[0]], value(st))
            at_target[st[0]] = [c + (st[1] if value(st) >= t else 0) for c, t in zip(at_target[st[0]], TARGETS[st[0]])]
    if not p["schedule"]["day_type"].startswith("exam"):
        ids = {st[0] for st in p["main"]}
        for st in p["main"]:
            for rungs in LADDERS.values():
                if st[5] == "assessment" and st[0] in rungs:
                    harder = ids & set(rungs[rungs.index(st[0]) + 1:])
                    assert not harder, f"{where(p)} has a {st[0]} record beside {sorted(harder)}"
elite_own = {sid for sid, counts in at_target.items() if all(c >= need for c, need in zip(counts, BELT_SETS))}
elite_in_plan = {r for rungs in BELT_LADDERS for i, r in enumerate(rungs) if elite_own & set(rungs[i:])}
for sid, t in TARGETS.items():
    assert sid in first, f"{sid} is never trained"
    assert reach[sid] >= t[4] or sid in elite_in_plan, f"{sid} never reaches its Elite target {t[4]} (best {reach[sid]})"
l9 = {st[0] for st in by[(9, 100)]["main"] if st[5] == "assessment"}
assert set(FULL_SHAPES) <= l9, set(FULL_SHAPES) - l9
assert all(TARGETS[s][0] <= value(st) for st in by[(9, 100)]["main"] for s in FULL_SHAPES if st[0] == s)
records = [st for p in classes if p["level"] == 10 for st in p["main"] if st[5] == "assessment" and st[0] in TARGETS and value(st) == TARGETS[st[0]][4]]
assert records, "Level 10 has no record sets"

print(f"Plan checks passed: frozen exams with a re-entry after each, {len(TARGETS)} skills each reaching Elite at least once "
      f"({len(elite_in_plan & {s for s in TARGETS if reach[s] < TARGETS[s][4]})} through a harder rung), no progression record beside a harder rung, "
      f"every full shape tested at L9:100, {len(debuts)} lever, planche and flag rungs at least 60 classes apart, "
      f"easy days easy, lighter exam weeks, weekly hinge, pressing and compression, and no stacked straight-arm, elbow, planche or flag days.")
