# StreetGym curriculum method

## Current baseline: ten levels, individual daily workouts

The only current curriculum has 10 levels, 100 sessions per level, 1,000 unique positional workout IDs and 50 exams. It is one fixed sequence followed by an individual athlete; no group-class system is involved. Current data and code take precedence over historical review prose.

| Level | Name | Exam days |
| --- | --- | --- |
| 1 | Foundations | 20, 40, 60, 81, 100 |
| 2 | Bar Strength | 20, 40, 62, 84, 100 |
| 3 | First Skills | 20, 40, 64, 84, 100 |
| 4 | Skill Control | 13, 34, 55, 76, 100 |
| 5 | Balance & Levers | 13, 34, 55, 76, 100 |
| 6 | Confident Calisthenics | 13, 34, 55, 76, 100 |
| 7 | Full Shapes | 13, 34, 55, 76, 100 |
| 8 | Strength Endurance | 13, 34, 55, 76, 100 |
| 9 | Superhuman | 13, 34, 55, 76, 100 |
| 10 | Beyond Gravity | 13, 34, 55, 76, 100 |

Today assigns the next session. Sessions keep warm-up, practice, main and cooldown, explicit regressions, the difficulty toggle and exam/replay behavior. See [the program plan](CURRICULUM_PLAN.md), [skills and belts](SKILLS_SCORE.md) and [tiers](TIERS.md). Do not infer mastery from attendance or treat the level names as sporting qualifications.

## How the classes are made (since 5 November 2026: by hand)

The owner compared the generator's Round 12 and Round 13 with a program written by hand ("Round X") and chose the hand-written one for production, with the generator switched off. The 1,000 classes are now written class by class in `curriculum/handwritten/L01.md` … `L10.md`. Each file holds a level's 100 classes, then its `## Changes` notes and the level verdict. The format is described at the top of `tools/handwritten_program.py`:
- the header (number, day type, title, minutes, RPE);
- a `D:` description line;
- the four sections `WU`, `SKILL`, `STR` and `CD`, with items `id SETSxAMOUNT[s][/side][ [tempo]][ TEST]`.

The pipeline:
1. Edit the level files. Classes are edited in place: the numbers, exam days and day types are the frozen frame (`DayIdsTest`). Check each edited level with `python tools/check_handwritten.py <level>` (Pillow Python): it runs every check the app has on the hand-written files and lists every failure (MUST lines block a release), plus the coaches' rules (PANEL lines) and the level's belts.
2. `python tools/handwritten_program.py` converts them into `curriculum/program.json`; it decides nothing about a class. A step's rest and tempo come from the same movement in that class before, else that movement's usual rest in the level; the easier option is the nearest movement down the library's regression chain that the program has already taught.
3. `tools/assemble_curriculum.ps1`, the five validators and the copy to the app's assets, as below. `tools/package_dist.ps1` runs the whole chain.

`tools/build_curriculum.py` refuses to run (unless `--force-generator`, which would overwrite the hand-written program); other tools still import its belt counter, class timing and movement data.

## The generator (round 9, 29 October 2026, to Round 13, 5 November 2026)

Until 5 November 2026 every one of the 1,000 classes was written by `tools/build_curriculum.py` from one plan, into `curriculum/program.json`. Ids stay positional and frozen (`DayIdsTest`), so the classes are rewritten in place. The plan and its numbers are in [CURRICULUM_PLAN.md](CURRICULUM_PLAN.md):
- circuits in Level 1;
- a fixed rotation of seven skill classes from Level 2, run at whatever pace the athlete chooses;
- the full shapes in Levels 8-9 and records in Level 10;
- 40 belted skills (37 until round 11), each reaching its Elite target at least once inside the program.

The pipeline:
1. `python tools/build_curriculum.py` writes the classes. It checks the validators' rules as it writes and prints where every belt lands if each set is done as written.
   `python tools/build_achievements_page.py` then rebuilds [ACHIEVEMENTS.html](ACHIEVEMENTS.html), the page of what the program gives skill by skill and belt by belt, counted from the same classes.
2. `tools/assemble_curriculum.ps1` joins them with the movement library and the level definitions into `generated/curriculum_v2.json`. Copy that file to the app's assets.
3. The five validators: `validate_curriculum.ps1` (catalog structure and weekly coverage), `validate_animations.ps1`, `validate_morning_class.py` (the practice part and animation contacts), `validate_progressions.py` (the plan's rules) and `validate_progression_motions.py`.

Every one of the 1,000 classes is written by `tools/build_curriculum.py`. Since 2.7.0 the catalog carries nothing from the third-party app the first workouts came from: no `source_baseline` hashes, no `origin` labels, no `legacy` workout ids and no `catalog_provenance`, and neither the assembler nor `validate_curriculum.ps1` reads its export (the validator now rejects those fields). Class N of a level is the authored class numbered N, so the day ids did not move. The extraction history stays in [EXTRACTION_REPORT.md](EXTRACTION_REPORT.md); the export itself stays out of git.

Round 9 removed what only the earlier generations used:
- the per-level source files (`l1_tail`, `l2_l3_tails`, `l3_extension`, `l4_l5`, `l6`, `l7`, `l8_l10`, `source_replacements`);
- the weekday practice timetable and original-source projection (`morning_class.json`) and `authentic_titles.json`;
- the assembler's projection and set-trimming code.

Round 10 (the owner's review of 2.5.0, the same day) kept the plan and fixed how progressions read beside their full shapes:
- **Carry.** A level held on a harder rung holds on the easier rungs of its ladder, never higher (`SkillStatus.carriedBy`). The generator, the page and `validate_progressions.py` count belts the same way, the validator from `SkillBook.kt`'s own ladders. Before, the plan gave the full back lever Elite while its straddle, with the same 8 s target, only reached Advanced 16 classes later.
- **Peaks.** A progression peaks while it is still trained, as the next rung's back-off, not as a Level 10 record beside the full shape. Five such records went, and a new rule forbids a lever, planche or flag record beside a harder rung.
- **Records.** The generator decides on the fitted classes in two passes, so a rung whose Elite comes through a harder rung gets no record.
- **Names.** The tuck and straddle human flags and the chest-to-wall handstand lost their old "Practice" suffix.

## Before round 9

Rounds 1-8 (October 2026) built the ten-level catalog by hand-authored batches, a weekday practice timetable and generator passes kept outside the repository. Their descriptions and review transcripts are in Git history; the design decisions that survive are in the plan.
