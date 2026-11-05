"""Checks one level of the hand-written program (curriculum/handwritten) against everything the app checks.

The program is written by hand since 2.12.0 (the generator is off), so this is how an edit is checked before
tools/handwritten_program.py and the release pipeline run. It reads all ten level files (the validators look across
levels), converts them in memory to the app's catalog (on the frame of generated/curriculum_v2.json: ids, rests,
readiness and safety texts), and reports every failure, not only the first:
  A. the file format: sections, exam days, Skill-practice ids, D: lines, banned words, Changes and verdict
  B. the class limits: set budget, sets per exercise, 4+ Skill/Strength items, 3+ warm-up and cool-down items, minutes
     against the app's own timing
  C. the app's validators: validate_curriculum.ps1, validate_progressions.py and validate_morning_class.py (every failed
     check, not only the first), the effort test (WorkoutEffortTest) and the placement's level tests (PlacementTest)
  D. the panel's rules (warm-ups, class shape, one new movement a class, flat doses...)
and prints the level's belts and the doses at the level line. MUST lines block a release; PANEL lines are the coaches'
rules (keep one only with a reason in the level's Changes). Nothing here writes a class.

usage: python tools/check_handwritten.py <level> [--fast]   (--fast skips the PowerShell validator; needs Pillow)
"""
import ast
import collections
import contextlib
import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = str(Path(__file__).resolve().parents[1])
SECTIONS = (("WU", "warmup"), ("SKILL", "practice"), ("STR", "main"), ("CD", "cooldown"))
DEFAULT_REST = {"warmup": 10, "practice": 120, "main": 90, "cooldown": 10}
REHEARSE = {"skill_foundation", "skill", "skill_strength", "advanced_skill", "advanced_strength", "mastery_skill"}

HEAD = re.compile(r"^(\d{3}) ([a-z_]+) · (.+?) · (\d+) min · RPE (\d+)\s*$")
SEC = re.compile(r"^ (WU|SKILL|STR|CD|D): ?(.*)$")
ITEM = re.compile(r"^(NEW:)?([a-z0-9_]+) (\d+)x(\d+)(?:-(\d+))?(s)?(/side)?(?: \[([^\]]+)\])?( TEST)?$")
SECTIONS = (("WU", "warmup"), ("SKILL", "practice"), ("STR", "main"), ("CD", "cooldown"))
DEFAULT_REST = {"warmup": 10, "practice": 120, "main": 90, "cooldown": 10}
REHEARSE = {"skill_foundation", "skill", "skill_strength", "advanced_skill", "advanced_strength", "mastery_skill"}


def parse_level(path, errors=None):
    """{number: {type, title, min, rpe, desc, secs: {WU: [item]}, rounds, line}}; item = dict(mid, sets, v, v2, sec,
    side, tag, test, new). Also the level's block names {first class: name}."""
    classes, cur, blocks, stop = {}, None, {}, False
    for n, line in enumerate(open(path, encoding="utf-8").read().splitlines(), 1):
        if line.startswith("Blocks:"):
            for part in line[len("Blocks:"):].split("·"):
                m = re.match(r"\s*(\d{3})\s*[–-]\s*(\d{3})\s+(.+?)\s*$", part)
                if m:
                    blocks[(int(m.group(1)), int(m.group(2)))] = m.group(3)
            continue
        if line.startswith("## "):
            stop = True
            cur = None
            continue
        h = HEAD.match(line)
        if h and not stop:
            num = int(h.group(1))
            if errors is not None and num in classes:
                errors.append(f"line {n}: class {num:03d} twice")
            cur = classes[num] = {"number": num, "type": h.group(2), "title": h.group(3), "min": int(h.group(4)),
                                  "rpe": int(h.group(5)), "desc": None, "secs": {}, "rounds": None, "line": n}
            continue
        s = SEC.match(line)
        if s and cur is not None:
            name, body = s.group(1), s.group(2).strip()
            if name == "D":
                cur["desc"] = body
                continue
            if errors is not None and name in cur["secs"]:
                errors.append(f"line {n}: {name} twice in class {cur['number']:03d}")
            r = re.match(r"^\(circuit, (\d+) rounds?\)\s*", body)
            if r:
                cur["rounds"] = int(r.group(1))
                body = body[r.end():]
            items = []
            if body != "-":
                for raw in [x.strip() for x in body.split(";") if x.strip()]:
                    it = ITEM.match(raw)
                    if not it:
                        if errors is not None:
                            errors.append(f"line {n}: class {cur['number']:03d}: bad item '{raw}'")
                        continue
                    new, mid, sets, v, v2, sec, side, tag, test = it.groups()
                    items.append({"mid": mid, "sets": int(sets), "v": int(v), "v2": int(v2) if v2 else None, "sec": bool(sec),
                                  "side": bool(side), "tag": tag, "test": bool(test), "new": bool(new), "raw": raw})
            cur["secs"][name] = items
    return classes, blocks


