# The Street Gymnastics program (round 9, 29 October 2026; round 10 review the same day)

One fixed solo program: 10 levels × 100 daily classes, no choices, no freestyle, no group classes. It starts with easy circuits, moves to a fixed skill week, brings the full shapes in Levels 8–9 and spends Level 10 on records. Every class is written by `tools/build_curriculum.py` from the plan below; the numbers in this document are counted from those classes.

This is the final version of the round 9 proposals (`curiculum-proposals/`). It takes claude-combined-1 as the base and corrects it as follows:
- **From the Codex proposals:**
  - every class within the validator's set budget, counting practice and main together;
  - exams that use today's all-targets pass rule;
  - circuit stations each tested on their own;
  - a lighter legs day;
  - doses the app can run, and belt dates counted from the real classes instead of estimated.
- **From the owner on 29 October:**
  - the full front lever by the end of Level 8 and the full planche in Level 9;
  - every full shape achieved by Level 9, and Level 10 for records;
  - every skill's maximum reachable inside the program, even though its 30 Elite sets are not.
- **From the research on realism:** the dates and the Elite targets of the rarest skills (see the realism check below).

## The frame

| | |
|---|---|
| Levels | 10 × 100 classes; Free 1–3, Plus 4–5, Pro 6–10 |
| Exam days (frozen) | L1 20/40/60/81/100 · L2 20/40/62/84/100 · L3 20/40/64/84/100 · L4–L10 13/34/55/76/100 |
| Belts | 37 skills × Beginner–Elite, 10/20/30/30/30 sets at the target |
| Class | warm-up, practice (the handstand line), main, cool-down; ≤55 minutes |
| Set budget | practice + main ≤ 14 / 16 / 18 / 18 / 20 sets in Levels 1–5, 20 after; ≤3 sets a step in Levels 1–2, ≤4 after |
| Pace | 1,000 classes ≈ 33 months at 7 a week, ≈ 38 at 6, ≈ 46 at 5 |

## The week

| Day | Levels 1–2: circuits | Levels 3–7: skill days | Levels 8–10: hardcore |
|---|---|---|---|
| 1 | Circuit A, pull & push | Front Lever & Pull | Front Lever & One-Arm |
| 2 | Push line & wrists (easy) | Planche & Push | Planche & Push |
| 3 | Circuit B, legs & core | Legs & Core (no arm work) | Legs & Core |
| 4 | Bar line (easy) | Back Lever & Bar | Back Lever & Muscle-Up |
| 5 | Easy handstand line | Handstand, Flag & Press | Handstand, Flag & Press |
| 6 | Circuit C, engine | Basics Circuit | L8: Battle; L9–10: Statics Chain (week A), Battle (week B) |
| 7 | Recovery | Recovery | Recovery |

Level 2 hands over one day per block: the front-lever day from block 3, the planche and bar days from block 4, the handstand and basics days from block 5. A circuit is written as repeated one-set steps (A, B, C, then A, B, C again).

The skill days:
- **The ladder.** Each straight-arm day runs the top rung for 3–4 sets, then 2 back-off sets on the rung below, so lower rungs keep earning.
- **New rungs.** A new rung starts with 2 short sets beside 3 sets of the rung below.
- **Week B.** It steps up the lead steps by one rep or 1–2 seconds, never past the next block's dose.
- **Levels 9–10.** In weeks with a Statics Chain, day 5 drops the flag ladder, so straight-arm work never runs three days in a row and the flag never runs two.

## The blocks

From Level 4 every block is three calendar weeks:
1. **Re-entry**, an easy class after the exam.
2. **Week A** at the block dose.
3. **Week B**, one step up.
4. **Exam week**:
   - days 1–3 at no more than 70% of the sets of the week before;
   - day 4 a light back-lever and bar class at 60%;
   - day 5 the primer, one easy set of each line;
   - day 6 the exam.

