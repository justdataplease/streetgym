# The Street Gymnastics program (round 11, review 9, 30 October 2026)

One fixed solo program: 10 levels × 100 classes, no choices, no freestyle, no group classes. The athlete decides when to train: two classes a week, eight, or two in a day. The app keeps the place and the order; nothing in the program depends on a weekday or a date. Every class is written by `tools/build_curriculum.py` from the plan below, and the numbers in this document are counted from those classes.

Round 11 builds on round 10 (2.5.1):
- **Skills.** 40 belted skills in 16 families, up from 37 in 14. The one-arm handstand tops the handstand ladder; the shrimp squat and the Hefesto are new families, each with its own line of steps.
- **Three coach-persona reviews.** AI reviewers playing a statics coach, an adult-class teacher and an S&C coach/physio reviewed the plan three times; all three answered "a very good calisthenics program", 9/10. Their fixes are in the load rules below. No human coach has reviewed this version.
- **Batch 1, the coaches' last notes.** Exams at the trained dose, need-based bar-day leads, second exposures for the levers, overhead pressing near six sets per rotation, whole circuit rounds and the time cap.
- **Batch 2, four new movements, animated in side and front view.** The vertical flag, the flag negative, the full skin-the-cat and the free supinated German hang.
- **Review 6 (2.6.2), the same three AI personas.** The owner asked for exams that are real tests of the level: every exam now tests every skill family the block trained, 12 to 15 tests from Level 4, in a fixed order. Class order: wrist drills before hand-balance work, the flag after the front-lever ladder and the muscle-up, the back lever before the bent-arm Hefesto work, day 6's lever sets before its basics, ramp-up sets at half of the day's set, and one heavy set of high-rep pull-ups or dips a class.
- **Review 7 (2.6.3), the final sign-off.** All three personas signed off at 9/10 ("freeze the exercises") once three rules were in: a ramp set before every straddle or full lever and flag, a press between the one-arm pull-up and the Hefesto in exams, and exam headlines that match their names. Classes are now named for what they train. New movements fill the stale stretches the reviews found (the owner): in Level 1's classes 21–60 the kick-up to the wall (L1:023), the bear crawl (L1:026), the support walk along the bars (L1:041), the Cossack squat (L1:045) and the hollow-to-arch swing (L1:046) take the places of planks, calf raises and arch holds; in Level 3's end the jump-assisted muscle-up (L3:078) and the low-bar pullover (L3:089) join the muscle-up line until the strict one, and squat jumps (L3:086) the legs class. Level 10 adds the full freestanding handstand push-up (L10:060), its negative kept as the back-off.
- **Review 8 (2.6.3), stale periods.** The owner asked whether any stretch goes stale. The three personas agreed the cause was copy weeks, not missing movements: 143 training classes repeated the same class of a week before, because week B can't pass the next block's dose. Fixes:
  - **Tempo week:** a class that would repeat last week lowers its rep steps in 3 s, the same sets and reps, named "Tempo · …".
  - **Swaps within a line, never an added set:** the paused squat (L1:087), the paused push-up (L2:055), the planche lean a week earlier (L2:052, five classes before its exam), the wall balance practice from L2:053, the pancake lift for the seated pike lift (L4:081, then blocks 3 and 5), the supported handstand shift (L6:060, for one straddle handstand set), the one-arm handstand hover (L8:081, for the finished weight shift), and squat jumps opening the legs class in blocks 2 and 4 of L6–L9.
  - **Fixes:**
    - a new press never arrives in the light class (the deficit HSPU now starts at L8:018, not two days before an exam);
    - the day before the Level 3 test holds 8 sets;
    - "One step up" only when a step rose;
    - the countdown to the level test in its last 13 classes.

  Copy weeks fell from 143 to 81 and titles repeating three weeks running from 171 to 71, with 163 belts each on the same class. Not done: chin-ups (the S&C: elbow load), the thoracic extension (kept in reserve), and the behind-bar support (it cost the Korean dip's rung below its 11 sets).
- **Review 9 (2.6.3), a fresh panel.** Three new AI personas, class coaches with 5, 10 and 20 years of teaching, read the whole program and asked whether they would give it to their own students. All three said "yes, if", at 8/10. Their must-fixes are in:
  - **The safe way down first:** the cartwheel exit from L1:023, a side at a time; the kick-up from L1:072, once the wall holds reach 30 s, always after an exit; the exit rehearsed in the warm-up before the first balance attempts (L3:027–L4:015).
  - **Readiness gates that match the classes:** 20 "Start when" lines rewritten to what the classes reach before each step (two named movements no class uses), checked by `validate_progressions.py`. The elevated pike push-up's safety now matches its counting rule, and the weight shift counts time.
  - **Records fresh:** a Level 10 record comes first in its class, after a ramp on the rung below (the assisted Hefesto before the Hefesto record); the long hang record comes last.
  - **Exams:** no step over the trained dose on a rung under 21 classes old, and never on the muscle-up, one-arm pull-up or Hefesto. A block exam says a skill new in its block is tested at the next one.
  - **The muscle-up bridge:** chest-to-bar keeps a set until the strict muscle-up, and the first strict blocks (to L4:076) keep a negative beside it.
  - **Whole circuit rounds:** a basics or Battle class drops a whole round, never a station; without a round left it is "Basics & Strength".
  - **The front day's flag:** the straddle flag at 70% while the full flag's 70% would floor to its 3 s minimum.
  - **The freestanding press before the wall reps**, and the Korean dip's starting position (the behind-bar support) in the bar-class warm-up from L7:019 to L9:040.
  - **Set notes:** the kick-up, bar walk, bear crawl and squat jumps read "controlled"; the Level 3 squat jumps open their class.

  Belts stay at 163. The muscle-up's Beginner and Novice belts each come one week later, and the full flag's Beginner at L8:005. Not done in 2.6.3: rehearsing the Full Battle before L10:100, dynamic lever and planche strength, and a real-athlete beta, which needs people.
- **2.6.4, the owner's "go fix" of what review 9 left open:**
  - **Dynamic lever strength:** the tuck front-lever row (new movement and animation, feet free on the high bar) takes the feet-elevated rows' place on the bar day in Levels 5–7, from L5:005.
  - **Dynamic planche strength:** the tuck planche push-up (new movement and animation) takes the tuck back-off set on the Level 7 planche day in blocks 2 and 4. Blocks 1, 3 and 5 keep the hold, which still reaches its Elite target.
  - **Chin-ups** for the slow pull-up negatives in Level 1's last block (L1:088, L1:095), 2 × 2 lowered in 3 s: the same sets, the grip that gives a novice an extra rep.
  - **The Full Battle rehearsal (L10:090):** after the lever sets, three of the champion test's five rounds at its own numbers and rests (10 pull-ups, 20 dips, 30 push-ups, 40 squats; 30 s and 90 s), a day type of its own.

  - **Belts the classes complete (review 11, the owner):**
    - Sets 10/15/20/25/30 for Beginner to Elite, five more each belt.
    - A harder rung's set counts for the easier rungs of its ladder measured alike, at the belt it reaches on its own rung: a 6 s full planche is an Elite set of the straddle planche.
    - Ten targets fitted to the classes: one-arm pull-up 1/1/1/1/2 a side, strict muscle-up Elite 7, full back lever Elite 14 s, one-arm push-up 1/3/4/4/5 a side, deficit wall HSPU Elite 7, one-arm handstand 6/7 s a side, V-sit Elite 7 s, full flag 5/6 s a side, shrimp squat Elite 8 a side, Hefesto 1/1/2/2/3.
    - Every skill reaches Master inside the program. The basics, the core ladder (the owner: leg raise, toes-to-bar, L-sit, V-sit) and the easier rungs finish Elite there too. Each other ladder's top rung holds a step under its Elite target until its class in `ELITE_FROM`, then at it, so the classes give 23–27 of its 30 Elite sets (77–90%); a repeated block or the athlete's own training does the rest. Level 10 needs no record sets.

  Both new movements have readiness gates checked by `validate_progressions.py`, and the app counts the row as a front-lever foundation (the push-up already was one for the planche). The library has 184 movements; 184 of 200 belts land in classes, every Master and 24 Elites.
- **2.6.5, skill switches (review 12, the owner):**
  - **Settings → Skills in your classes:** a switch per skill, and a tap on a skill opens its progression to switch it off from a rung on. The basics (dead hang, pull-up, dip, push-up, squat, hollow body) stay on.
  - **The lines:** each family's line is curated in `SkillProgressions.LINES` (`data/skills/SkillSwitches.kt`), in first-use order, each step with the rung it leads to. Shared work belongs to no line. A step two lines share (the German hang) goes off only when both lines are off, and the freestanding HSPU needs the freestanding handstand.
  - **In the classes:** switched-off exercises stay visible, greyed, and out of the set count. A tap asks before doing one anyway. A class with nothing left to train counts as passed (`NextClass.passed`) and earns nothing; one family off never empties a class.
  - **Exams and skills:** an exam doesn't test a switched-off skill. The skill score is out of the skills trained; the leaderboard keeps the full scale.
- **2.6.6, first screen and shorter texts (the owner):**
  - **First screen (design B of three):** the placement test asks "How strong are you today?" under a dark header, with three big counters. The start shows on yellow tape as soon as all three counts are in. One card below says what the start opens with, what it assumes (one target per line) and what a few more reps would change. The skills step is folded under one line below the start button.
  - **Sharing progress:** a note and switch close the first screen and sit in Settings › Your data, on by default (`AppSettings.shareProgress`). Nothing is uploaded yet: the upload, anonymous sign-in and database rules wait for the owner's go-ahead.
  - **Shorter texts:** 36 app strings are shorter and plainer; the Skills help went from about 600 words to 300. The class texts follow in 2.6.7.
- **2.6.7, shorter class texts (the owner):**
  - **Classes:** a description is the block and one line about the class. The exercise list it repeated is gone, and so is the advice the readiness text gives. Readiness, the same for every class, is 31 words instead of 108; safety is 25 instead of 45; an exam says what it asks in about 45 words. Class texts went from 260,000 words to 77,000, and the asset from 3.05 MB to 1.93 MB. Only text changed: ids, titles, steps, doses and the belt report are as before.
  - **Where the texts live:** readiness, recovery and the level descriptions are in `curriculum/catalog_metadata.json`, the movements in `curriculum/movement_library.json`, and the class lines in the generator's `ROLE_SENTENCE`, `KIND_SENTENCE` and `EXAM_SENTENCE`.
  - **Movements:** 97 entries are shorter, and every "Start when" number is unchanged. No safety note contains "; " any more: the app splits a movement's notes there, so 42 movements showed part of their safety note in the description.
  - **Fixes:** Levels 7 and 10 no longer promise work they don't contain, and the class before an exam says "Tomorrow is the exam" instead of "1 classes to the level test".
- **2.6.8, every exam a skills test (the owner):**
  - **Level 10:** L10:100 is the Level 10 test, a full skills test like L9:100 (headline: the one-arm pull-up). The Full Battle is a class on L10:090 in place of its rehearsal, five rounds at its numbers and rests; L10:076 is a block exam again ("Block 4 Exam · Full Front Lever"). The `battle_rehearsal`, `exam_level_full_battle` and `exam_block_champion_statics_strength` day types are gone; the Full Battle's day type is `full_battle`.
  - **Targets only:** the German hang and the tuck holds that stood between the tests close the exam's warm-up instead. An exam over the hour shortens its strength rests to 120 s before a test waits, so the warm-up's extra minutes cost no test.
  - **More families tested:** a fifth leg and trunk test, the Level 9 and 10 tests hold all nine max-strength families (the one-arm push-up joins), and Levels 1–2 test the dead hang beside the pull-up (one more test each). Families a block trained but its exam left out fell from 97 to 62 over the 50 exams.
  - **Belts:** 185 of 200 in the classes (straddle planche Elite lands at L10:100). The squat's Elite moves to the Full Battle (L10:090) with the pistol's Elite class at L10:009 (was L10:013); the full flag's last bar set before the test (L10:089) holds 5 s, so the classes keep 27 of its 30 Elite sets (`hold_elite_share`). The easy class before a level test keeps one easy one-arm push-up set from Level 8.

## The frame

| | |
|---|---|
| Levels | 10 × 100 classes; Free 1–3, Plus 4–5, Pro 6–10 |
| Exam classes (frozen) | L1 20/40/60/81/100 · L2 20/40/62/84/100 · L3 20/40/64/84/100 · L4–L10 13/34/55/76/100 |
| Belts | 40 skills × Beginner–Elite, 10/15/20/25/30 sets at the target; a harder rung's sets count down its ladder |
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
- **Tempo week (review 8).** A build class that would repeat the same class a week before lowers its rep steps in 3 s (tempo 3111): the same sets and reps. Never on the elbow list, the handstand push-ups, holds or pull-ups over 6 reps, never in the 3 classes before an exam, and only when every set still fits the class's minutes.

## The blocks

From Level 4 every block runs from the recovery after one exam to the next exam:
1. **Re-entry**, an easy class after the exam (block 1 opens with a re-entry and then carries the last level's end doses for five classes).
2. **Week A** at the block dose, then **week B** one step up.
3. **Hold and trim** classes in the longer blocks, and a deload in block 3.
4. **Exam week:** position 4 is a light handstand-and-press class (no slow negatives), position 5 a light bar class (no flag, one set at about 70% of each line the exam tests, two of the Hefesto line's hangs until the strict Hefesto is tested, so the exam meets fresh elbows), and the exam falls on position 6, its warm-up the primer.

Block 5 (classes 77–100) ends with a taper into the level exam on class 100: 92–94 deload, 95 hold, 96 light, 97 easy, 98 recovery, 99 primer.

In Levels 1–3 the exam replaces that class. The class before it is the primer, an exam on a recovery position makes the class two before it easy, and the class after the exam is the re-entry.

## When the steps start and the full shapes arrive

The first class each movement appears in, counted from the program:

| Line | Steps and their first class |
|---|---|
| Front lever | tuck_front_row L2:008 · tuck_front_hold L2:022 · advanced_tuck_front L4:008 · one_leg_front L5:036 · straddle_front L7:015 · full_front L8:015 |
| Back lever | skin_cat_partial L2:068 · german_hang L2:025 · tuck_back_hold L3:012 · skin_the_cat L3:067 · advanced_tuck_back L4:061 · straddle_back L5:082 · full_back L6:047 |
| Planche | planche_lean L2:052 · frog_stand L1:044 · pseudo_planche_pushup L3:003 · tuck_planche L4:003 · advanced_tuck_planche L6:003 · straddle_planche L8:003 · full_planche L9:059 |
| Flag | flag_ground_support L3:005 · assisted_tuck_flag L3:047 · vertical_flag L4:026 · tuck_flag L5:005 · straddle_flag L6:082 · flag_negative L7:019 · full_flag L7:061 |
| Pull | eccentric_pullup L1:025 · pullup L1:064 · archer_pullup L5:008 · assisted_one_arm_pullup L7:043 · one_arm_pullup_negative L8:036 · one_arm_pullup L9:015 |
| Muscle-up | low_bar_transition L2:043 · band_muscleup L3:050 · chest_to_bar L3:050 · negative_muscleup L3:071 · strict_muscleup L4:043 |
| Handstand | wall_handstand L1:022 · handstand_balance L3:027 · freestanding_handstand L4:006 |
| One-arm handstand | straddle_handstand L6:020 · handstand_weight_shift L7:041 · fingertip_one_arm_handstand L8:041 · straddle_one_arm_handstand L9:006 · one_arm_handstand L9:069 |
| Press | pike_pushup L1:044 · elevated_pike_pushup L3:013 · wall_hspu_negative L3:053 · wall_hspu L4:067 · deficit_wall_hspu L8:018 · freestanding_hspu_negative L10:039 |
| Core | lsit L3:023 · toes_to_bar L4:015 · bent_dragon_negative L3:051 · dragon_flag L6:023 · v_sit L8:009 |
| Legs | pistol_squat L4:009 · nordic_negative L4:023 · nordic_curl L7:023 |
| Shrimp | assisted_partial_shrimp L3:002 · assisted_shrimp L5:016 · floating_shrimp L6:037 · assisted_held_shrimp L7:002 · shrimp_squat L7:058 |
| Hefesto | assisted_supinated_german_hang L6:040 · assisted_korean_dip L7:026 · supinated_german_hang L7:026 · assisted_partial_hefesto L8:005 · assisted_hefesto L8:061 · hefesto L9:040 |

Straight-arm rungs on one ladder start at least 60 classes apart; no two start in the same week.

The four batch-2 movements:
- **Vertical flag** (seconds a side), hips stacked over the shoulders on the pole: the flag line's prep set from L4:026 until the tuck flag starts. It does not claim the bar class's lead, so the tuck back lever keeps its Level 4 volume.
- **Flag negative** (reps a side), lowered from the vertical through the line: from L7:014 until the full flag starts, it takes the place of one straddle-flag set on each flag class, so the elbow count stays the same.
- **Full skin-the-cat** (reps), through the German hang and back: the handstand-and-press class's shoulder-extension set from L3:067.
- **Free supinated German hang** (seconds): the Korean dip's back-off once it starts, on the bar class only. Review 6 took it off the press class, the class before the Korean dip; the press class keeps the skin-the-cat.

The exams that mark the plan:
- **L7:100, Advanced Athlete.** A fresh set of each standard, or what the athlete already trains when that is more, then the other trained families up to the caps.
- **L9:100: every full shape.** The one-arm handstand, full planche, full front lever, Hefesto, full back lever, full flag and one-arm pull-up, with the strict muscle-up, the deficit wall HSPU and the basics: pull-ups, dips and push-ups.
- **L10:090, the Full Battle (a class, not an exam).** Five rounds of 10 pull-ups, 20 dips, 30 push-ups and 40 squats, with 30 s between stations and 90 s between rounds, every set as written; day type `full_battle`, outside the set budget.
- **L10:100, Level 10 Test: Beyond Gravity.** Like L9:100: the one-arm handstand, full planche, full front lever, full back lever, full flag, muscle-up, one-arm pull-up, deficit wall HSPU, Hefesto and one-arm push-up, then the basics: pull-ups, dips and push-ups. Headline: the one-arm pull-up. L10:076 is a block exam again.

**What an exam tests (review 6; the owner, 2.6.8: every exam is a skills test).** An exam is a real test of the level: one fresh set of every skill family the block trained, at least 4 sets of it in the 21 classes before, on its hardest rung with two weeks behind it. A lower rung of a tested ladder holds its belt through the harder one, so it isn't tested; the pull-up and push-up are, beside the one-arm rungs, because reps measure something else. In Levels 1–2 the dead hang is tested beside the pull-up; from Level 3 the pull-up holds it.
- **Order:** balance first (the handstand line), then the holds (planche, front lever, back lever after a German hang, flag), the bent-arm strength (muscle-up, one-arm or archer pull-up, the press, the Hefesto, the one-arm push-up), the basics (pull-ups, dips, push-ups) and the trunk last. A leg test sits inside the rest after a hold: 60 s, the leg test, then 90 s.
- **Rest:** 150 s after a hold or strength test (120 s in Levels 3–4), 120 s after the handstand and the basics, 90 s after a leg test, 60 s between trunk tests.
- **Caps:** at most 15 tests (14 in a block exam; Levels 1–2: 13 and 11): 8 max-strength tests (7 in a block exam, 9 in the Level 9 and 10 tests), 3 basics, 5 leg and trunk tests, 8 elbow-load sets with the German hang, 10 advanced sets and 60 minutes. Over a cap, the family tested most recently waits for the next exam, the supporting strength tests first; the planche, front lever, back lever and flag are never left out, nor the exam's newest skill. From Level 4 every exam tests 12 to 15 skills (Levels 1–3: 6 to 14).
- **Dose:** the trained dose, the best of the last 21 classes. Only the headline item may go one step over, and only up to a belt target.
- **The repeat offer:** missing the headline skill, or three targets or more, offers to repeat the block; any other miss is simply tested again at the next exam.
- **Warm-up:** the general part, the wrists, the shoulder blades and an active hang, then a ramp set of the first test (the straddle and full shapes two rungs down) and from Level 7 one of the levers.
- **Prep sets in the warm-up (2.6.8, the owner):** the exam lists only targets. The German hang before a back-lever or Hefesto test, and one set of the tuck rung at half its dose before a straddle or full front lever, back lever or flag test (review 7's ramps), close the warm-up in test order, 30 s apart; they count toward no cap.
- **The press parts the elbow work (review 7):** whenever the one-arm pull-up or the Hefesto is tested, the handstand press is too, between them, so no exam runs more than five elbow-load sets in a row. Over the strength cap the one-arm push-up waits first, then a block exam tests one of the two.
- **Over the hour:** the strength rests shorten to 120 s first (2.6.8), then leg and trunk tests rotate out, and only then a basic waits.
- **Headline:** the named exams of Levels 1–2 headline the skill in their name (First Pull-Up: the pull-up); the others the newest belted skill no earlier exam of the level named. Every exam's description ends with its headline.

Level 10 takes every skill to Master and each ladder's top rung most of the way to Elite (review 11). The one-arm pull-up, strict muscle-up, full front and back levers, one-arm push-up, deficit wall HSPU, full planche, one-arm handstand, pistol squat, dragon flag, full flag, shrimp squat and Hefesto hold a step under their Elite target until their class in `ELITE_FROM` (L8:069 to L10:058), then at it: the classes give 23–27 of their 30 Elite sets. The V-sit's class (L9:054) comes early enough to finish its Elite, and with it the L-sit's. No record sets are needed. Progressions peak while they are still trained; no lever, planche or flag record sits beside a harder rung of its ladder.

## Where each belt lands

The same table as a page, with milestones and the months at any pace: [ACHIEVEMENTS.html](ACHIEVEMENTS.html), rebuilt by `tools/build_achievements_page.py`.

Counted from the classes, if every set is done as written on the Normal toggle, with a harder rung's sets counting for the easier rungs measured alike. A percentage is an Elite the classes take that far, the sets they give at its target out of 30; block repeats or the athlete's own training do the rest. A * marks a belt held through a harder rung of the same ladder: the level of the harder rung holds on the easier ones, never higher.

| Family | Skill | Targets | First set | Beginner | Intermediate | Advanced | Master | Elite |
|---|---|---|---|---|---|---|---|---|
| Pull-up | dead_hang | 30/45/60/75/90 s | L1:001 | L1:018 | L1:100 | L5:055* | L8:055* | L10:043* |
| Pull-up | pullup | 3/6/10/15/20 | L1:064 | L2:013 | L3:048 | L5:055 | L8:055 | L10:043 |
| Pull-up | archer_pullup | 2/3/4/6/8 a side | L5:008 | L5:055 | L7:029 | L8:050 | L9:057 | L10:064 |
| Pull-up | one_arm_pullup | 1/1/1/1/2 a side | L9:015 | L9:050 | L9:071 | L9:100 | L10:022 | 77% |
| Muscle-up | strict_muscleup | 1/2/4/6/7 | L4:043 | L4:071 | L5:050 | L7:071 | L9:092 | 87% |
| Front lever | tuck_front_hold | 5/8/12/16/20 s | L2:022 | L2:040 | L2:078 | L4:008 | L6:055 | L10:071 |
| Front lever | advanced_tuck_front | 5/8/10/12/15 s | L4:008 | L4:029 | L5:043 | L6:078 | L10:015 | L10:078 |
| Front lever | straddle_front | 3/5/7/10/12 s | L7:015 | L7:036 | L8:008 | L9:036 | L10:036 | L10:092 |
| Front lever | full_front | 3/4/5/7/10 s | L8:015 | L8:036 | L8:071 | L9:055 | L10:057 | 87% |
| Back lever | tuck_back_hold | 5/8/12/16/20 s | L3:012 | L3:034 | L3:064 | L3:097 | L4:047 | L5:076 |
| Back lever | advanced_tuck_back | 5/8/10/15/20 s | L4:061 | L4:089 | L5:033 | L5:047 | L9:082 | L10:076 |
| Back lever | straddle_back | 3/5/8/10/12 s | L5:082 | L6:006 | L7:019 | L8:055 | L10:047 | 93% |
| Back lever | full_back | 3/5/8/12/14 s | L6:047 | L6:082 | L7:068 | L8:061 | L10:061 | 80% |
| Dip | parallel_dip | 4/10/15/20/25 | L1:062 | L1:078 | L2:094 | L5:052 | L8:052 | L10:034 |
| Push-up | pushup | 6/12/20/30/40 | L1:001 | L1:013 | L1:064 | L2:062 | L5:080 | L9:013 |
| Push-up | one_arm_pushup | 1/3/4/4/5 a side | L7:006 | L7:048 | L9:027 | L10:027 | L10:083 | 83% |
| Handstand push-up | pike_pushup | 6/8/10/12/15 | L1:044 | L1:079 | L2:004 | L2:081 | L9:004 | L10:076 |
| Handstand push-up | wall_hspu | 2/4/6/8/10 | L4:067 | L4:088 | L5:081 | L7:067 | L10:032 | L10:081 |
| Handstand push-up | deficit_wall_hspu | 2/3/5/6/7 | L8:018 | L8:039 | L8:100 | L10:018 | L10:081 | 83% |
| Planche | pseudo_planche_pushup | 5/8/10/12/15 | L3:003 | L3:031 | L4:087* | L5:080* | L8:024 | L10:073 |
| Planche | tuck_planche | 5/8/10/12/15 s | L4:003 | L4:017 | L4:087 | L5:080 | L8:087 | L10:080 |
| Planche | advanced_tuck_planche | 5/6/8/10/12 s | L6:003 | L6:066 | L6:087 | L7:055 | L9:038 | L10:080 |
| Planche | straddle_planche | 3/4/5/6/8 s | L8:003 | L8:024 | L8:052 | L8:100 | L9:066 | L10:100 |
| Planche | full_planche | 3/3/4/5/6 s | L9:059 | L9:080 | L9:087 | L10:024 | L10:066 | 83% |
| Handstand | wall_handstand | 20/30/45/60/75 s | L1:022 | L1:030 | L1:082 | L3:004 | L5:046 | L9:008 |
| Handstand | freestanding_handstand | 5/15/30/45/60 s | L4:006 | L4:015 | L5:046 | L6:031 | L8:004 | L9:011 |
| Handstand | one_arm_handstand | 3/4/5/6/7 s a side | L9:069 | L9:090 | L10:039 | L10:067 | L10:092 | 80% |
| Pistol squat | squat | 15/20/25/30/40 | L1:003 | L1:013 | L1:045 | L3:009 | L7:044 | L10:090 |
| Pistol squat | pistol_squat | 2/4/6/8/10 a side | L4:009 | L4:037 | L5:051 | L7:002 | L8:009 | 87% |
| Toes to bar | hanging_leg_raise | 5/8/10/12/15 | L2:022 | L2:036 | L3:020 | L3:100 | L9:026 | L10:043 |
| Toes to bar | toes_to_bar | 3/6/8/12/15 | L4:015 | L4:068 | L6:036 | L7:047 | L9:026 | L10:043 |
| L-sit | lsit | 5/10/15/20/30 s | L3:023 | L3:051 | L4:051 | L6:009 | L8:051 | L10:037 |
| L-sit | v_sit | 3/4/5/6/7 s | L8:009 | L8:030 | L8:051 | L9:072 | L10:016 | L10:044 |
| Dragon flag | hollow_body_hold | 20/30/40/50/60 s | L1:001 | L1:011 | L1:064 | L3:002 | L4:065 | L5:086 |
| Dragon flag | dragon_flag | 1/3/5/6/8 | L6:023 | L6:037 | L7:051 | L8:065 | L9:009 | 87% |
| Human flag | tuck_flag | 5/8/10/12/15 s a side | L5:005 | L5:026 | L6:034 | L6:089 | L9:100 | L10:068 |
| Human flag | straddle_flag | 3/4/6/8/10 s a side | L6:082 | L7:008 | L7:089 | L9:089 | L10:055 | 97% |
| Human flag | full_flag | 3/4/5/5/6 s a side | L7:061 | L8:005 | L9:034 | L10:055 | L10:068 | 90% |
| Shrimp squat | shrimp_squat | 2/3/5/7/8 a side | L7:058 | L7:079 | L8:030 | L9:030 | L10:051 | 77% |
| Hefesto | hefesto | 1/1/2/2/3 | L9:040 | L9:076 | L9:096 | L10:034 | L10:055 | 87% |

**185 of 200 belts land in classes:** every Master, and Elite for 25 skills; the other 15 Elites stand at 77–97%. By level, Levels 1–10 land 12, 8, 11, 14, 18, 12, 15, 24, 28 and 43. The first belt comes at class 11 (hollow body Beginner), and the push-up and squat follow at class 13. Every level brings new ones; the last first belt is the one-arm handstand's Beginner at L9:090.

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

- **Frame:** frozen exam classes, a re-entry after every exam, every class 55 minutes or less, an exam 60.
- **Budget:** each class within its set budget and per-step limit, with rep caps of push-up 50, squat 40, dip 30, pull-up 21 and leg raises 15.
- **Easy classes:** RPE 4 or less, no practice part, no assessments, at most two sets of anything. From Level 4, any 21 classes hold at most 6 lighter, primer, easy or re-entry classes.
- **Lighter exam weeks:** at most 70% of the week before.
- **Ramp sets (review 7):** every straddle or full lever and flag line gets a ramp set before its first set in a class, not only the class's lead; in exams, the tuck rung right before the test.
- **Elbows:** at most 6 elbow-load sets a class (7 on a three-line bar class, 4 on position 6, which follows the bar class, and on the first full class after an exam, 3 in the light bar class, 8 in an exam); heavy elbow classes never back to back. High-rep pull-ups (12 or more) and dips (18 or more) run one set a class at the dose and the rest at 75% (the generator's rule).
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
- **Exams:** at the trained dose, with at most two items a step over; from Level 4, 12 to 15 tests, at most 8 elbow-load sets besides the ramps. The step over is only for a rung at least 21 classes old, never for the muscle-up, one-arm pull-up or Hefesto (review 9).
- **Records (review 9):** first in the class after a ramp on the rung below; the long hang record last. Since review 11 the top rungs train at their Elite target instead, and no class needs one.
- **Circuits (review 9):** whole rounds only, in the circuits, the basics and the Battle; the fit drops a whole round, last first.
- **Readiness gates (review 9):** every "Start when" line in the library is met by a class before its step starts.
- **Skills:**
  - all 40 trained, each at its Elite target at least once or holding Elite through a harder rung;
  - every skill to Master inside the program, and every Elite at least 21 of its 30 sets there (review 11);
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

## Class names (review 7)

A class is named for what it trains, so the list reads like a program, not a rotation (the owner, 30 October 2026):
- **What it trains:** its lead and the next line of another family, "Full Front Lever & Muscle-Ups", "Straddle Planche & Dips".
- **Firsts:** the class a new movement arrives is named after it, "First Strict Pull-Up", "First Hefesto".
- **Records:** a Level 10 record class says so, "Record · One-Arm Handstand".
- **The rest:** lighter classes lead with their kind ("Lighter · …", "Light Bar · …"), the class before a level test names it ("Exam Prep · Level 9 Test"), and the circuits, basics, Battle and Statics Chain keep their names. Exams keep theirs.
- **What rose (review 8):** the second half of a name is the step that rose since the last class of its type, never a rung of the lead's own ladder, so a name changes when the class does ("Frog Stand & Pseudo-Planche Push-Ups", then "Frog Stand & Dips"). A tempo week says so ("Tempo · Tuck Front Lever & Pull-Ups", "Legs & Core · Tempo").
- **Firsts (review 8):** a skill line's foundation drill gets its "First" too ("First Skin-the-Cat", "First Vertical Human Flag"); supporting work never does.
