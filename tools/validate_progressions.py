"""Round 11 plan checks: the program's frame, its load rules and its skill dates (docs/CURRICULUM_PLAN.md).

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
assert len(TARGETS) == 40, len(TARGETS)
# The app's belt ladders (SkillBook.DEFAULT_FAMILIES), easiest rung first: a level on a harder rung holds on the easier.
book = (ROOT / "app/src/main/java/com/justdataplease/streetgym/data/skills/SkillBook.kt").read_text(encoding="utf-8")
BELT_LADDERS = [re.findall(r'"(\w+)"', rungs) for rungs in re.findall(r'SkillFamilySpec\("\w+", "[^"]+", SkillGroup\.\w+,\s*listOf\(([^)]*)\)', book)]
assert sorted(r for rungs in BELT_LADDERS for r in rungs) == sorted(TARGETS), BELT_LADDERS
BELT_SETS = [10, 20, 30, 30, 30]

FROZEN_EXAMS = {1: [20, 40, 60, 81, 100], 2: [20, 40, 62, 84, 100], 3: [20, 40, 64, 84, 100]}
FROZEN_EXAMS.update({L: [13, 34, 55, 76, 100] for L in range(4, 11)})
LADDERS = {
    "front": ["tuck_front_hold", "advanced_tuck_front", "one_leg_front", "straddle_front", "full_front"],
    "back": ["tuck_back_hold", "advanced_tuck_back", "straddle_back", "full_back"],
    "planche": ["tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche"],
    "flag": ["tuck_flag", "straddle_flag", "full_flag"],
}
STRAIGHT_ARM = {r for rungs in LADDERS.values() for r in rungs} | {"planche_lean", "frog_stand", "german_hang"}
TUCK_OR_HARDER_PLANCHE = set(LADDERS["planche"])
FLAGS = set(LADDERS["flag"]) | {"assisted_tuck_flag", "vertical_flag", "flag_negative"}
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


# 1. The frame: ten levels of 100 classes, the frozen exam days, an easy class after every exam from Level 3 (a
#    re-entry, or the week's recovery), at most 55 minutes.
for L in range(1, 11):
    exams = [p["number"] for p in classes if p["level"] == L and p["schedule"]["day_type"].startswith("exam")]
    assert exams == FROZEN_EXAMS[L], (L, exams)
for a, b in zip(classes, classes[1:]):
    if a["schedule"]["day_type"].startswith("exam") and (a["level"] >= 3 or a["schedule"]["day"] == 7):
        assert b["schedule"]["day_type"] in ("reentry_recovery", "recovery"), f"{where(b)} must be easy after the exam"
for p in classes:
    s = p["schedule"]
    assert 15 <= s["minutes"] <= 55, f"{where(p)} runs {s['minutes']} minutes"
    assert s["day"] == (p["number"] - 1) % 7 + 1 and s["week"] == (p["number"] - 1) // 7 + 1, where(p)
    lim = limits[p["level"]]
    # The Full Battle tests the basics, outside the set budget.
    assert s["day_type"] == "exam_level_full_battle" or sum(st[1] for st in work(p)) <= lim["main_sets_including_practice"], \
        f"{where(p)} is over the set budget"
    assert all(st[1] <= lim["sets_per_exercise"] for st in work(p)), f"{where(p)} has a step over {lim['sets_per_exercise']} sets"
    for st in work(p):
        cap = REP_CAP.get(st[0])
        if cap and st[2] is not None:
            assert value(st) <= cap, f"{where(p)} {st[0]} {value(st)} is over its rep cap {cap}"

# 2. The fixed week from Level 2: elbow-heavy days (pull, planche, bar) between arm-light ones (legs, handstand,
#    basics), the same weekday keeping the same role in every build class.
ROLES = {1: ("front_pull", "front_onearm"), 2: ("legs_core",), 3: ("planche_push",), 4: ("handstand_press",),
         5: ("back_flag",), 6: ("basics", "battle", "statics_chain"), 7: ("recovery",)}
for p in classes:
    if p["level"] >= 2:
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
for a, b in zip(classes, classes[1:]):
    # Exams included: the day before an exam holds no flag, the day after is easy (the statics review, round 11).
    assert not (hold_sets(a, FLAGS) and hold_sets(b, TUCK_OR_HARDER_PLANCHE)), f"{where(b)} holds the planche the day after the flag"
    assert not (hold_sets(a, TUCK_OR_HARDER_PLANCHE) and hold_sets(b, FLAGS)), f"{where(b)} holds the flag the day after the planche"

# 7. Every week covers the hinge, and from Level 2 overhead pressing and compression.
HINGE = {"single_leg_rdl", "glute_bridge", "single_leg_bridge", "sliding_leg_curl", "nordic_negative", "nordic_curl"}
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


# 10. Round 11 load rules (the S&C review). Elbow-load sets (straight-arm pushing, back-lever-type pulling, the flag,
#     the muscle-up, the Hefesto line, one-arm and archer pulling, free shoulder extension and the hard push-up
#     variants) stay at 6 a class outside exams (7 on the bar day), and classes with 5 or more never follow each other.
#     Holds are never touches: 3 s for the full and straddle shapes, the V-sit, the advanced-tuck planche and the one-arm
#     balance line, 5 s for the rest. A skill at the top of its ladder never goes more than 14 classes without a set (16
#     across a level's taper and exam).
#     The Hefesto never shares a class with the planche, nor the one-arm handstand lead with the planche lead. From
#     Level 4, any 21 classes hold at most 6 lighter, primer, easy or re-entry classes.
ELBOW = (set(LADDERS["planche"]) | set(LADDERS["back"]) | set(LADDERS["flag"]) |
         {"assisted_supinated_german_hang", "supinated_german_hang", "assisted_korean_dip", "assisted_partial_hefesto", "assisted_hefesto", "hefesto",
          "skin_the_cat", "flag_negative",
          "strict_muscleup", "band_muscleup", "negative_muscleup", "archer_pullup", "assisted_one_arm_pullup", "one_arm_pullup_negative",
          "one_arm_pullup", "german_hang", "skin_cat_partial", "pseudo_planche_pushup", "one_arm_pushup", "straight_bar_dip"})
HEFESTO = {"assisted_korean_dip", "assisted_partial_hefesto", "assisted_hefesto", "hefesto"}
OAH = {"handstand_weight_shift", "fingertip_one_arm_handstand", "straddle_one_arm_handstand", "one_arm_handstand"}
MIN3 = {"full_front", "full_back", "full_planche", "full_flag", "straddle_front", "straddle_back", "straddle_planche", "straddle_flag",
        "v_sit", "advanced_tuck_planche", "one_leg_front", "handstand_weight_shift", "fingertip_one_arm_handstand",
        "straddle_one_arm_handstand", "one_arm_handstand"}
NOT_HOLDS = {"recovery", "mobility", "static_stretch", "static_cooldown", "dynamic_warmup", "activation"}


def elbow(p):
    return sum(st[1] for st in work(p) if st[0] in ELBOW)


for p in classes:
    if not p["schedule"]["day_type"].startswith("exam"):
        cap = 7 if p["schedule"]["day_type"].endswith("back_flag") else 6
        assert elbow(p) <= cap, f"{where(p)} has {elbow(p)} elbow-load sets"
        assert not (hold_sets(p, HEFESTO) and hold_sets(p, TUCK_OR_HARDER_PLANCHE)), f"{where(p)} holds the Hefesto and the planche"
        assert not (hold_sets(p, OAH) >= 2 and hold_sets(p, TUCK_OR_HARDER_PLANCHE) >= 2), f"{where(p)} leads the one-arm handstand and the planche"
    for st in work(p):
        if st[3] is not None and movements[st[0]]["category"] not in NOT_HOLDS and st[0] != "handstand_balance":
            assert value(st) >= (3 if st[0] in MIN3 else 5), f"{where(p)} holds {st[0]} for {value(st)} s"
for a, b in zip(classes, classes[1:]):
    if not (a["schedule"]["day_type"].startswith("exam") or b["schedule"]["day_type"].startswith("exam")):
        assert not (elbow(a) >= 5 and elbow(b) >= 5), f"{where(b)} follows a heavy elbow-load class"
REDUCED = re.compile(r"^(deload_|light_|primer|easy_|reentry)")
for i in range(len(classes) - 20):
    win = classes[i:i + 21]
    if win[0]["level"] >= 4 and win[0]["level"] == win[-1]["level"]:
        assert sum(1 for p in win if REDUCED.match(p["schedule"]["day_type"])) <= 6, f"{where(win[0])} starts 21 classes with too many lighter ones"
gaps = defaultdict(list)
for idx, p in enumerate(classes):
    trained = {st[0] for st in work(p)}
    for rungs in BELT_LADDERS:
        reached = [r for r in rungs if r in first and first[r][0] <= idx]
        if reached:
            gaps[reached[-1]].append(idx if reached[-1] in trained else None)
for sid, seen in gaps.items():
    idxs = [i for i in seen if i is not None]
    for a, b in zip(idxs, idxs[1:]):
        # Across a level's taper (lighter week, primer, level exam, re-entry) a line may wait 16 classes.
        limit = 16 if classes[a]["level"] != classes[b]["level"] else 14
        assert b - a <= limit, f"{sid} goes {b - a} classes without a set before {where(classes[b])}"


# 11. Review 2 and 3 (the three coaches). Exams test what the athlete trained: no item under the best set of the 21
#     classes before it, at most two items above it and those by one step. Before a new rung, the rung below gets about two sets a week (11 in the 40 classes before its first
#     set) on every ladder, the new lines' too. Straight-bar dips reach 10 before the Korean dip (the Hefesto's gate).
#     From Level 4, a movement's sets and dose never rise together from one class of a day type to the next. Level 1
#     circuits run whole rounds.
ALL_LADDERS = dict(LADDERS, oah=["straddle_handstand", "handstand_weight_shift", "fingertip_one_arm_handstand", "straddle_one_arm_handstand",
                                 "one_arm_handstand"],
                   shrimp=["assisted_partial_shrimp", "assisted_shrimp", "floating_shrimp", "assisted_held_shrimp", "shrimp_squat"],
                   hefesto=["assisted_supinated_german_hang", "assisted_korean_dip", "assisted_partial_hefesto", "assisted_hefesto", "hefesto"])
R_STEPS = [1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 35, 40]
S_STEPS = [5, 8, 10, 12, 15, 20, 25, 30, 40, 45, 50, 60, 75, 90, 120]


def one_step(v, seconds, level):
    """The next dose up: a palette step in Levels 1-2, later +1 (+2 over 12 reps or 10 s, +5 over 20 reps or 30 s)."""
    if level <= 2:
        return next((x for x in (S_STEPS if seconds else R_STEPS) if x > v), v + 1)
    return v + (1 if v <= (10 if seconds else 12) else 2 if v <= (30 if seconds else 20) else 5)


for i, p in enumerate(classes):
    if not p["schedule"]["day_type"].startswith("exam") or p["schedule"]["day_type"] == "exam_level_full_battle":
        continue
    trained = defaultdict(int)
    for q in classes[max(0, i - 21):i]:
        if not q["schedule"]["day_type"].startswith("exam"):
            for st in work(q):
                if st[5] != "assessment":
                    trained[st[0]] = max(trained[st[0]], value(st))
    above = 0
    for st in p["main"]:
        if st[5] == "assessment" and trained[st[0]]:
            assert value(st) >= trained[st[0]], f"{where(p)} tests {st[0]} at {value(st)}, under the {trained[st[0]]} it trained"
            if value(st) > trained[st[0]]:
                above += 1
                assert value(st) <= one_step(trained[st[0]], st[3] is not None, p["level"]),                     f"{where(p)} tests {st[0]} at {value(st)}, more than a step over the {trained[st[0]]} it trained"
    assert above <= 2, f"{where(p)} tests {above} items above what was trained"
debut = {}
for i, p in enumerate(classes):
    for st in work(p):
        debut.setdefault(st[0], i)
for name, rungs in ALL_LADDERS.items():
    for below, rung in zip(rungs, rungs[1:]):
        i = debut[rung]
        sets = sum(st[1] for q in classes[max(0, i - 40):i] for st in work(q) if st[0] == below)
        assert sets >= 11, f"{below} gets {sets} sets in the 40 classes before {rung} starts at {where(classes[i])}"
sbd = [value(st) for q in classes[:debut["assisted_korean_dip"]] for st in work(q) if st[0] == "straight_bar_dip"]
assert max(sbd) >= 10, f"straight-bar dips reach {max(sbd)} before the Korean dip"
last_seen = {}
for p in classes:
    t = p["schedule"]["day_type"]
    if p["level"] < 4 or re.match(r"^(deload_|light_|exam|reentry|primer|easy_|recovery)", t):
        continue
    count = defaultdict(int)
    for st in work(p):
        count[st[0]] += 1
    for st in work(p):
        if count[st[0]] > 1 or st[5] == "assessment":
            continue
        key = (t.replace("front_onearm", "front_pull"), st[0])
        if key in last_seen:
            n0, v0 = last_seen[key]
            assert not (st[1] > n0 and value(st) > v0), f"{where(p)} raises {st[0]}'s sets and dose together ({n0}x{v0} to {st[1]}x{value(st)})"
        last_seen[key] = (st[1], value(st))
for p in classes:
    if p["schedule"]["day_type"].startswith("circuit"):
        rounds = defaultdict(int)
        for st in p["main"]:
            rounds[st[0]] += 1
        assert len({v for v in rounds.values() if v > 1}) <= 1, f"{where(p)} cuts a circuit round"

print(f"Plan checks passed: frozen exams with a re-entry after each, {len(TARGETS)} skills each reaching Elite at least once "
      f"({len(elite_in_plan & {s for s in TARGETS if reach[s] < TARGETS[s][4]})} through a harder rung), no progression record beside a harder rung, "
      f"every full shape tested at L9:100, {len(debuts)} lever, planche and flag rungs at least 60 classes apart, "
      f"easy days easy, lighter exam weeks, weekly hinge, pressing and compression, no stacked straight-arm, elbow, planche or flag days, "
      f"elbow-load caps, minimum holds, no skill left more than 14 classes, exams at the trained dose (two items a step over at most), two sets a week of the "
      f"rung below before each new rung, the Hefesto's dip gate, no sets and dose rising together and whole circuit rounds.")
