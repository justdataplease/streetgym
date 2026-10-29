# The Street Gymnastics program (round 11, 29 October 2026)

One fixed solo program: 10 levels × 100 classes, no choices, no freestyle, no group classes. The athlete decides when to train: two classes a week, eight, or two in a day. The app keeps the place and the order; nothing in the program depends on a weekday or a date. Every class is written by `tools/build_curriculum.py` from the plan below, and the numbers in this document are counted from those classes.

Round 11 builds on round 10 (2.5.1):
- **Skills.** 40 belted skills in 16 families, up from 37 in 14. The one-arm handstand tops the handstand ladder; the shrimp squat and the Hefesto are new families, each with its own line of steps.
- **Three coach-persona reviews.** AI reviewers playing a statics coach, an adult-class teacher and an S&C coach/physio reviewed the plan three times; all three answered "a very good calisthenics program", 9/10. Their fixes are in the load rules below. No human coach has reviewed this version.
- **Batch 1, the coaches' last notes.** Exams at the trained dose, need-based bar-day leads, second exposures for the levers, overhead pressing near six sets per rotation, whole circuit rounds and the time cap.
- **Batch 2, four new movements, animated in side and front view.** The vertical flag, the flag negative, the full skin-the-cat and the free supinated German hang.

## The frame

| | |
|---|---|
| Levels | 10 × 100 classes; Free 1–3, Plus 4–5, Pro 6–10 |
| Exam classes (frozen) | L1 20/40/60/81/100 · L2 20/40/62/84/100 · L3 20/40/64/84/100 · L4–L10 13/34/55/76/100 |
| Belts | 40 skills × Beginner–Elite, 10/20/30/30/30 sets at the target |
| Class | warm-up (closing on a ramp-up set), practice (the handstand line), main, cool-down; 25–55 minutes |
| Set budget | practice + main ≤ 14/14/15/16/18 sets in Levels 1–5 (`class_progression.json`), more after |
| Pace | 1,000 classes ≈ 33 months at 7 a week, ≈ 38 at 6, ≈ 46 at 5; at 2 a week it simply takes longer |

## The rotation

Classes run in a fixed rotation of seven. The `schedule.week` and `schedule.day` fields are positions in that rotation, not calendar days.

| Position | Level 1: circuits | Levels 2–7: skill classes | Levels 8–10: hardcore |
|---|---|---|---|
| 1 | Circuit A, pull & push | Front Lever & Pull | Front Lever & One-Arm |
| 2 | Push line & wrists (easy) | Legs & Core (no arm work) | Legs & Core |
| 3 | Circuit B, legs & core | Planche & Push | Planche & Push |
| 4 | Bar line (easy) | Handstand & Press | Handstand & Press |
| 5 | Core & balance line | Back Lever, Flag & Hefesto bar class | the same |
| 6 | Circuit C, engine | Basics & Endurance | L8: Battle; L9–10: Statics Chain in week A, Battle otherwise |
| 7 | Recovery | Recovery | Recovery |

A circuit is written as repeated one-set steps (A, B, C, then A, B, C again). When a class runs long, the fit drops a whole round, last first, never a station.

The skill classes:
- **The ladder.** A straight-arm line runs its top rung for 3 sets, then a back-off set on the rung below, so the lower rungs keep earning. A new rung enters with 2 short sets beside the rung below for its first two weeks.
- **The bar class (position 5).** It rotates the back lever, the flag and, from Level 6, the Hefesto. The line whose next rung comes soonest leads with 3 sets; the others keep 2. With no rung due, the full flag leads for the two blocks after it starts, and otherwise the lines take turns.
- **Second exposures.** Position 6 adds a lighter set of the front lever (from Level 3) and of the back lever, so each lever trains twice per rotation, and two pike or elevated-pike press sets from Level 3.
- **Week B.** It steps up the lead steps by one rep or one dose step, never past the next block's dose; from Level 4 sets and dose never rise together.

## The blocks