Two blocks differ:
- **Block 1 (classes 1–13)** replays the last level's end doses, with one set fewer on the lead steps in classes 2–7.
- **Block 5 (classes 77–100)** ends with a taper into the level exam on class 100: 92–94 lighter, 95–96 light, 97 easy, 98 recovery, 99 primer.

In Levels 1–3 the exam replaces that day's class. The class three before it is lighter, the next is easy, the one before the exam is the primer, and the class after is the re-entry.

## When the skills start and the full shapes arrive

| Line | Rungs and their first class |
|---|---|
| Front lever | tuck L2:043 · advanced tuck L4:008 · straddle L7:015 · **full L8:015** |
| Back lever | tuck L3:011 · advanced tuck L4:060 · straddle L5:081 · **full L6:046** |
| Planche | lean and frog L2:065 · tuck L4:002 · advanced tuck L6:002 · straddle L8:037 · **full L9:016** |
| Flag | ground support L3:005 · assisted tuck L3:047 · tuck L5:005 · straddle L6:061 · **full L7:061** |
| Pull | eccentrics L1:022 · pull-up L1:043 · archer L5:008 · assisted one-arm L7:036 · negatives L8:036 · **one-arm L9:015** |
| Muscle-up | low-bar transitions L2:040 · band and chest-to-bar L3:046 · negatives L3:067 · **strict L4:039** |
| Handstand | wall L1:026 · balance windows L3:026 · **freestanding L4:005** |
| Press | pike L1:044 · wall negatives L3:047 · wall HSPU L4:061 · deficit L8:005 · freestanding negatives L10:026 |
| Core | L-sit L3:023 · toes-to-bar L4:015 · dragon flag L6:017 (negatives from L3:046) · V-sit L8:003 |
| Legs | pistol L4:003 · Nordic negatives L4:017 · Nordic curl L7:017 |

Straight-arm rungs on one ladder start at least 60 classes apart. No two start in the same week, and no level starts more than three.

The exams that mark the plan:
- **L7:100, Advanced Athlete.** Each a fresh set:
  - freestanding handstand 20 s, strict muscle-up 3, advanced-tuck planche 8 s;
  - straddle front lever 3 s, full back lever 5 s, full flag 2 s a side;
  - wall HSPU 7, pull-ups 15, dips 20, push-ups 35, pistol 8 a side, L-sit 20 s.
- **L8:100:** the first full front lever is tested.
- **L9:100: every full shape.** Full planche, full front lever, full back lever, full flag and the one-arm pull-up, each at its first target or more, with the muscle-up and the freestanding handstand.
- **L10:076, champion test part one.** Every full shape and the hardest strength skills, one fresh set each.
- **L10:100, the Full Battle.** Five rounds of 10 pull-ups, 20 dips, 30 push-ups and 40 squats, with 30 s between stations and 90 s between rounds. Every station is its own test.

Level 10 is for records: every skill reaches its Elite target. 34 reach it with their working sets. The one-arm pull-up gets one record set in a Level 10 class (L10:015), and the class says so. The tuck and advanced-tuck back levers hold Elite itself through the full back lever (L10:088). The 30 Elite sets come from block repeats and own training, by design.

**Progressions peak while they are trained (round 10).** Until 2.5.0, the easier rungs reached their top only as Level 10 record sets, next to the full shape: a 20 s tuck back lever beside 4 × 11 s full back levers. Now each one peaks as the back-off of the next rung, before it leaves the classes:
- advanced-tuck front lever 15 s at L7:078;
- tuck flag 15 s a side at L6:082;
- advanced-tuck planche 12 s at L8:079;
- L-sit 30 s at L9:003, its back-off hold on the legs and core day once the V-sit takes the planche day;
- wall handstand 75 s at L5:082, one endurance hold after the freestanding practice on the handstand day in Levels 4–5.

A level held on a harder rung also holds on the easier rungs of its ladder, never higher, in the app and in these counts. So a progression never shows a lower belt than the shape above it.

## Where each belt lands

