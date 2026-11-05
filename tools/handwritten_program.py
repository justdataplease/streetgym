"""The program's classes, from the hand-written level files to curriculum/program.json.

Since 2026-11-05 (the owner: "keep the generator disabled") the 1,000 classes are written by hand, class by class, in
curriculum/handwritten/L01.md ... L10.md. This tool only converts them; it decides nothing about a class. Then run
tools/assemble_curriculum.ps1, the validators, and copy generated/curriculum_v2.json to the app's assets, as before.

A class in a level file:

    022 front_pull · First Floor Pull · 27 min · RPE 6
     D: Your first floor push-ups: two sets of three, stop with two in reserve.
     WU: march 1x60s; wrist_circles 1x10; inverted_row 1x3 [ramp-up]
     SKILL: tuck_front_hold 3x5s
     STR: pushup 2x3; inverted_row 3x6 [2111]; hollow_body_hold 2x20s
     CD: lat_stretch 1x30s; child_pose 1x45s

- header: number, day type, title, minutes, RPE. The day types and exam days are the frozen frame (DayIdsTest).
- D: the description line ("<block name>. <D>"; the block names come from the file's "Blocks:" line). Exams keep their
  fixed exam text when they have none.
- items: id SETSxAMOUNT, "s" for seconds, "/side" for each side, [tag] for a tempo (3111, ramp-up, controlled...),
  TEST for an exam's test set; "-" for an empty section. SKILL holds the skill holds and balances, STR the rest.

What the files don't say comes from the program before (curriculum/program.json as it stands): a step's rest and tempo
from the same movement in the same class there, else the movement's usual rest in that level; the readiness and safety
notes of the class's day type; ids, weeks and days. The easier option of a step is the nearest movement down the
library's regression chain that the program has already taught.

usage: python tools/handwritten_program.py [--check]   (--check: convert and compare, write nothing)
"""
import collections
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "curriculum" / "handwritten"
PROGRAM = ROOT / "curriculum" / "program.json"

HEAD = re.compile(r"^(\d{3}) ([a-z_]+) · (.+?) · (\d+) min · RPE (\d+)\s*$")
SEC = re.compile(r"^ (WU|SKILL|STR|CD|D): ?(.*)$")
ITEM = re.compile(r"^(NEW:)?([a-z0-9_]+) (\d+)x(\d+)(?:-(\d+))?(s)?(/side)?(?: \[([^\]]+)\])?( TEST)?$")
SECTIONS = (("WU", "warmup"), ("SKILL", "practice"), ("STR", "main"), ("CD", "cooldown"))
DEFAULT_REST = {"warmup": 10, "practice": 120, "main": 90, "cooldown": 10}
REHEARSE = {"skill_foundation", "skill", "skill_strength", "advanced_skill", "advanced_strength", "mastery_skill"}
PLAIN = ("assessment", "ramp-up", "readiness_check")


def parse_level(path):
    """The level file's classes {number: class} and block names {(first, last): name}. Stops at the first '## '
    heading (the notes after the classes)."""
    classes, cur, blocks, errors = {}, None, {}, []
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.startswith("Blocks:"):
            for part in line[len("Blocks:"):].split("·"):
                m = re.match(r"\s*(\d{3})\s*[–-]\s*(\d{3})\s+(.+?)\s*$", part)
                if m:
                    blocks[(int(m.group(1)), int(m.group(2)))] = m.group(3)
            continue
        if line.startswith("## "):
            break
        h = HEAD.match(line)
        if h:
            cur = classes.setdefault(int(h.group(1)), {"type": h.group(2), "title": h.group(3), "min": int(h.group(4)),
                                                       "rpe": int(h.group(5)), "desc": None, "secs": {}})
            continue
        s = SEC.match(line)
        if s and cur is not None:
            name, body = s.group(1), s.group(2).strip()
            if name == "D":
                cur["desc"] = body
                continue
            body = re.sub(r"^\(circuit, \d+ rounds?\)\s*", "", body)
            items = []
            for raw in ([] if body == "-" else [x.strip() for x in body.split(";") if x.strip()]):
                it = ITEM.match(raw)
                if not it:
                    errors.append(f"{path.name} line {n}: bad item '{raw}'")
                    continue
                _, mid, sets, v, _, sec, side, tag, test = it.groups()
                items.append({"mid": mid, "sets": int(sets), "v": int(v), "sec": bool(sec), "side": bool(side), "tag": tag,
                              "test": bool(test)})
            cur["secs"][name] = items
    return classes, blocks, errors


