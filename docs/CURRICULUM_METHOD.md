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

## How the classes are made (round 9, 29 October 2026)

Every one of the 1,000 classes is written by `tools/build_curriculum.py` from one plan, into `curriculum/program.json`. Ids stay positional and frozen (`DayIdsTest`), so the classes are rewritten in place. The plan and its numbers are in [CURRICULUM_PLAN.md](CURRICULUM_PLAN.md):
- circuits in Levels 1-2;
- a fixed skill week from Level 3;
- the full shapes in Levels 8-9 and records in Level 10;
- 37 belted skills, each reaching its Elite target at least once inside the program.

The pipeline:
1. `python tools/build_curriculum.py` writes the classes. It checks the validators' rules as it writes and prints where every belt lands if each set is done as written.
2. `tools/assemble_curriculum.ps1` joins them with the movement library and the original export's legacy ids into `generated/curriculum_v2.json`. Copy that file to the app's assets.
3. The five validators: `validate_curriculum.ps1` (catalog structure and weekly coverage), `validate_animations.ps1`, `validate_morning_class.py` (the practice part and animation contacts), `validate_progressions.py` (the round 9 plan rules) and `validate_progression_motions.py`.

The original app's 90 detailed workouts and 109 out-of-range placeholders stay in the immutable forensic export (`street_gymnastic_programs_levels.json`, checked by SHA-256); every one of their positions is now an authored class (`catalog_metadata.json` `replaced_source_slots`). See [extraction provenance](EXTRACTION_REPORT.md).

Round 9 removed what only the earlier generations used:
- the per-level source files (`l1_tail`, `l2_l3_tails`, `l3_extension`, `l4_l5`, `l6`, `l7`, `l8_l10`, `source_replacements`);
- the weekday practice timetable and original-source projection (`morning_class.json`) and `authentic_titles.json`;
- the assembler's projection and set-trimming code.

## Before round 9

Rounds 1-8 (October 2026) built the ten-level catalog by hand-authored batches, a weekday practice timetable and generator passes kept outside the repository. Their descriptions and review transcripts are in Git history; the design decisions that survive are in the plan.