def block_name(blocks, n):
    for (a, b), name in blocks.items():
        if a <= n <= b:
            return name
    return ""


def load_frame():
    return json.load(open(FRAME, encoding="utf-8"))


def convert(folder, frame=None):
    """All ten levels of the folder as the app's catalog (a dict), plus notes on what the conversion had to assume."""
    cat = frame
    cat = json.loads(json.dumps(cat))
    lib = {m["id"]: m for m in cat["movement_library"]}
    notes = []
    # Rests and tempos per movement and section from the frame: the same class first, then the level, then anywhere.
    by_level = collections.defaultdict(collections.Counter)
    anywhere = collections.defaultdict(collections.Counter)
    for level in cat["levels"]:
        for d in level["programs"]:
            for sec in ("warmup", "practice", "main", "cooldown"):
                for s in d[sec]:
                    key = (s[4], s[5] if s[5] not in ("assessment", "ramp-up", "readiness_check") else None)
                    by_level[(level["number"], sec, s[0])][key] += 1
                    anywhere[(sec, s[0])][key] += 1
                    anywhere[("*", s[0])][key] += 1
    texts = collections.defaultdict(collections.Counter)
    for level in cat["levels"]:
        for d in level["programs"]:
            texts[d["schedule"]["day_type"]][(d["readiness"]["en"], d["safety"]["en"])] += 1
    rehearsed = set()
    for level in cat["levels"]:
        L = level["number"]
        path = os.path.join(folder, f"L{L:02d}.md")
        classes, blocks = parse_level(path)
        for d in level["programs"]:
            c = classes.get(d["number"])
            if c is None:
                notes.append(f"L{L}:{d['number']:03d} missing in {path}")
                continue
            frame_type = d["schedule"]["day_type"]
            old = {}
            for sec in ("warmup", "practice", "main", "cooldown"):
                for s in d[sec]:
                    old.setdefault((sec, s[0]), s)
            d["title"]["en"] = c["title"]
            d["schedule"]["minutes"] = c["min"]
            d["schedule"]["rpe"] = c["rpe"]
            if not (c["type"] == "exam" and frame_type.startswith("exam")):
                d["schedule"]["day_type"] = c["type"]
            t = d["schedule"]["day_type"]
            if t != frame_type and texts[t]:
                d["readiness"]["en"], d["safety"]["en"] = texts[t].most_common(1)[0][0]
            for key, sec in SECTIONS:
                steps = []
                for it in c["secs"].get(key, []):
                    mid = it["mid"]
                    if it["v2"] is not None:
                        notes.append(f"L{L}:{d['number']:03d} {it['raw']}: a range, the app gets {it['v']}")
                    val = f"{it['v']}/side" if it["side"] else it["v"]
                    o = old.get((sec, mid))
                    if o is not None:
                        rest, tempo = o[4], (o[5] if o[5] not in ("assessment", "ramp-up", "readiness_check") else None)
                    else:
                        pick = (by_level.get((L, sec, mid)) or anywhere.get((sec, mid)) or anywhere.get(("*", mid)))
                        rest, tempo = pick.most_common(1)[0][0] if pick else (DEFAULT_REST[sec], None)
                    if it["test"]:
                        tempo = "assessment"
                    elif it["tag"]:
                        tempo = it["tag"]
                    elif tempo is None:
                        tempo = "isometric" if it["sec"] else "controlled"
                    # The easier option: the nearest movement down the library's chain that the sequence already taught.
                    reg, cand, seen = None, (lib.get(mid) or {}).get("regression_id"), set()
                    while cand and cand in lib and cand not in seen:
                        seen.add(cand)
                        if lib[cand]["category"] not in REHEARSE or cand in rehearsed:
                            reg = cand
                            break
                        cand = lib[cand].get("regression_id")
                    steps.append([mid, it["sets"], None if it["sec"] else val, val if it["sec"] else None, rest, tempo, reg])
                    rehearsed.add(mid)
                d[sec] = steps
            block = block_name(blocks, d["number"])
            if c["desc"]:
                d["description"]["en"] = f"{block}. {c['desc']}" if block else c["desc"]
            elif t != frame_type:
                d["description"]["en"] = f"{block}." if block else ""
            M = d["main"] or d["practice"]
            d["schedule"]["focus"] = list(dict.fromkeys(p for s in M[:4] for p in lib.get(s[0], {}).get("patterns", [])))[:5]
            if t.startswith("exam"):
                tests = [s[0] for s in d["practice"] + d["main"] if s[5] == "assessment"]
                if tests and d["schedule"].get("headline") not in tests:
                    d["schedule"]["headline"] = tests[0]
            elif d["schedule"].get("headline") and d["schedule"]["headline"] not in {s[0] for s in d["practice"] + d["main"]}:
                d["schedule"].pop("headline")
    return cat, notes