def convert(frame_programs, library):
    """The program from the level files, on the frame of the program before."""
    lib = {m["id"]: m for m in library}
    by_level, anywhere = collections.defaultdict(collections.Counter), collections.defaultdict(collections.Counter)
    texts = collections.defaultdict(collections.Counter)
    for d in frame_programs:
        texts[d["schedule"]["day_type"]][(json.dumps(d.get("readiness")), json.dumps(d.get("safety")))] += 1
        for sec in ("warmup", "practice", "main", "cooldown"):
            for s in d[sec]:
                key = (s[4], s[5] if s[5] not in PLAIN else None)
                by_level[(d["level"], sec, s[0])][key] += 1
                anywhere[(sec, s[0])][key] += 1
                anywhere[("*", s[0])][key] += 1
    files, errors = {}, []
    for L in range(1, 11):
        classes, blocks, errs = parse_level(SOURCE / f"L{L:02d}.md")
        files[L] = (classes, blocks)
        errors += errs
    out, rehearsed = [], set()
    for d in sorted(frame_programs, key=lambda d: (d["level"], d["number"])):
        d = json.loads(json.dumps(d))
        L, n = d["level"], d["number"]
        classes, blocks = files[L]
        c = classes.get(n)
        if c is None:
            errors.append(f"L{L}:{n:03d} is missing from L{L:02d}.md")
            out.append(d)
            continue
        frame_type = d["schedule"]["day_type"]
        old = {}
        for sec in ("warmup", "practice", "main", "cooldown"):
            for s in d[sec]:
                old.setdefault((sec, s[0]), s)
        d["title"] = {"en": c["title"]}
        d["schedule"]["minutes"], d["schedule"]["rpe"] = c["min"], c["rpe"]
        if not (c["type"] == "exam" and frame_type.startswith("exam")):
            d["schedule"]["day_type"] = c["type"]
        t = d["schedule"]["day_type"]
        if t != frame_type and texts[t]:
            readiness, safety = (json.loads(x) for x in texts[t].most_common(1)[0][0])
            for key, value in (("readiness", readiness), ("safety", safety)):
                if value is None:
                    d.pop(key, None)
                else:
                    d[key] = value
        for key, sec in SECTIONS:
            steps = []
            for it in c["secs"].get(key, []):
                mid = it["mid"]
                if mid not in lib:
                    errors.append(f"L{L}:{n:03d} {mid} is not in the movement library")
                val = f"{it['v']}/side" if it["side"] else it["v"]
                o = old.get((sec, mid))
                if o is not None:
                    rest, tempo = o[4], (o[5] if o[5] not in PLAIN else None)
                else:
                    pick = by_level.get((L, sec, mid)) or anywhere.get((sec, mid)) or anywhere.get(("*", mid))
                    rest, tempo = pick.most_common(1)[0][0] if pick else (DEFAULT_REST[sec], None)
                if it["test"]:
                    tempo = "assessment"
                elif it["tag"]:
                    tempo = it["tag"]
                elif tempo is None:
                    tempo = "isometric" if it["sec"] else "controlled"
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
        block = next((name for (a, b), name in blocks.items() if a <= n <= b), "")
        if c["desc"]:
            d["description"] = {"en": f"{block}. {c['desc']}" if block else c["desc"]}
        elif t != frame_type:
            d["description"] = {"en": f"{block}." if block else ""}
        work = d["main"] or d["practice"]
        d["schedule"]["focus"] = list(dict.fromkeys(p for s in work[:4] for p in lib.get(s[0], {}).get("patterns", [])))[:5]
        if t.startswith("exam"):
            tests = [s[0] for s in d["practice"] + d["main"] if s[5] == "assessment"]
            if tests and d["schedule"].get("headline") not in tests:
                d["schedule"]["headline"] = tests[0]
        elif d["schedule"].get("headline") and d["schedule"]["headline"] not in {s[0] for s in d["practice"] + d["main"]}:
            d["schedule"].pop("headline")
        out.append(d)
    return out, errors


def main():
    frame = json.loads(PROGRAM.read_text(encoding="utf-8"))
    library = json.loads((ROOT / "curriculum" / "movement_library.json").read_text(encoding="utf-8"))["movements"]
    programs, errors = convert(frame["programs"], library)
    for e in errors:
        print("  ", e)
    if errors:
        print(len(errors), "problems; curriculum/program.json not written")
        return 1
    result = {"schema_version": frame.get("schema_version", "round11"), "generated_by": "tools/handwritten_program.py",
              "source": "curriculum/handwritten", "step_tuple": frame["step_tuple"], "programs": programs}
    text = json.dumps(result, ensure_ascii=False, indent=1) + "\n"
    changed = sum(1 for a, b in zip(frame["programs"], programs) if a != b)
    if "--check" in sys.argv:
        print(f"{changed} of {len(programs)} classes differ from curriculum/program.json (nothing written)")
        return 0
    PROGRAM.write_text(text, encoding="utf-8")
    print(f"wrote curriculum/program.json: {len(programs)} classes, {changed} changed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