From Level 4 every block runs from the recovery after one exam to the next exam:
1. **Re-entry**, an easy class after the exam (block 1 opens with a re-entry and then carries the last level's end doses for five classes).
2. **Week A** at the block dose, then **week B** one step up.
3. **Hold and trim** classes in the longer blocks, and a deload in block 3.
4. **Exam week:** position 4 is a light handstand-and-press class, position 5 a light bar class (no flag, one set of each line at about 70% of the block's dose, so the exam meets fresh elbows), and the exam falls on position 6, its warm-up the primer.

Block 5 (classes 77–100) ends with a taper into the level exam on class 100: 92–94 deload, 95 hold, 96 light, 97 easy, 98 recovery, 99 primer.

In Levels 1–3 the exam replaces that class. The class before it is the primer, an exam on a recovery position makes the class two before it easy, and the class after the exam is the re-entry.

## When the steps start and the full shapes arrive

The first class each movement appears in, counted from the program:

| Line | Steps and their first class |
|---|---|
| Front lever | tuck front row L2:008 · tuck L2:022 · advanced tuck L4:008 · one-leg L5:036 · straddle L7:015 · **full L8:015** |
| Back lever | German hang L2:025 · partial skin-the-cat L2:068 · tuck L3:012 · full skin-the-cat L3:067 · advanced tuck L4:061 · straddle L5:082 · **full L6:047** |
| Planche | frog stand L1:044 · lean L2:066 · pseudo-planche push-up L3:003 · tuck L4:003 · advanced tuck L6:003 · straddle L8:003 · **full L9:059** |
| Flag | ground support L3:005 · assisted tuck L3:047 · vertical flag L4:026 · tuck L5:005 · straddle L6:082 · flag negative L7:019 · **full L7:061** |
| Pull | eccentrics L1:025 · pull-up L1:064 · archer L5:008 · assisted one-arm L7:036 · one-arm negatives L8:036 · **one-arm L9:015** |
| Muscle-up | low-bar transitions L2:043 · band and chest-to-bar L3:050 · negatives L3:071 · **strict L4:043** |
| Handstand | wall L1:022 · balance windows L3:027 · **freestanding L4:006** |
| One-arm handstand | straddle handstand L6:020 · weight shift L7:041 · fingertip L8:041 · straddle one-arm L9:006 · **one-arm L9:069** |
| Press | pike L1:044 · elevated pike L3:013 · wall negatives L3:053 · wall HSPU L4:067 · deficit L8:011 · freestanding negatives L10:039 |
| Core | L-sit L3:023 · dragon negatives L3:051 · toes-to-bar L4:015 · dragon flag L6:023 · V-sit L8:009 |
| Legs | pistol L4:009 · Nordic negatives L4:023 · Nordic curl L7:023 |
| Shrimp | assisted partial L3:002 · assisted L5:016 · floating L6:037 · rear foot held, assisted L7:002 · **shrimp squat L7:058** |
| Hefesto | supinated German hang, feet assisted L6:040 · free L7:025 · Korean dip, assisted L7:026 · partial Hefesto L8:005 · assisted Hefesto L8:061 · **Hefesto L9:040** |

Straight-arm rungs on one ladder start at least 60 classes apart; no two start in the same week.

The four batch-2 movements:
- **Vertical flag** (seconds a side), hips stacked over the shoulders on the pole: the flag line's prep set from L4:026 until the tuck flag starts. It does not claim the bar class's lead, so the tuck back lever keeps its Level 4 volume.
- **Flag negative** (reps a side), lowered from the vertical through the line: from L7:014 until the full flag starts, it takes the place of one straddle-flag set on each flag class, so the elbow count stays the same.
- **Full skin-the-cat** (reps), through the German hang and back: the handstand-and-press class's shoulder-extension set from L3:067.
- **Free supinated German hang** (seconds): the Korean dip's back-off once it starts, and the press class's shoulder-extension set while the Korean dip is the Hefesto line's top rung (L7:025 to L8).

The exams that mark the plan:
- **L7:100, Advanced Athlete.** A fresh set of each standard, or what the athlete already trains when that is more.
- **L9:100: every full shape.** The one-arm handstand, full planche, full front lever, Hefesto, full back lever, full flag and one-arm pull-up, with the strict muscle-up, the deficit wall HSPU and the V-sit.
- **L10:076, champion test part one.** The one-arm handstand, every full shape, the Hefesto, the muscle-up, the one-arm pull-up and push-up and the shrimp squat, one fresh set each.
- **L10:100, the Full Battle.** Five rounds of 10 pull-ups, 20 dips, 30 push-ups and 40 squats, with 30 s between stations and 90 s between rounds. Every station is its own test.

Exams test at the trained dose: the best of the last 21 classes. Only the headline item may go one step over, and only up to a belt target; a block exam keeps at most 8 items and a level exam 10. From Level 7, max-effort items rest 180 s and the warm-up ramps the levers.

Level 10 is for records: every skill reaches its Elite target. 38 skills reach it in a set of their own. The one-arm handstand (L10:088), the shrimp squat (L10:086) and the Hefesto (L10:089) get record sets, and the classes say so. The dead hang and the hanging leg raise hold Elite through the pull-up and toes-to-bar. Progressions peak while they are still trained; no lever, planche or flag record sits beside a harder rung of its ladder.

## Where each belt lands

The same table as a page, with milestones and the months at any pace: [ACHIEVEMENTS.html](ACHIEVEMENTS.html), rebuilt by `tools/build_achievements_page.py`.

Counted from the classes, if every set is done as written on the Normal toggle. "own" means block repeats or the athlete's own training. A * marks a belt held through a harder rung of the same ladder: the level of the harder rung holds on the easier ones, never higher.

| Family | Skill | Targets | First set | Beginner | Intermediate | Advanced | Master | Elite |
|---|---|---|---|---|---|---|---|---|
| Pull-up | dead_hang | 30/45/60/75/90 s | L1:001 | L1:018 | L2:054 | L5:071* | L8:029* | L10:085* |
| Pull-up | pullup | 3/6/10/15/20 | L1:064 | L2:008 | L3:062 | L5:071 | L8:029 | L10:085 |
| Pull-up | archer_pullup | 2/3/4/6/8 a side | L5:008 | L5:050 | L7:071 | L9:036 | L10:078 | own |
| Pull-up | one_arm_pullup | 1/1/1/2/3 a side | L9:015 | L9:043 | L9:076 | L10:015 | own | own |
| Muscle-up | strict_muscleup | 1/2/4/6/8 | L4:043 | L4:064 | L5:057 | L7:085 | L9:092 | own |
| Front lever | tuck_front_hold | 5/8/12/16/20 s | L2:022 | L2:040 | L2:092 | L4:050 | L10:064* | own |
| Front lever | advanced_tuck_front | 5/8/10/12/15 s | L4:008 | L4:029 | L5:076 | L9:071* | L10:064* | own |
| Front lever | straddle_front | 3/5/7/10/12 s | L7:015 | L7:036 | L8:008 | L9:071* | L10:064* | own |
| Front lever | full_front | 3/4/5/7/10 s | L8:015 | L8:036 | L8:078 | L9:071 | L10:064 | own |
| Back lever | tuck_back_hold | 5/8/12/16/20 s | L3:012 | L3:033 | L3:075 | L4:020 | L4:068 | own |
| Back lever | advanced_tuck_back | 5/8/10/15/20 s | L4:061 | L4:089 | L5:041 | L5:068 | L10:082* | own |
| Back lever | straddle_back | 3/5/8/10/12 s | L5:082 | L6:006 | L7:069 | L8:100* | L10:082* | own |
| Back lever | full_back | 3/5/8/12/15 s | L6:047 | L6:082 | L7:089 | L8:100 | L10:082 | own |
| Dip | parallel_dip | 4/10/15/20/25 | L1:062 | L1:078 | L2:100 | L5:073 | L8:003 | L9:052 |
| Push-up | pushup | 6/12/20/30/40 | L1:001 | L1:013 | L1:071 | L2:080 | L6:017 | L9:045 |
| Push-up | one_arm_pushup | 1/3/5/7/10 a side | L7:006 | L8:006 | L10:020 | own | own | own |
| Handstand push-up | pike_pushup | 6/8/10/12/15 | L1:044 | L1:079 | L2:011 | L2:094 | L10:095* | own |
| Handstand push-up | wall_hspu | 2/4/6/8/10 | L4:067 | L4:095 | L5:095 | L7:095 | L10:095* | own |
| Handstand push-up | deficit_wall_hspu | 2/3/5/6/8 | L8:011 | L8:039 | L9:018 | L10:046 | L10:095 | own |
| Planche | pseudo_planche_pushup | 5/8/10/12/15 | L3:003 | L3:031 | L4:100* | L6:003* | L8:059 | L10:073 |
| Planche | tuck_planche | 5/8/10/12/15 s | L4:003 | L4:017 | L4:100 | L6:003 | L9:094* | own |
| Planche | advanced_tuck_planche | 5/6/8/10/12 s | L6:003 | L6:059 | L6:100 | L7:073 | L9:094* | own |
| Planche | straddle_planche | 3/4/5/6/8 s | L8:003 | L8:024 | L8:059 | L9:017 | L9:094 | own |
| Planche | full_planche | 3/3/4/5/6 s | L9:059 | L9:080 | L10:003 | L10:038 | L10:073 | own |
| Handstand | wall_handstand | 20/30/45/60/75 s | L1:022 | L1:030 | L1:086 | L3:011 | L8:010* | L9:011* |
| Handstand | freestanding_handstand | 5/15/30/45/60 s | L4:006 | L4:015 | L5:053 | L6:039 | L8:010 | L9:011 |
| Handstand | one_arm_handstand | 3/4/5/7/10 s a side | L9:069 | L9:090 | L10:048 | L10:088 | own | own |
| Pistol squat | squat | 15/20/25/30/40 | L1:003 | L1:013 | L1:052 | L3:051 | L8:030* | L10:044* |
| Pistol squat | pistol_squat | 2/4/6/8/10 a side | L4:009 | L4:044 | L5:065 | L7:030 | L8:030 | L10:044 |
| Toes to bar | hanging_leg_raise | 5/8/10/12/15 | L2:022 | L2:036 | L3:029 | L7:064* | L9:029* | L10:029* |
| Toes to bar | toes_to_bar | 3/6/8/12/15 | L4:015 | L4:068 | L6:043 | L7:064 | L9:029 | L10:029 |
| L-sit | lsit | 5/10/15/20/30 s | L3:023 | L3:051 | L4:065 | L6:037 | L10:037* | own |
| L-sit | v_sit | 3/4/5/6/8 s | L8:009 | L8:030 | L8:065 | L10:002 | L10:037 | own |
| Dragon flag | hollow_body_hold | 20/30/40/50/60 s | L1:001 | L1:011 | L1:071 | L4:002 | L5:023 | L6:023 |
| Dragon flag | dragon_flag | 1/3/5/6/8 | L6:023 | L6:044 | L7:065 | L8:093 | L9:023 | L10:023 |
| Human flag | tuck_flag | 5/8/10/12/15 s a side | L5:005 | L5:026 | L6:047 | L10:078* | own | own |
| Human flag | straddle_flag | 3/4/6/8/10 s a side | L6:082 | L7:008 | L8:068 | L10:078* | own | own |
| Human flag | full_flag | 3/4/5/6/8 s a side | L7:061 | L7:092 | L9:057 | L10:078 | own | own |
| Shrimp squat | shrimp_squat | 2/3/5/7/10 a side | L7:058 | L7:081 | L8:044 | L9:072 | L10:072 | own |
| Hefesto | hefesto | 1/1/2/3/4 | L9:040 | L9:076 | L10:013 | L10:082 | own | own |

**165 of 200 belts land in classes.** By level, Levels 1–10 land 11, 9, 8, 15, 13, 13, 14, 22, 24 and 36. The first belt comes at class 11 (hollow body Beginner), and the push-up and squat follow at class 13. Every level brings new ones. Every skill earns its Beginner belt inside the plan, the last being the one-arm handstand at L9:090.

## The realism check

Sources and method: a research pass on 29 October 2026.
- **Difficulty levels and gaps:** Steven Low's *Overcoming Gravity*, 2nd edition, progression charts.
- **Element values:** FIG.
- **Record minimums:** WSWCF.
- **What two commercial apps do:** Calisteniapp and Thenics, summarised only.
- **Timelines:** coaching timelines and self-reported strength logs.

No study gives median times, so the months below are estimates. They assume a motivated adult who starts with 0–3 pull-ups, trains 5–6 classes a week and weighs about 75 kg.

**Typical months to the first target** (median, with range), against the class where the plan lands Beginner:

| Skill | Research | The plan (6 classes a week) |
|---|---|---|
| Strict muscle-up | 18 | L4:064 ≈ month 14 |
| Full back lever | 14 (7–28) | L6:082 ≈ month 22 |
| Full flag | 24 (12–42) | L7:092 ≈ month 27 |
| Straddle planche | 36 (24–60); many never | L8:024 ≈ month 28 |
| Full front lever | 27 (15–48) | L8:036 ≈ month 28 |
| One-arm pull-up | 40 (24–72); many never | L9:043 ≈ month 32 |
| Full planche | 60 (36–96); most never | L9:080 ≈ month 34 |

**What stays ambitious, honestly:**
- **The full planche, the one-arm pull-up, the one-arm handstand and the Hefesto.** The plan gives them their first sets in Level 9, at about months 32–34. That is ahead of the research medians, so it is realistic for committed, lighter athletes. Many others will repeat blocks; the block repeat after a missed exam is the plan's answer, and nothing is locked.
- **Heavier, taller, female and over-40 athletes.** Expect 20–50% longer on the one-arm skills, the muscle-up, the levers, the flag, the planche and the Hefesto. Handstand, compression and legs are much less affected.
- **The strict muscle-up.** Its first rep (about month 14) is earlier than the research median of 18 months. The band, chest-to-bar and negative work from L3:050 is what makes it reachable.

The targets keep every skill's maximum reachable inside the program, and no hold target starts under 3 s (round 11). From Advanced up every level takes 30 sets, so no two of those levels share a target.

## Load rules (`tools/validate_progressions.py`)

- **Frame:** frozen exam classes, a re-entry after every exam, every class 55 minutes or less.
- **Budget:** each class within its set budget and per-step limit, with rep caps of push-up 50, squat 40, dip 30, pull-up 21 and leg raises 15.
- **Easy classes:** RPE 4 or less, no practice part, no assessments, at most two sets of anything. From Level 4, any 21 classes hold at most 6 lighter, primer, easy or re-entry classes.
- **Lighter exam weeks:** at most 70% of the week before.
- **Elbows:** at most 6 elbow-load sets a class (7 on a three-line bar class, 4 on position 6, which follows the bar class, and in the light bar class); heavy elbow classes never back to back.
- **Spacing:**
  - straight-arm classes never three in a row;
  - the planche and the flag never on consecutive classes, exams included, and never together outside an exam;
  - the Hefesto never shares a class with the planche, nor the one-arm handstand lead with the planche lead.
- **Weekly coverage:** the hinge every rotation, and from Level 2 overhead pressing and compression.
- **Ladders:**
  - rungs at least 60 classes apart, none starting in a lighter class, none two in a rotation, at most three a level;
  - the rung below gets at least 11 sets in the 40 classes before each new rung;
  - straight-bar dips reach 10 before the Korean dip;
  - a build dose never under 80% of its best on the same class type;
  - sets and dose never rise together from Level 4.
- **Exams:** at the trained dose, with at most two items a step over.
- **Skills:**
  - all 40 trained, each at its Elite target at least once or holding Elite through a harder rung;
  - minimum holds of 3 s for the full and straddle shapes, the V-sit, the advanced-tuck planche and the one-arm balance line, 5 s for the rest;
  - no skill at the top of its ladder left more than 14 classes (16 across a level's taper and exam);
  - no lever, planche or flag record set beside a harder rung of its ladder;
  - every full shape tested at L9:100.
- **Circuits:** whole rounds only.

`tools/validate_curriculum.ps1` keeps the catalog rules: every rolling seven classes covers the basics, an easy recovery class and, from Level 3, the muscle-up, front lever, back lever, dragon flag and planche lines. It also checks the regressions practised before they are offered, unique class texts, and the time and set limits. `tools/validate_morning_class.py` checks the practice part and the animations' contacts, and `tools/validate_progression_motions.py` the progressions' motions (30 movements, the batch-2 four included).

## Not in this version

- **A real-athlete beta.** The 9/10 is a paper review by AI coach personas. The step to 10 is a real coach's read and a few real athletes running Levels 1–4, reporting where the doses or the order feel wrong; that needs people, not code.
- **A value retune.** The research suggests new rung values: front lever 70, flag 65, muscle-up 45 and deficit wall HSPU 21. Retuning means changing the leaderboard's family ceilings and a Firebase deploy, so the values stay as they are.
- **The leaderboard's version 3.** Round 11's families need the score document's version 3 (`firebase/functions/boards.py`, `firestore.rules`). It is written and tested but not deployed; the app and the deploy ship together. The GitHub builds have no Firebase configuration, so they are not affected.