The same table as a page, with milestones and the months at 5, 6 or 7 classes a week: [ACHIEVEMENTS.html](ACHIEVEMENTS.html), rebuilt by `tools/build_achievements_page.py`.

Counted from the classes, if every set is done as written on the Normal toggle. "own" means block repeats or own training.

| Family | Skill | Targets | First set | Beginner | Intermediate | Advanced | Master | Elite |
|---|---|---|---|---|---|---|---|---|
| Pull | dead_hang | 30/45/60/75/90 s | L1:001 | L1:067 | L2:032 | L4:029 | L8:015* | L10:078* |
| Pull | pullup | 3/6/10/15/20 | L1:043 | L1:100 | L3:055 | L5:064 | L8:015 | L10:078 |
| Pull | archer_pullup | 2/3/4/6/8 a side | L5:008 | L5:043 | L6:092 | L8:050 | L9:085 | L10:085 |
| Pull | one_arm_pullup | 1/1/1/2/3 a side | L9:015 | L9:043 | L9:078 | L10:022 | own | own |
| Bar | strict_muscleup | 1/2/3/4/5 | L4:039 | L4:053 | L5:067 | L8:018 | L8:088 | L10:018 |
| Push | parallel_dip | 4/10/15/20/25 | L1:064 | L1:099 | L3:044 | L5:076 | L7:100 | L10:023 |
| Push | pushup | 6/12/20/30/40 | L1:002 | L1:016 | L1:085 | L3:044 | L6:041 | L10:037 |
| Push | pseudo_planche_pushup | 5/8/10/12/15 | L3:002 | L3:020 | L4:065 | L5:093 | L7:044 | L10:051 |
| Push | one_arm_pushup | 1/3/5/7/10 a side | L7:002 | L7:037 | L9:016 | L10:058 | own | own |
| Overhead | pike_pushup | 6/8/10/12/15 | L1:044 | L2:023 | L2:096 | L3:054 | L4:013 | own |
| Overhead | wall_hspu | 2/4/6/8/10 | L4:061 | L5:005 | L6:019 | L8:026 | L9:089 | own |
| Overhead | deficit_wall_hspu | 2/3/5/6/8 | L8:005 | L8:040 | L9:026 | L10:061 | own | own |
| Handstand | wall_handstand | 20/30/45/60/75 s | L1:026 | L1:073 | L2:076 | L3:089 | L10:008* | own |
| Handstand | freestanding_handstand | 5/10/20/30/45 s | L4:005 | L4:061 | L5:089 | L8:004 | L10:008 | own |
| Front lever | tuck_front_hold | 5/8/12/16/20 s | L2:043 | L2:084 | L3:057 | L4:085 | L6:015 | own |
| Front lever | advanced_tuck_front | 5/8/10/12/15 s | L4:008 | L4:085 | L6:008 | L7:008 | L10:078* | own |
| Front lever | straddle_front | 3/5/6/8/10 s | L7:015 | L7:050 | L9:022 | L9:085 | L10:078* | own |
| Front lever | full_front | 2/3/4/6/8 s | L8:015 | L8:064 | L9:057 | L10:008 | L10:078 | own |
| Back lever | tuck_back_hold | 5/8/12/16/20 s | L3:011 | L3:032 | L3:081 | L4:060 | L9:081* | L10:088* |
| Back lever | advanced_tuck_back | 5/8/10/15/20 s | L4:060 | L4:095 | L5:025 | L6:004 | L9:081* | L10:088* |
| Back lever | straddle_back | 3/5/8/10/12 s | L5:081 | L6:011 | L6:060 | L8:088* | L9:081* | L10:088* |
| Back lever | full_back | 3/5/8/10/12 s | L6:046 | L6:088 | L7:088 | L8:088 | L9:081 | L10:088 |
| Planche | tuck_planche | 5/8/10/12/15 s | L4:002 | L4:044 | L5:002 | L6:002 | L7:058 | own |
| Planche | advanced_tuck_planche | 3/5/8/10/12 s | L6:002 | L6:030 | L7:009 | L8:023 | own | own |
| Planche | straddle_planche | 2/3/4/5/6 s | L8:037 | L8:072 | L9:079* | L10:002* | own | own |
| Planche | full_planche | 2/2/2/3/4 s | L9:016 | L9:065 | L9:079 | L10:002 | own | own |
| Flag | tuck_flag | 5/8/10/12/15 s a side | L5:005 | L5:047 | L6:026 | L7:026 | L10:089* | own |
| Flag | straddle_flag | 2/4/6/8/10 s a side | L6:061 | L6:089 | L8:026 | L10:047* | L10:089* | own |
| Flag | full_flag | 2/3/4/5/6 s a side | L7:061 | L8:005 | L8:089 | L10:047 | L10:089 | own |
| Core | hollow_body_hold | 20/30/40/50/60 s | L1:001 | L1:015 | L2:020 | L3:064 | L4:038 | L5:068 |
| Core | hanging_leg_raise | 5/8/10/12/15 | L2:025 | L2:073 | L3:071 | L5:060 | L6:025 | L7:039 |
| Core | toes_to_bar | 3/6/8/12/15 | L4:015 | L4:076 | L6:078 | L8:015 | L9:078 | own |
| Core | lsit | 5/10/15/20/30 s | L3:023 | L3:079 | L4:093 | L6:072 | own | own |
| Core | v_sit | 2/3/4/5/6 s | L8:003 | L8:044 | L9:058 | L10:044 | own | own |
| Core | dragon_flag | 1/3/5/6/8 | L6:017 | L6:038 | L7:087 | L9:017 | L9:038 | L10:038 |
| Legs | squat | 15/20/25/30/40 | L1:001 | L1:010 | L2:020 | L3:010 | L3:059 | L6:059 |
| Legs | pistol_squat | 2/4/6/8/10 a side | L4:003 | L4:038 | L5:059 | L6:094 | L8:024 | L10:010 |