args = [a for a in sys.argv[1:] if not a.startswith("--")]
FAST = "--fast" in sys.argv
L = int(args[0])
folder = args[1] if len(args) > 1 else os.path.join(REPO, "curriculum", "handwritten")
path = os.path.join(folder, f"L{L:02d}.md")
sys.path.insert(0, os.path.join(REPO, "tools"))
_here = os.getcwd()
os.chdir(REPO)
import build_curriculum as bc  # noqa: E402  (imported for its belt counter, timing and library; it writes nothing)
os.chdir(_here)
LIB = bc.LIB
EXAMS = {1: {20, 40, 60, 81, 100}, 2: {20, 40, 62, 84, 100}, 3: {20, 40, 64, 84, 100}}
SKILL_IDS = set(bc.SKILL_SECTION)
must, panel, info = [], [], []

# ---------------------------------------------------------------- A. format
errs = []
levels = {}
for lv in range(1, 11):
    p = os.path.join(folder, f"L{lv:02d}.md")
    levels[lv] = parse_level(p, errs if lv == L else None)[0]
classes = levels[L]
must += [f"format: {e}" for e in errs]
exam_days = EXAMS.get(L, {13, 34, 55, 76, 100})
no_desc = []
FORBID = re.compile(r"(?i)(choose|choice|optional|independent|\broutes?\b|routing|non[-_ ]?blocking|never[-_ ]?gates?|never\s+blocks?|does\s+not\s+(?:block|prevent)|cannot\s+block)")
for n in range(1, 101):
    c = classes.get(n)
    if not c:
        must.append(f"{n:03d} missing")
        continue
    if list(c["secs"]) != ["WU", "SKILL", "STR", "CD"]:
        must.append(f"{n:03d} sections {list(c['secs'])}, want WU SKILL STR CD")
    if (n in exam_days) != (c["type"] == "exam"):
        must.append(f"{n:03d} type {c['type']}: the exam days are {sorted(exam_days)}")
    for sec in ("WU", "SKILL", "STR", "CD"):
        for it in c["secs"].get(sec, []):
            if it["mid"] not in LIB:
                must.append(f"{n:03d} {it['mid']} is not in the movement library (no new movements in this pass)")
    for it in c["secs"].get("SKILL", []):
        if it["mid"] not in SKILL_IDS:
            must.append(f"{n:03d} {it['mid']} is not a Skill-practice id (skill_section.txt): it belongs in STR")
    for it in c["secs"].get("STR", []):
        if it["mid"] in SKILL_IDS:
            must.append(f"{n:03d} {it['mid']} is a Skill-practice id: it belongs in SKILL")
    if c["type"] == "exam" and not any(i["test"] for i in c["secs"].get("SKILL", []) + c["secs"].get("STR", [])):
        must.append(f"{n:03d} exam without TEST items")
    for text in (c["title"], c["desc"] or ""):
        bad = FORBID.search(text)
        if bad:
            must.append(f"{n:03d} '{bad.group(0)}' in the title or D: line: the app's validator bans that word (no choices in the program)")
    if c["type"] != "exam":
        if not c["desc"]:
            no_desc.append(f"{n:03d}")
        elif len(c["desc"].split()) > 22 or len(c["desc"]) > 140:
            panel.append(f"{n:03d} D: line {len(c['desc'].split())} words, keep it to about 20")
if no_desc:
    must.append(f"no D: line (one plain sentence, about 8-20 words) in {len(no_desc)} classes: {' '.join(no_desc[:30])}{' ...' if len(no_desc) > 30 else ''}")
body = open(path, encoding="utf-8").read()
if "## Changes" not in body:
    must.append("no '## Changes' section")
if not re.search(r"^Level verdict: ", body, re.M):
    must.append("no 'Level verdict: ' line")

# ---------------------------------------------------------------- convert
cat, notes = convert(folder, json.load(open(os.path.join(REPO, "generated", "curriculum_v2.json"), encoding="utf-8")))
out_dir = os.path.join(REPO, "build", "handwritten-check")
os.makedirs(out_dir, exist_ok=True)
cat_path = os.path.join(out_dir, f"catalog_L{L:02d}.json")
json.dump(cat, open(cat_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
# The authored source (curriculum/program.json in the repo), which the PowerShell validator checks as well.
src_path = os.path.join(out_dir, f"program_L{L:02d}.json")
json.dump({"schema_version": "round11", "generated_by": "curriculum/handwritten",
           "step_tuple": ["movement_id", "sets", "reps", "seconds", "rest_seconds", "tempo", "regression_id"],
           "programs": [{"id": d["authored_id"], "level": int(d["id"][4:6]), "number": d["number"], "title": d["title"],
                         "description": d["description"], "schedule": d["schedule"], "warmup": d["warmup"], "practice": d["practice"],
                         "main": d["main"], "cooldown": d["cooldown"], "readiness": d["readiness"], "safety": d["safety"]}
                        for lv in cat["levels"] for d in lv["programs"]]},
          open(src_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
info += [n for n in notes if n.startswith(f"L{L}:")]
progs = {(lv["number"], d["number"]): d for lv in cat["levels"] for d in lv["programs"]}
ordered = [progs[k] for k in sorted(progs)]
for d in ordered:
    d["level"] = int(d["id"][4:6])

# ---------------------------------------------------------------- B. class limits
lim = bc.LIMITS[L]
for n in range(1, 101):
    d = progs[(L, n)]
    s = d["schedule"]
    work = d["practice"] + d["main"]
    sets = sum(x[1] for x in work)
    if s["day_type"] != "full_battle" and sets > lim["main_sets_including_practice"]:
        must.append(f"{n:03d} {sets} sets in SKILL+STR, the level's budget is {lim['main_sets_including_practice']}")
    for x in work:
        if x[1] > lim["sets_per_exercise"]:
            must.append(f"{n:03d} {x[0]} {x[1]} sets, the most per exercise in this level is {lim['sets_per_exercise']}")
    if not d["main"] or len(work) < 4:
        must.append(f"{n:03d} {len(d['practice'])} SKILL + {len(d['main'])} STR items: the app needs 4 or more, at least 1 in STR")
    if len(d["warmup"]) < 3 or len(d["cooldown"]) < 3:
        must.append(f"{n:03d} warm-up {len(d['warmup'])} / cool-down {len(d['cooldown'])} items: the app needs 3 or more of each")
    need = bc.class_seconds([d["warmup"], d["practice"], d["main"], d["cooldown"]]) / 60
    top = 60 if s["day_type"].startswith("exam") else 55
    if need > s["minutes"]:
        must.append(f"{n:03d} the app times it at {need:.1f} min, the header says {s['minutes']}: raise the header (max {top}) or cut")
    elif need < s["minutes"] - 8:
        must.append(f"{n:03d} the app times it at {need:.1f} min, the header says {s['minutes']}: lower the header to about {int(need) + 1}")
    if not 15 <= s["minutes"] <= top:
        must.append(f"{n:03d} {s['minutes']} min: classes run 15-{top}")

# ---------------------------------------------------------------- C. the app's validators
def level_of(msg):
    m = (re.search(r"\bL(\d+):\d{3}\b", msg) or re.search(r"sg:l(\d\d):p", msg) or re.search(r"\bLevel (\d+)\b", msg)
         or re.search(r"r9-l(\d\d)-p", msg))
    return int(m.group(1)) if m else None


program_wide = []


def take(source, msgs):
    for m in msgs:
        lv = level_of(m)
        if lv == L:
            must.append(f"{source}: {m}")
        elif lv is None:
            program_wide.append(f"{source}: {m}")


class SoftAssert(ast.NodeTransformer):
    def visit_Assert(self, node):
        msg = node.msg or ast.Constant(value=ast.unparse(node.test))
        call = ast.Expr(ast.Call(func=ast.Name("_ERR", ast.Load()), args=[msg], keywords=[]))
        return ast.copy_location(ast.If(test=ast.UnaryOp(ast.Not(), node.test), body=[call], orelse=[]), node)

    def visit_Raise(self, node):
        # "raise AssertionError(msg)" is a failed check too: report it and go on, so later checks still run.
        exc = node.exc
        if isinstance(exc, ast.Call) and isinstance(exc.func, ast.Name) and exc.func.id == "AssertionError" and exc.args:
            return ast.copy_location(ast.Expr(ast.Call(func=ast.Name("_ERR", ast.Load()), args=[exc.args[0]], keywords=[])), node)
        return node


def run_py(name):
    p = os.path.join(REPO, "tools", name)
    src = open(p, encoding="utf-8").read().replace('ROOT / "generated/curriculum_v2.json"', "Path(__CATALOG__)")
    tree = ast.fix_missing_locations(SoftAssert().visit(ast.parse(src)))
    found = []
    g = {"__name__": "__main__", "__file__": p, "__CATALOG__": cat_path, "_ERR": lambda m: found.append(str(m))}
    sys.path.insert(0, os.path.join(REPO, "tools"))
    here = os.getcwd()
    os.chdir(REPO)
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            exec(compile(tree, p, "exec"), g)
    except SystemExit:
        pass
    except Exception as e:  # a later line tripping over an earlier failure
        found.append(f"(the validator stopped: {type(e).__name__}: {e})")
    finally:
        os.chdir(here)
    return found


take("validate_progressions", run_py("validate_progressions.py"))
take("validate_morning_class", run_py("validate_morning_class.py"))
if not FAST:
    r = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "tools/validate_curriculum.ps1",
                        "-CatalogPath", cat_path, "-AuthoredSourcePaths", src_path], cwd=REPO, capture_output=True, text=True, encoding="utf-8", errors="replace")
    take("validate_curriculum", [l.strip()[2:] for l in r.stdout.splitlines() if l.strip().startswith("- ")])

# The app's effort marks (WorkoutEffortTest): per level at least 50 Moderate and 15 Easy, from Level 2 at least 5 Hard;
# no exam Easy, no recovery class Hard.
GW = [1.0, 1.5, 2.0, 3.0, 4.0]
LIGHT_CATS = {"dynamic_warmup", "activation", "mobility", "static_stretch", "static_cooldown", "recovery"}
LIGHT = ("exam", "light", "deload", "primer", "easy", "reentry")


def load(d):
    tot = 0.0
    for x in d["practice"] + d["main"]:
        if x[5] == "readiness_check":
            continue
        m = LIB.get(x[0], {})
        w = 0.25 if m.get("category") in LIGHT_CATS else GW[min(5, max(1, m.get("grade", 1))) - 1]
        tot += x[1] * w * (1.25 if (x[4] if x[4] is not None else 60) <= 45 else 1.0)
    return tot


def training(d):
    t = d["schedule"]["day_type"]
    return not t.startswith(LIGHT) and "recovery" not in t


lv_progs = [progs[(L, n)] for n in range(1, 101)]
loads = sorted(load(d) for d in lv_progs if training(d))
typical = max(1.0, (loads[(len(loads) - 1) // 2] + loads[len(loads) // 2]) / 2) if loads else 15.0
marks = collections.Counter()
for d in lv_progs:
    ratio, t, rpe = load(d) / typical, d["schedule"]["day_type"], d["schedule"]["rpe"]
    exam = t.startswith("exam")
    e = "HARD" if ratio > 1.25 or (not exam and rpe >= 8) else "EASY" if ratio < 0.7 and not (exam or rpe >= 7) else "MODERATE"
    marks[e] += 1
    if exam and e == "EASY":
        must.append(f"{d['number']:03d} effort: an exam rates Easy (load {ratio:.2f}x the level's typical class)")
    if "recovery" in t and e == "HARD":
        must.append(f"{d['number']:03d} effort: a recovery class rates Hard (load {ratio:.2f}x typical)")
if marks["MODERATE"] < 50 or marks["EASY"] < 15 or (L > 1 and marks["HARD"] < 5):
    must.append(f"effort: {dict(marks)}; the app's test wants 50+ Moderate, 15+ Easy{', 5+ Hard (RPE 8 on a training class is Hard; load over 1.25x the typical class too)' if L > 1 else ''}")

# The placement's skills step (PlacementTest, Placement.meets): it offers the Level 3-9 level tests only to counts that
# pass their pull-up and push-up tests, so each of those tests holds a pull-up test and 7 or more tests in all.
if 3 <= L <= 9:
    d = progs[(L, 100)]
    tests = [x for x in d["practice"] + d["main"] if x[5] == "assessment"]
    pull = [x for x in tests if x[0] == "pullup" and x[2] is not None]
    push = [x for x in tests if x[0] == "pushup" and x[2] is not None]
    if not pull:
        must.append("100 the level test has no pull-up test: the placement offers it only to counts that pass its pull-ups (PlacementTest)")
    if len(tests) < 7:
        must.append(f"100 the level test has {len(tests)} tests: the placement's skills step needs 7 or more (PlacementTest)")
    if L == 9 and pull and int(str(pull[0][2]).split("/")[0]) < 10:
        must.append("100 the Level 9 pull-up test must be 10 or more (PlacementTest)")
    if L == 3 and (any(int(str(x[2]).split("/")[0]) > 30 for x in pull) or any(int(str(x[2]).split("/")[0]) > 60 for x in push)):
        must.append("100 the Level 3 test must pass at 30 pull-ups and 60 push-ups (PlacementTest)")
    if L == 9 and any(int(str(x[2]).split("/")[0]) > 50 for x in push):
        must.append("100 the Level 9 push-up test must pass at 50 (PlacementTest)")

# ---------------------------------------------------------------- D. the panel's rules
HS = {"freestanding_handstand", "wall_handstand", "straddle_handstand", "supported_handstand_shift", "handstand_weight_shift",
      "handstand_balance", "handstand_toe_pull", "handstand_exit", "wall_kickup", "pike_handstand_hold",
      "fingertip_one_arm_handstand", "one_arm_handstand_hover", "straddle_one_arm_handstand", "one_arm_handstand"}
PL = {"tuck_planche", "advanced_tuck_planche", "planche_lean", "straddle_planche", "full_planche", "frog_stand", "tuck_planche_pushup",
      "pseudo_planche_pushup"}
WRIST = {"wall_handstand", "pike_handstand_hold", "frog_stand", "planche_lean", "handstand_exit", "wall_kickup", "handstand_toe_pull",
         "freestanding_handstand", "handstand_balance", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche"}
DRILLS = ("handstand_exit", "frog_stand", "wall_kickup", "handstand_toe_pull")
first_seen = {}
for lv in range(1, 11):
    for n in sorted(levels[lv]):
        for sec in ("SKILL", "STR"):
            for it in levels[lv][n]["secs"].get(sec, []):
                first_seen.setdefault(it["mid"], (lv, n))
new_by_class = collections.defaultdict(list)
for mid, (lv, n) in first_seen.items():
    if lv == L:
        new_by_class[n].append(mid)
hist = collections.defaultdict(list)
for n in sorted(classes):
    c = classes[n]
    t, s = c["type"], c["secs"]
    wu, sk, st, cd = s.get("WU", []), s.get("SKILL", []), s.get("STR", []), s.get("CD", [])
    words = len(c["title"].replace(" · ", " ").split())
    if words > 4:
        panel.append(f"{n:03d} title {words} words (R11: 2-4)")
    if len(wu) > 5:
        panel.append(f"{n:03d} warm-up {len(wu)} items (R1: at most 5)")
    ramps = [x for x in wu if x["tag"] == "ramp-up"]
    if len(ramps) > (2 if t == "exam" else 1):
        panel.append(f"{n:03d} {len(ramps)} ramp-ups (R1)")
    if ramps and wu[-1] is not ramps[-1]:
        panel.append(f"{n:03d} the ramp-up is not the last warm-up item (R1)")
    if ramps and (t.startswith("primer") or "recovery" in t):
        panel.append(f"{n:03d} a ramp-up on a primer or recovery day (R1)")
    for x in wu:
        if x["mid"] in DRILLS or x["mid"].startswith("eccentric_") or x["mid"].endswith("_negative"):
            panel.append(f"{n:03d} skill drill {x['mid']} in the warm-up (R1: it belongs in SKILL)")
    if len(sk) > 3 and t != "statics_chain":
        panel.append(f"{n:03d} SKILL {len(sk)} items (R5: at most 3)")
    cap = 6 if t in ("legs_core", "circuit_b", "battle", "full_battle", "basics") else 5
    if len(st) > cap and t != "exam" and "recovery" not in t:
        panel.append(f"{n:03d} STR {len(st)} items (R5: at most {cap})")
    ids = [x["mid"] for x in sk + st]
    if len(ids) != len(set(ids)):
        panel.append(f"{n:03d} a movement twice in SKILL/STR (R5: merge, or use the neighbouring variation)")
    allids = [x["mid"] for x in wu if x["tag"] != "ramp-up"] + ids + [x["mid"] for x in cd]
    twice = sorted({i for i in allids if allids.count(i) > 1} - set(ids))
    if twice:
        panel.append(f"{n:03d} {twice} twice in the class (R2)")
    if t in ("front_pull", "front_onearm") and any(x["mid"] in HS for x in sk) and (L, n) >= (2, 8):
        panel.append(f"{n:03d} a handstand item on a lever pull day (R5)")
    if t in ("front_pull", "front_onearm") and c["min"] > 45:
        panel.append(f"{n:03d} pull day {c['min']} min (R5: at most 45)")
    if t == "planche_push" and (L, n) >= (2, 52) and any(x["mid"] not in PL for x in sk):
        panel.append(f"{n:03d} a non-planche Skill item on a planche day (R5)")
    if any(x["mid"] in HS for x in sk) and not any(x["mid"] == "wall_slide" for x in wu):
        panel.append(f"{n:03d} a handstand item without wall_slide in the warm-up (R1)")
    wn = {x["mid"] for x in wu}
    if any(x["mid"] in WRIST for x in sk + st) and "recovery" not in t and not ("wrist_circles" in wn and wn & {"wrist_rocks", "wrist_pushup"}):
        panel.append(f"{n:03d} wrist-loaded work without wrist prep (R1)")
    if "recovery" in t and any(LIB.get(x["mid"], {}).get("category") in ("static_stretch", "static_cooldown") for x in st):
        panel.append(f"{n:03d} a stretch in STR on a recovery day (R2: it belongs in CD)")
    news = new_by_class.get(n, [])
    if len(news) > 1:
        panel.append(f"{n:03d} {len(news)} new movements {news} (R4: one per class)")
    if t == "exam":
        tests = sum(1 for x in sk + st if x["test"])
        if n == 100 and tests > 8:
            panel.append(f"{n:03d} {tests} tests (R8: a level test at most 8)")
        if n != 100 and not 4 <= tests <= 7 and not (n == min(exam_days)):
            panel.append(f"{n:03d} {tests} tests (R8: a block exam 4-7)")
    if t != "exam" and not t.startswith("primer"):
        for x in sk:
            hist[x["mid"]].append((n, f"{x['sets']}x{x['v']}"))
for mid, h in hist.items():
    run = 1
    for i in range(1, len(h)):
        run = run + 1 if h[i][1] == h[i - 1][1] else 1
        if run == 5:
            panel.append(f"flat: {mid} {h[i][1]} for 5 of its classes, up to {h[i][0]:03d} (R7)")
first10 = sum(len(new_by_class.get(n, [])) for n in range(1, 11))
if first10 > 2 and L > 1:
    panel.append(f"{first10} new movements in classes 001-010 (R4: at most 2)")

# ---------------------------------------------------------------- belts
try:
    landed, _, via = bc.belts(ordered)
    names = ["Beginner", "Intermediate", "Advanced", "Master", "Elite"]
    here_b = sorted((x[1], sid, names[k]) for sid, v in landed.items() for k, x in enumerate(v) if x and x[0] == L)
    total = sum(1 for v in landed.values() for x in v if x)
    belts_line = f"belts in this level ({len(here_b)}): " + "; ".join(f"{sid} {b} ({n:03d})" for n, sid, b in here_b)
    per = collections.Counter(x[0] for v in landed.values() for x in v if x)
    belts_total = f"belts in the program: {total} of 200; per level {[per[i] for i in range(1, 11)]}"
except Exception as e:
    belts_line, belts_total = f"belts: could not count ({e})", ""

# ---------------------------------------------------------------- the join: best doses around the level line
LINES = ["pushup", "pullup", "parallel_dip", "squat", "wall_handstand", "freestanding_handstand", "tuck_front_hold", "advanced_tuck_front",
         "one_leg_front", "straddle_front", "full_front", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche",
         "tuck_back_hold", "advanced_tuck_back", "straddle_back", "full_back", "tuck_flag", "straddle_flag", "full_flag", "lsit",
         "strict_muscleup", "one_arm_pullup", "hefesto", "one_arm_handstand"]


def best(lv, a, b):
    out = {}
    for n in range(a, b + 1):
        c = levels.get(lv, {}).get(n)
        if not c or c["type"] == "exam" or c["type"].startswith("primer"):
            continue
        for x in c["secs"].get("SKILL", []) + c["secs"].get("STR", []):
            if x["mid"] in LINES and (x["mid"] not in out or x["v"] > out[x["mid"]][0]):
                out[x["mid"]] = (x["v"], f"{x['sets']}x{x['v']}{'s' if x['sec'] else ''}", n)
    return out


# ---------------------------------------------------------------- report
print(f"{path}: {len(classes)} classes")
print(f"MUST ({len(must)}): what blocks shipping")
for m in must:
    print("  -", m)
print(f"PANEL ({len(panel)}): the panel's rules")
for m in panel[:80]:
    print("  -", m)
if len(panel) > 80:
    print(f"  ... {len(panel) - 80} more")
if info:
    print("NOTES:", "; ".join(info[:10]))
if program_wide:
    print(f"PROGRAM-WIDE ({len(program_wide)}; no level named, often caused by any level):")
    for m in program_wide[:15]:
        print("  -", m)
print(f"effort marks: {dict(marks)}")
print(belts_line)
print(belts_total)
if L > 1:
    prev = best(L - 1, 81, 100)
    print(f"join: L{L - 1} classes 081-100 best -> this level 001-020 best:")
    now = best(L, 1, 20)
    print("  " + "; ".join(f"{k} {prev[k][1]} -> {now[k][1] if k in now else '-'}" for k in LINES if k in prev))
end = best(L, 81, 100)
print(f"this level's 081-100 best (what the next level starts from): " + "; ".join(f"{k} {v[1]} ({v[2]:03d})" for k, v in ((k, end[k]) for k in LINES if k in end)))
sys.exit(1 if must else 0)