**156 of 185 belts land in classes.** By level, Levels 1–10 land 8, 8, 14, 14, 13, 19, 11, 19, 20 and 30. A * marks a belt held through a harder rung of the same ladder (round 10): the level of the harder rung holds on the easier ones, never higher. The first belt comes at class 10 (squat Beginner), the push-up and hollow follow by class 16, and every level brings new ones. Every skill earns its Beginner belt inside the plan, the last being the full planche at L9:065. Followed exactly, the program scores 12 by the end of Level 1, 193 by Level 7, 465 by Level 9 and 698 by Level 10, out of 1000.

## The realism check

Sources and method: a research pass on 29 October 2026.
- **Difficulty levels and gaps:** Steven Low's *Overcoming Gravity*, 2nd edition, progression charts.
- **Element values:** FIG.
- **Record minimums:** WSWCF.
- **What two commercial apps do:** Calisteniapp and Thenics, summarised only.
- **Timelines:** coaching timelines and self-reported strength logs.

No study gives median times, so the months below are estimates. They assume a motivated adult who starts with 0–3 pull-ups, trains 5–6 classes a week and weighs about 75 kg.

**Difficulty order at the first target:**
1. full planche;
2. one-arm pull-up;
3. straddle planche, full front lever and full flag;
4. strict muscle-up, straddle flag and full back lever;
5. advanced-tuck planche, straddle front lever, V-sit and one-arm push-up.

The strict muscle-up sits below the full front lever in every source:
- Overcoming Gravity, level 7 against 8;
- Thenics, rung 8 against 12;
- Calisteniapp's own guide, months 3–10 against 10–34.

**Typical months to the first target** (median, with range), against the class where the plan first reaches that target:

| Skill | Research | The plan (6 classes a week) |
|---|---|---|
| Full back lever | 14 (7–28) | L6:088 ≈ month 23 |
| Full flag | 24 (12–42) | L8:005 ≈ month 27 |
| Full front lever | 27 (15–48) | L8:064 ≈ month 29 |
| Straddle planche | 36 (24–60); many never | L8:072 ≈ month 29 |
| One-arm pull-up | 40 (24–72); many never | L9:043 ≈ month 32 |
| Full planche | 60 (36–96); most never | L9:065 ≈ month 33 |

**What the check changed:**
- The full back lever moved from Level 7 to Level 6, and the full flag from Level 8 to Level 7. The full front lever stays in Level 8, its research median.
- Seven Elite targets came down so that every skill's maximum is reachable inside the program. Before, they were past class 1,000 for a typical adult:
  - full planche 8 → 4 s;
  - straddle planche 10 → 6 s;
  - one-arm pull-up 5 → 3 a side;
  - full front lever 10 → 8 s;
  - full flag 10 → 6 s a side;
  - strict muscle-up 8 → 5;
  - V-sit 10 → 6 s.
- From Advanced up every level takes 30 sets, so no two of those levels share a target. The full planche is 2/2/2/3/4 s and the one-arm pull-up 1/1/1/2/3 a side: Beginner to Advanced differ by sets alone.

**What stays ambitious, honestly:**
- **Full planche and one-arm pull-up.** The plan gives them their first holds and reps in Level 9, at about month 33. That is ahead of the research medians, so it is realistic for committed, lighter athletes. Many others will repeat blocks; the block repeat after a missed exam is the plan's answer, and nothing is locked.
- **Heavier, taller, female and over-40 athletes.** Expect 20–50% longer on the one-arm pull-up, muscle-up, levers, flag and planche. Handstand, compression and legs are much less affected.
- **The strict muscle-up.** Its first rep (L4:053, about month 14) is earlier than the research median of 18 months. The band, chest-to-bar and negative work from L3:046 is what makes it reachable.

## Load rules (`tools/validate_progressions.py`)

- **Frame:** frozen exam days, a re-entry after every exam, and every class 55 minutes or less.
- **Budget:** each class within its set budget and per-step limit, with rep caps of push-up 50, squat 40, dip 30, pull-up 21 and leg raises 15.
- **Week:** the fixed week from Level 3.
- **Easy classes:** RPE 4 or less, no practice part, no assessments, at most two sets of anything.
- **Lighter exam weeks:** at most 70% of the week before.
- **Spacing:**
  - straight-arm classes never three in a row;
  - heavy elbow classes never back to back;
  - the planche and the flag never on consecutive days, and never together outside an exam.
- **Weekly coverage:** the hinge every week, and from Level 2 overhead pressing and compression.
- **Ladders:**
  - rungs at least 60 classes apart, none starting in a lighter class, none two in a week, at most three a level;
  - a build dose never under 80% of its best on the same day type.
- **Skills:**
  - all 37 trained and each at its Elite target at least once, or holding Elite through a harder rung inside the program;
  - no lever, planche or flag record set beside a harder rung of its ladder;
  - every full shape tested at L9:100;
  - Level 10 record sets.

`tools/validate_curriculum.ps1` keeps the catalog rules: every rolling seven classes covers the basics, an easy recovery day and, from Level 3, the muscle-up, front lever, back lever, dragon flag and planche lines. It also checks the regressions practised before they are offered, unique class texts, and the time and set limits. `tools/validate_morning_class.py` checks the practice part and the animations' contacts.

## Not in this version

- **A value retune.** The research suggests new rung values: front lever 70, flag 65, muscle-up 45 and deficit wall HSPU 21. Retuning means changing the leaderboard's family ceilings and a Firebase deploy, so the values stay as they were.
- **The overall score tops out at 96.9.** The deployed board still counts the retired Nordic family. The next functions deploy can drop it (`OverallScore.RETIRED_CEILINGS`).
- **New animations.** The plan uses only movements the app already animates, so the one-arm hang, band external rotation and half-straddle planche are left out.
