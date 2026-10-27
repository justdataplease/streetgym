# StreetGym curriculum method

## Round 6 (27 October 2026): exam days and the three-coach panel

A panel of three coaches (a street-workout group coach, a gymnastics strength coach and a strength coach with a physiotherapy degree) debated round 5b over three rounds. All three scored the curriculum 5.5/10. They scored "the strong advance, the weak stay, like a normal class" 2.5–3/10: the app moved everyone forward one class per class finished. The owner chose exam days. Validator rules 44–48 and wider rules 3, 5, 7, 11, 13, 31, 32 and 38 hold the changes; one deterministic pass made them.

- *Exam days.* An exam comes about every 20 classes, 35 in all:
  - L1: 20, 40, 60, 81, 98
  - L2: 20, 40, 62, 84, 98
  - L3: 20, 40, 64, 84, 100
  - L4–L7: day 6 of weeks 2, 5, 8, 11 and 14 (13, 34, 55, 76, 97)

  An exam is a normal class: warm-up, one fresh set of each skill the block trained, cool-down, and no practice block. Each target is the best per-set dose the block prescribed, so someone who did the classes passes. A target never falls below the previous exam's target for the same movement, and a block that moved nothing on raises one repetition target. A German hang comes directly before the back-lever test. Rule 44 keeps exams at most 25 classes apart, each testing only what its block trained.
- *The app.* After an exam class the athlete ticks the targets reached. The result is kept and synced with the other cloud progress. If a target was missed, the app offers to repeat the classes since the previous exam and take the exam again. It is the athlete's choice; Today runs the block as a level replay from its first class. Exam classes are marked in the level list and show their last result.
- *Original-source positions.* Four exams (L1:020, L1:040, L2:062, L3:020) take the place of original-source classes. `catalog_metadata.json` lists them as `replaced_source_slots`, and the authored classes live in `source_replacements.json`. They keep the original block's calendar, and the original content stays in the forensic export.
- *No advanced rung debuts inside a deload (rule 45).* The straddle levers start at L6:031 and L6:033 (2 × 3 s), no longer in the L6:024 and L6:026 deloads. The one-arm negative leaves the L7:079 deload.
- *Readiness checks come first among their skill's sets (rule 46).* The full-lever checks at L7:030 and L7:033 now come before the straddle sets. Their 3 × 8 s straddle gate moves a week earlier, to L7:016 and L7:019.
- *At most eight lever sets a class in every level (rule 38).* Nineteen Level 6–7 classes come down one set at a time. The German hang goes first, then one-leg holds (never below the straddle's 3 × 8 s gate), then straddle and advanced-tuck sets (never below two), then brief full holds. Every step stays, so the second weekly front-lever exposure stays.
- *Flags (rule 47).* Tuck-or-harder flags are maximal straight-arm holds, so they lead the main work rather than the practice. They never share a class with a tuck-or-harder planche hold. Levels 6–7 train the flag on day 6 only (it was also on the planche day), and from L7:076 day 6 checks the full flag before its brief holds.
- *Freestanding handstand (rule 48).* Holds join the balance practice: 1 × 5 s from L5:071, 2 × 5–10 s through Level 6 and 2 × 10–20 s in Level 7, in place of two balance-attempt sets. They come before the freestanding handstand push-up negatives.
- *Placement.* The strongest answers start at L2:071, so Level 2's German hangs, tuck levers and negatives come before Level 3.
- *Copy.* L3:065 describes the sets it prescribes. The Level 6–7 descriptions say most adults need two to four years for the full shapes.
- *Knock-on fixes.* Archer push-ups start at L4:069, a build class. The wall-HSPU gate (negatives 3 × 5) moves to a Level 5 build class. Level 7 deload weeks give up supporting sets so they stay at 70% of the build week. The validators leave replaced positions out of the authored weeks.
- *Left for the next round.* The panel also asked for a stored working rung per skill, with promotion by test, and for the original-source blocks (L1:001–040, L2:041–070, L3:001–020) to be rebuilt in the authored format.

## Round 5b (27 October 2026)

Round 5 was audited before release by three reviewers: a gymnastics coach (skill progression), a sports physiotherapist (tissue load) and a strength coach for beginners. All three said "ship after fixes". Where they disagreed, the physiotherapist's tissue-safety view decided. New rules 42–43 and wider rules 5, 13, 29, 32 and 39 hold the fixes.

- *Flag on consecutive days.* Round 5 moved Level 4–5 flag practice onto day 3, so flags fell on days 2 and 3, right beside the planche day. Levels 4–5 now train the flag once a week, on day 2 (2 × 8 → 3 × 8 → 3 × 10 s a side in Level 4; 3 × 8 → 3 × 10 s, then the tuck flag, in Level 5). Day 3 keeps only its handstand practice. Rule 43: from Level 3, flag work never falls on consecutive days. Level 3 drops the ground-support rung wherever the assisted tuck flag is trained.
- *Elbow runs across level ends.* Rule 29 now counts one run from Level 3 to the end, because the body does not reset at a level change. L6:001 becomes an easy re-entry class (RPE 5: strict muscle-ups 2 × 2, chest-to-bar 1 × 4, straight-bar dips 2 × 8), so L5:099–L6:003 is no longer five heavy days. With the flag off day 3, L3:100–L4:003 is no longer four.
- *Handstand push-ups first.* Wall handstand push-up negatives come before any German hang, lever or planche work in their class: 16 classes, including every Level 5 fundamentals day (rule 42).
- *L5:006.* It keeps the muscle-up pairs but holds the advanced-tuck back lever 2 × 10 s after a 10 s German hang. The one-leg back lever starts at L5:013, a class with no muscle-ups.
- *Set budget.* Each class's lead skill (its first advanced skill, else its first skill rung) keeps its authored sets, and trims fall on the supporting work. Gates no longer rest on a trimmed session: L4:097 keeps the advanced-tuck back lever at 3 × 10 s and L4:093 the one-leg dragon negative at 3 × 2 a side. Seventeen classes that this pushed past 55 minutes give back one set of supporting work.
- *Pressing.* Level 4 keeps the elevated pike push-up (2 × 8, then × 9 from L4:027) instead of stepping back to the floor version. The original-source blocks add pike push-ups twice a week to their practice (2 × 6 in L2:041–070, 2 × 8 in L3:001–020), and their recovery days add single-leg bridges 2 × 10 a side from Level 2. Rule 5 now checks overhead and hinge work by calendar week, original-source classes included.
- *Steadier steps.*
  - Push-ups go 3 × 10 → 3 × 11 (L1:053–067) → 3 × 12.
  - Dips go 3 × 6 → 3 × 7 (L1:057–066) → 3 × 8, and 3 × 9 from L2:026.
  - Pike push-ups go 2 × 6 → 2 × 7 (L2:016–026) → 2 × 8 (L2:030–040).
  - Sliding leg curls rise to 3 × 8 from L2:022.
  - The Level 3 wall handstand runs 2 × 35 s in phase 1, then 2 × 40 s with one 3 × 40 s a week in phase 2.
  - L5:041 runs 3 × 5 s a side, L5:064 3 × 7 s a side, L4:094 3 × 6 s.
  - L3:061 drops its hollow holds, so the tuck back lever keeps its sets.
- *Legs.* Paused squats become strength sets: 3 × 10 at 32X1 through Level 5, 3 × 12 later (deloads keep one set). L6:002's 2 × 32 squats become Bulgarian split squats 2 × 10 a side; the 20-repetition cap now holds on test days too, outside assessment sets. Bulgarian split squats 2 × 8 a side join L3:058 and replace L3:073's hollow holds. L1:050 and L1:064 swap split squats for glute bridges, so split squats never fall on back-to-back days.
- *Recovery and joint prep.*
  - RPE 4 classes stay within 30 minutes, with one walk a class (rule 39).
  - Deload weeks halve the practice too: one set a side of flag work and half the other sets.
  - The day-4 recovery class after the planche day adds wrist push-ups, 2 × 10 rising to 2 × 15 from Level 4 to Level 7.
  - Handstand-balance and handstand push-up days open with a 30 s wall shoulder opener.
  - The thoracic extension now says to cradle the head, never pull on the neck. The all-fours rotation is renamed "Quadruped Thoracic Rotation" to match its clip.
- *Validator.* Rule 13's weekly Nordic and toes-to-bar checks had slipped inside the planche check and never ran; they run again.
- *Left open for the reviewer.*
  - The coach asked for a weekly flag session in L3:078–084 and L3:092–098, and the tuck front-lever hold back at L3:027. Both would make four heavy elbow days in a row unless the foot-assisted tuck flag stopped counting as heavy, which the physiotherapist advised against.
  - The strength coach asked for the post-source deloads (L2:073–077, L3:022–026) to become RPE 6 ramp classes.
  - The physiotherapist flagged back-lever days before pseudo-planche days at L3:057→058 and L3:092→093.

## Round 5 (27 October 2026)

A former gymnast who now coaches adults reviewed the whole program. The order of the skills held up; the review was about how load is spread. The changes below follow its report, and validator rules 38–41 plus wider rules 5, 8, 13, 29 and 32 hold them.

- *Elbow load in Levels 3–5.* Rule 29 (no four heavy elbow-flexor days in a row) now covers Levels 3–5, counted inside each level (round 5b: across level ends). L3:066 rows instead of pulling up; L3:076 and L3:090 drop their German hang and tuck lever holds (L3:090 keeps tuck front-lever rows); L3:027 rows the tuck front lever. In every Level 4–5 week the German hang and back-lever rows move from the muscle-up day to the next day's fundamentals class, whose pull-ups become feet-elevated rows (archer pull-ups move to that week's front-lever day). Level 5's first week swaps days 5 and 6 so the back lever stays seven days from the next, and drops the one-leg front lever from its muscle-up class. Rule 38: before Level 6 a class holds at most eight straight-arm lever sets (per-side sets count twice).
- *Overhead pressing.* Pike push-ups 2 × 6 in Level 2's push, fundamentals and deload classes (rule 5 from Level 2); a second pressing class in every Level 3 week, elevated pike push-ups from L3:044 (rule 13 from Level 3); wall handstand push-up negatives 2 × 3 at L4:062, 069, 076, 090 and 097 (rule 8), with planche leans in place of tuck planches on those days.
- *Level 1 and early Level 2.* Push-ups 3 × 10 from L1:043 and 3 × 12 from L1:069, paused push-ups 3 × 8 at L1:099; dips 3 × 6, then 3 × 8 from L1:069; split squats or reverse lunges in place of the squat fillers from L1:049. Fewer exercises per class carry those third sets inside the 14-set budget. L1:062–067 and L2:015–017 are normal build classes. Deload weeks lower slowly with dip negatives (the eccentric dip). The Level 1 source block's half dips now project to the assisted dip.
- *Daily practice.* The handstand ladder starts earlier and rises gradually: pike handstand holds join the overhead line in Level 1 phase 2, a 10 s wall handstand comes in phase 3, then 2 × 15, 2 × 20 and 2 × 30 s through Level 2 and 2 × 30 s in Level 3 phase 1. The assisted tuck flag starts in Level 3 phase 1 (2 × 5 s a side, once a week) and Level 4 keeps it at or above Level 3's best. Level 4–5 flag practice moves from day 6 (now a back-lever day) to day 3 (round 5b: day 2 only).
- *Planche spacing.* No tuck-or-harder planche hold the day after another, and the week's planche classes lie at least 48 hours apart (rule 13). Planche rows leave L4:037, L4:086, L5:002, L5:009, L5:030 and L5:086; planche leans 2 × 20 s join L4:041, L5:001, L5:013 and L5:034.
- *Reps.* Squat and push-up variations stop at 20 repetitions a set outside test days (rule 32). Squat rows of 25 or more become Bulgarian split squats 3 × 8 a side (Level 3 to L3:080), assisted pistols 3 × 6 a side (to the end of Level 4) or Bulgarian split squats 3 × 10 a side (later); rows of 21–24 become 20. Sliding leg curls 2 × 8 join Level 2's circuits.
- *Recovery and joint prep.* Four new movements: wrist push-ups, the wall shoulder opener, pancake compression lifts and the bench thoracic extension. Wrist push-ups replace the wrist rocks (or one set of wrist circles) in the warm-up of every class from Level 3 with planche or balance work (rule 40). RPE 3 classes stay within 25 minutes, with walks of 8 minutes or less and 3 minutes of slow breathing or less (rule 39). Every week from Level 3 has one skill-mobility class whose three new drills rise week by week (rule 41).
- *Time.* Listed minutes follow the assembled classes; seven classes that grew past 55 minutes lose a lower-priority set or step.

## Round 4 (26 October 2026)

Three coaches who train everyday people reviewed the calibrated program. Their reports are in `docs/pro-reviews-2026-10-round4/`, and all three said "ship after fixes, no blockers". One pass applied their consensus. Each changed line opens lower where needed and then rises, so rule 32's "no falling back" still holds.

- *L1:041 seam.* L1:041 becomes a moderate re-entry class for the new placement start: people with 20+ push-ups and 30+ squats but 0–4 pull-ups. It has push-ups 2 × 12, squats 2 × 15, full dips 2 × 6 (assisted dip as the easier rung), foot-assisted pull-ups 2 × 5 and pull-up negatives 2 × 3. Level 1 classes 42–100 keep full dips and pull-up negatives on the bar days. Push-ups rise 12 → 13, dips 6 → 7 and squats 15 → 18, meeting Level 2's openers.
- *Level 3 source block.* Plain pull-ups and chin-ups become library pull-ups and chin-ups, capped at 8 with at least 90 s rest. They keep their grip note and show the foot-assisted rung. Bar and parallel dips show their easier rungs. Dip spikes come down: L3:008 to 24 reps, L3:013 to 34, L3:036 to 36 and L3:064 to 42. Source rests are capped at 120 s, so every class fits 55 minutes by the length estimate (rule 35).
- *Level 4–5 pull-ups.* 3 × 8 → 3 × 9 → 3 × 10 through Level 4, then 3 × 10 → 3 × 11 through Level 5 (rule 36). Paused 22X2 sets run 3 × 6–7. Sets of 10 or more rest 120 s. L5:003 is back to three sets, and the pull-up safety text says to finish a short set foot-assisted.
- *Levels 6–7.* Day 7 is an easy day: RPE 5, ten sets or fewer, no strict pulling, handstand push-ups first (rule 37). Level 7's one-arm work leaves the day after the back lever for that week's front-lever day. Front- and back-lever classes carry two sets of the advanced-tuck or one-leg rung, in place of rows or extra straddle sets.
- *Minors.* Handstand push-ups come first in their class. The wall handstand rises 1 × 15 s → 2 × 20 s from Level 2 into Level 3. Kick-ups run in ten classes before the first balance attempt (was five), and in thirty in all. The negative muscle-up has a "Start when" gate. Benchmark and checkpoint classes read "clean reps up to N". Level 4–5 day-2 planche holds become L-sits where day 6 can take a lean.
- *Rule 32 exception.* Slow-tempo and paused sets (22X2, 31X1, 41X1) are exempt from rule 32's best-so-far check, because the coaches asked for 3 × 6–7 paused pull-ups.

## Round 3 (26 October 2026)

Three new reviewers (a street-workout coach, a gymnastics coach and a physio/S&C coach) checked the program after round 2. Their reports are in `docs/pro-reviews-2026-10-round3/`. They agreed on three points:
- The original-source blocks (L1:1–40, L2:41–70, L3:1–20) still showed steps with no easier option, ahead of the library's own ladders. Examples were unassisted dips, jumps to a high bar, inversions, L-sit pull-ups and hanging L-sits. "Muscle Up 5" and head bangers were hidden inside compound sets.
- There were load cliffs at the Level 1→2, 3→4 and 5→6 boundaries, and too few recoveries and deloads across level ends.
- Levels 6–7 stacked up to six heavy elbow-flexor days, and four heavy wrist days, in a row.

The owner set the audience: motivated recreational adults who download a calisthenics app. One deterministic pass applied the accepted fixes.

**Original-source projection.** The two override maps are gone. `morning_class.json` now holds one ordered list, `early_projection`, that names every distinct original step shown in the 64 training classes:
- A step the library has prepared by that class stays (`keep`). Its rule names the library movement that prepared it, or `entry` for entry-level Level 1 steps.
- Any other step becomes a library movement with its easier rung. That movement is of the same difficulty, at the original dose, wherever one exists: dips → parallel dips, push-ups → push-ups, rows → rows, jump-assisted pulls → foot-assisted pull-ups.
- A step moves to an easier movement only where the reviewers showed it comes before its ladder. Muscle-ups (also inside compound sets) become band muscle-ups. Negative muscle-ups drop to 1 × 3 and 3 × 2. One-third back-lever lowerings and inverted-L holds become tuck entries. Unassisted inversions become supported skin-the-cat, and strict high-bar pull-overs the library pull-over. Hanging L-sits and toes-to-bar become knee raises with a hold. Single-leg squats become supported pistols. Korean dips and reverse typewriters become straight-bar dips, and head bangers feet-elevated rows.
- Level 3 pull-up sets are capped at 10, with L3:2 at 6 and L3:6 at 3 × 8, so its opening weeks sit near the blocks on either side.

The assembler (`tools/assemble_curriculum.ps1`) applies the rules. It also keeps per-side notes true after capping: "10 reps on each leg" over 12 repetitions reads "6".

**Cliffs and recovery.**
- Level 1's last classes ramp up into Level 2: pull-ups 2 × 3 with chin-ups 3 × 4 at L1:97, and parallel dips at L1:92, 94 and 99.
- Level 4 gets strict muscle-ups at 2 × 2 and band muscle-ups first on day 1.
- L3:97 becomes a recovery class, so no more than six training days run in a row.
- New deload weeks at L2:73–77, L3:22–26, L4:85–90, L5:85–90 and L6:85–91 keep deloads no more than 35 classes apart. Gate sets keep their sets.

**Levels 6–7 weekly load.**
- The weekly order stays. R2's day 3 ↔ day 7 swap would still leave six elbow-flexor days in a row, because the muscle-up, planche, front-lever and one-arm days all load the elbow flexors. Instead, day 7 carries no strict pulling.
- Straddle and full back-lever days swap their pull sets for feet-elevated rows. In Level 7, the one-arm work moves from day 7 to day 6, one class earlier, so the path stays on days 3 and 6 and its gates hold.
- The day 6 advanced-tuck planche becomes a lean: one set in Level 7, and in Level 6 alongside the new wall HSPU negative.
- In Level 6 week 7, the five-skill circuit and the dragon class change places.

**Other fixes.**
- Every front-lever day keeps one 10 s advanced-tuck set.
- Level 7 day 5 trains the plain wall HSPU.
- The full-flag checks start at L7:71, and a tuck flag replaces the earlier checks.
- R2's set-cap breaches are fixed.
- The L-sit and full-lever gate texts are tightened.

**New validator rules** (`validate_progressions.py` 26–31):
- Every projected step is a library movement with a regression, or a prepared keep step within two reps of its ladder.
- No more than six training classes run without an easy one.
- Deloads are at most 42 classes apart.
- Levels 6–7 have no more than three heavy elbow-flexor or wrist days in a row, using the reviewers' definitions.
- No strict pulling sits beside straddle or full back levers.
- Every front-lever day has an advanced-tuck set, every Level 7 week has a wall HSPU, and the first full flag is a check at L7:71 or later.

**Calibration (same day).** A fourth reviewer asked whether the program is too easy for its audience. Their report is `review_calibration.md` in the same folder. The owner set two principles: progression runs from easiest to hardest, and someone who does every class meets each rung's gate when it starts. A new first-launch placement starts most users at Level 2 or 3, so Level 1 stays a gentle on-ramp, and C1/C2 were not applied.

A second pass applied the rest:
- *No falling back.* Along the 700 classes, no build dose falls more than 20% below that movement's best build dose so far (`validate_progressions.py` rule 32). Exceptions are deload weeks, recovery days, readiness checks, showcases and the L2:71 re-entry. The source blocks after Level 1 count, and benchmark sets form their own series. 385 later doses were raised; none were cut.
- *Basics and benchmarks (C3).* Level 4 pull-ups start at 10 per set, where Level 3 ends, and dips at 12. L4:83 tests 12/18/30 and L5:83 tests 15/20/35 (rule 33).
- *Muscle-up (C5).* 3 × 2 in every Level 4 class (rule 14's ceiling) and 3 × 3 from L5:5.
- *Pressing (C6).* Pike push-ups climb to 12, elevated pikes to 12, wall negatives to 3 × 5 by L5:45 and the wall HSPU to 3 × 5 in Level 6.
- *Handstand (C4).* Each rung starts once its gate is met: exit L3:36, toe pulls L3:71, kick-ups L4:1 and balance L4:36 (was L5:71). Each day keeps its practice set count, and recovery days keep no practice.
- *Recovery (C7, C8).* L3:89–91 return to build doses. Level 6 deloads at weeks 4, 8 and 13. The former deload weeks repeat the week before, one rep or second up.
- *Milestones (C9).* A first strict pull-up check at L1:90, four classes before the first pull-up sets. Eight checkpoint classes, whose basics become one recorded set above the best so far, keep no more than 45 classes between tests or checks (rule 34).
- *Minors (C10–C12).* L-sit, one-leg L-sit, pistol and flag-support doses rise.

## Applied in 2.4.0: three independent pro-athlete reviews, round 2 (25 October 2026)

Three reviewers checked, independently, whether every skill progression across the 700 classes is achievable for an ordinary adult: a street-workout statics coach, a gymnastics coach who teaches hand balancing, and a strength and conditioning coach who is also a physiotherapist. Each traced every rung, "Start when" gate and fallback with scripts. Their reports are in `docs/pro-reviews-2026-10-round2/`. They agreed on the main problems:
- The original-source Level 2 block asked for overhand and L-sit pull-ups, inverted hangs, a hanging L-sit and a high-bar pullover that no earlier class built, and the Level 1 tail restarted below where the Level 1 source block ended.
- Going into Level 3, pull-up reps, negative muscle-ups and a straight-body front-lever lowering jumped far past the Level 2 tail.
- End shapes ran ahead of their own gates: full-lever and straddle-planche readiness checks from Level 6, and the full flag nine times before its gate. The advanced-tuck back lever started although its 3 × 10 s tuck gate was never prescribed.
- The strict muscle-up came after six to nine straight-arm sets, and Levels 6–7 stacked three heavy pulling days in a row each week, with the one-arm rungs packed into a few weeks.
- Nothing taught the kick-up, the cartwheel exit vanished for 133 classes while balance attempts ramped up, and balance took five sets.
- Smaller items: fallbacks never practised before they were shown, missing "/side", duplicated steps, four or five RPE 8 days in some Level 5–6 weeks, spotter-only back-lever set-ups and a steep Level 7 dragon-flag ramp.

One deterministic pass applied the accepted fixes to the static session sources and logged every class before and after. The pass and its change log are archived with the ignored suite artifacts.

**Level 2 order.** The 30 original-source Level 2 workouts now run as classes 41–70, after the 40 reconstructed classes that were 31–70 and are now 1–40; classes 71–100 keep their numbers. `curriculum/catalog_metadata.json` (`app_slot_order`) maps each app position to its source slot, and source content, titles and legacy ids follow their slot. The block now follows pull-ups at 3 × 6, chest-to-bar pulls, knee raises, skin-the-cat and tuck L-sits. Source workouts on weekdays 4 and 7 still run the recovery routine, so 25 source workouts (9 in Level 2) now do and 65 keep their strength projection. Class 40 audits the basics before the block, and class 71 is an easy re-entry before class 72's recovery day.

**What changed.**
- *Level 1 tail.* Chin-ups reach 3 × 3, the first overhand pull-ups come at L1:94, low-bar pull-overs replace the plain transition, and skin-the-cat, hanging knee raises with a 3-second hold and tuck L-sits come several times. Floor push-ups (from L1:43) and parallel dips (from L1:50) replace incline push-ups and assisted dips, rising to 2 × 12 and 2 × 6.
- *Level 2 tail.* Negative muscle-ups and pull-ups build toward the Level 3 doses (3 × 8-9-10 pull-ups at L2:98). German hangs come before the back-lever work, tuck front-lever rows before the first tuck hold, and supported pistols before Level 3's single-leg squats.
- *Two source steps,* in the working projection only: the straight-body lowering at L3:15 and L3:19 becomes a 7-second tuck front-lever hold, and the behind-the-neck set at L3:1 and L3:8 becomes plain pull-ups. That variation is one set inside a pull-up exercise, so a set-level map (`early_set_overrides` in `morning_class.json`) joins the exercise-level one. L-sit pull-ups stay as written.
- *End shapes wait for their gates.* The first full-lever checks are L7:30 and L7:33, each after the 3 × 8 s straddle in the same class. Brief holds start at one set (L7:37, L7:40) and reach three sets at L7:58 and L7:61. The first straddle-planche check is L7:43, the first full-flag check L7:41 and the first trained full flags L7:71. Freed sets go to the straddles and the advanced-tuck planche.
- *Back lever.* Tuck holds start at 3 × 5 s (L3:29) and meet the 3 × 10 s gate twice (L3:67, L3:76) before the advanced tuck at L3:82. Tuck entries, tuck holds and one-leg holds set up feet-assisted on low rings or a low bar, with a spotter only if available.
- *Front lever and planche doses.* Tuck holds go 3 × 8 s (L3:22), then 3 × 10 s (L3:27, L3:34); advanced tucks reach 3 × 10 s at L4:89; the lean and tuck-planche gates are met twice. Advanced-tuck planche holds rise from 5 to 7 seconds through Level 6, and Level 7 builds stay at 10 seconds. In seven Level 4 weeks the day-2 planche dose moves to day 6, trading places with two push-up sets, so the two planche days are three days apart; the other four Level 4 weeks and all of Level 5 keep it, because day 6 trains wall handstand push-ups or has no two-set push-up step.
- *Muscle-up first.* In the 24 Level 4–5 muscle-up and back-lever classes the strict muscle-up and chest-to-bar pulls come first, then the German hang and the levers. L3:63 trains band muscle-ups, so the first strict attempt is the L3:65 readiness check.
- *Pulling load.* Level 7 swaps its day 1 and day 3 classes: one-arm work on day 7, then planche, front lever and muscle-up. Level 6 swaps days 2 and 3 instead (day 7, muscle-up, planche, front lever): a day-1 planche class would follow the straight-arm Level 5 finale and the L6:49 five-skill circuit as a third straight-arm day in a row, and moving the muscle-up off day 1 would leave seven-class stretches without one. Either way, no more than two heavy pulling days run together, and the practice timetable swaps with its classes so the flag stays on planche days. Typewriters run through L7:56, towel-assisted one-arm pulls start at L7:63 and one-arm negatives at L7:84.
- *Lower rungs in Level 7.* Every front-lever day keeps one one-leg front lever per side and every back-lever day one one-leg back lever per side, and tuck flags join the straddle and full flags, so a user two rungs behind still has work in the class.
- *Handstand.* Balance attempts drop from five sets to four. Cartwheel exits return through Level 5's last phase and Level 6; where a class is at its cap or would pass 55 minutes, the exit takes the wall-hold slot. A new movement, the kick-up to the wall, runs in Level 5 classes 36–70, before the first balance attempt at L5:71, with its own gate and side-view guide. Balance attempts now wait for controlled kick-ups, and freestanding handstand push-up negatives for a repeatable 5-second freestanding hold.
- *Fallbacks and data.* Dragon and pike-lift fallbacks point at rungs already practised, negative muscle-ups fall back to band muscle-ups from Level 3, hanging leg raises and sliding leg curls are refreshed in Level 5, every one-leg lever reads per side, and duplicated recovery steps become bird dogs or other mobility at the same dose.
- *Load.* Level 5–6 build weeks keep at most two RPE 8 classes, on the hardest skill content (24 classes drop to RPE 7), and Level 7 dragon flags stay at 3 × 4.

**Declined.** A new incline plyometric push-up (the Level 1 tail raises push-up volume instead); introduction deloads at the start of Levels 5–7; a lighter Level 6 day 7; removing the first archer pull-ups at L4:62; showing the library fallback on practice steps (the app already falls back to it); extra support holds in the Level 1 source block (the tail now covers dips). Original-source steps other than the two above stay as written.

**New first classes.**

| Rung | Before | Now |
|---|---|---|
| Floor push-up, parallel dip (reconstructed Level 1) | L1:71, L1:85 | L1:43, L1:50 |
| Overhand pull-up | L2:31 | L1:94 |
| Low-bar pull-over | L2:31 | L1:80 |
| Skin-the-cat, hanging knee raise | L1:83 | L1:69 |
| Original-source Level 2 block | L2:1 | L2:41 |
| Tuck front-lever row | L2:82 | L2:38 |
| German hang | L3:34 | L2:75 |
| Supported pistol squat | L4:16 | L2:81 |
| Strict muscle-up | L3:63 | L3:65 (readiness check) |
| Kick-up to the wall | new | L5:36 |
| Full front lever check, brief hold | L6:51, L7:37 | L7:30, L7:37 |
| Full back lever check, brief hold | L6:54, L7:40 | L7:33, L7:40 |
| Straddle planche check | L6:52 | L7:43 |
| Full flag check, trained hold | L6:76, L7:73 | L7:41, L7:71 |
| Towel-assisted one-arm pull-up | L7:42 | L7:63 |
| One-arm pull-up negative | L7:77 | L7:84 |

**Validators.**
- `validate_progressions.py`:
  - Start gates for the straddle planche, the advanced-tuck back lever and the kick-up.
  - A rule that readiness checks of the full levers, straddle planche and full flag wait for their gate (earlier in the class at the latest).
  - Two 3 × 10 s tuck sessions before the advanced-tuck back lever, and kick-ups before any balance attempt.
  - At most two RPE 8 classes in a Level 5–6 week outside the gate weeks.
  - Replaced: the October rule that put the back lever before the muscle-up. The strict muscle-up now comes before lever holds, and the German hang directly before the first back-lever hold.
  - Relaxed: Level 7 brief holds need one earlier readiness check instead of three.
- `validate_curriculum.ps1`: reads the Level 2 slot order and accepts two reviewed fallbacks (pull-up to eccentric pull-up, negative to band muscle-up).
- `validate_morning_class.py`: expects 25 recovery-routine source classes and treats the kick-up, like the exit, as reps per side.
- `validate_animations.ps1`: expects 151 movements.

These remain coaching judgments, not a validated protocol. An in-person coach review is still advised.

## Level 7 "Mastery" (classes 601–700, October 2026)

Level 7 continues the same fixed daily sequence after Level 6 and is part of the full program. It is authored and validated like Levels 4–6: static classes in `curriculum/l7.json`, written by one deterministic pass, with no choices inside a class. Every class stays small and daily: at most 20 sets including practice, 4 per exercise, and 55 minutes or less as listed, practice included. The source prescription already fits these limits, so the set-cap projection trims nothing.

**Weekly rhythm.** Day 1 planche and pressing, 2 front lever, 3 muscle-up, 4 recovery (RPE 2–3, no skill work), 5 back lever, 6 dragon flag and legs, 7 fundamentals with the one-arm pull-up and handstand push-up rungs. (Round 2 of the pro reviews swapped days 1 and 3, so the one-arm day is followed by the planche day instead of the muscle-up day.) Build weeks run RPE 7, 8, 7, 3, 8, 7, 6, so no more than two classes a week reach RPE 8 and never more than three RPE 7+ classes run together.

**Phases and loading.** Practice doses rise in three phases (classes 1–35, 36–70, 71–100). Weeks 4, 8 and 12 are deloads that keep at most 70% of the previous build week's main sets (about two thirds as written). Week 14 (classes 92–98) records a benchmark for each skill, class 99 is a five-skill mastery relay, and class 100 closes the program with an easy walk.

**Where each rung starts, and the gate that opens it.** A rung starts only after an earlier class has prescribed its gate dose; readiness checks never open a gate. Since round 2 of the pro reviews, readiness checks also wait for the gate (at the latest earlier in the same class), and the table shows the numbering after that round.

| Rung | First trained | Gate |
|---|---|---|
| Typewriter pull-up | L7:21 | archer pull-ups 3 × 5/side (L7:10) |
| Deficit wall handstand push-up | L7:35 | wall handstand push-ups 3 × 5 (L7:14) |
| Full front lever, 1–3 s holds | L7:37 | straddle 3 × 8 s (L7:30), after one readiness check (L7:30) |
| Full back lever, 1–3 s holds | L7:40 | straddle 3 × 8 s (L7:33), after one readiness check (L7:33) |
| Towel-assisted one-arm pull-up | L7:63 | typewriter pull-ups 3 × 3/side (L7:35, L7:42, L7:49) |
| Freestanding handstand push-up negative | L7:63 | deficit push-ups 3 × 3 (L7:42), 20-second balance practice and a repeatable 5-second freestanding hold |
| Full human flag, 2 s/side holds | L7:71 | straddle flag 3 × 5 s/side (L7:36), after readiness checks from L7:41 |
| One-arm pull-up negative | L7:84 | assisted one-arm pull-ups 3 × 3/side (L7:70) |

- **Levers.** Phase 1 raises the straddle front and back levers from 5 to 8 seconds, ending with the first full-lever readiness checks (L7:30, L7:33). From phase 2 the full levers are trained as brief holds: 1–3 seconds, at most three sets, always with the straddle in the same class as the fallback. One one-leg lever per side stays in every lever class. The front lever comes twice a week (day 2 and a second straddle exposure on day 5); German hangs open every back-lever class.
- **Human flag.** Practice stays twice a week on the planche and dragon days, which train no lever or muscle-up. The straddle flag rises from 3 × 4 to 3 × 5 seconds per side, with a full-flag readiness check each week in phase 2. Phase 3 trains one brief full flag per side with the straddle flag in the same class, and a tuck flag stays in the mix.
- **Handstand.** Balance attempts rise from 4 × 20 to 4 × 30 seconds; the cartwheel exit and the wall line stay in the weekly mix. Pressing runs wall handstand push-ups (3 × 4 to 3 × 5), then deficit push-ups, then freestanding negatives near a wall, on days 5 and 7, with elevated pike push-ups on the planche day.
- **One-arm pull-up groundwork.** Archer pull-ups, then typewriters, then towel-assisted one-arm pull-ups, then one-arm negatives, each new rung at least three weeks after the one below. The path runs twice a week: the newest rung on day 7 and an easier one on day 3.
- **Muscle-up.** Strict only, no kipping: doubles and triples, small density work (quick singles and clusters such as 2+2), and a level-end set of six.
- **Planche** stays a supporting goal: advanced-tuck holds rise from 5 to 10 seconds (12 in the benchmark; at most five planche hold sets a class), with a straddle-planche readiness check about once a month from L7:43.
- **Dragon flag:** full repetitions rise from doubles to sets of four, with slow full negatives.
- **Legs and trunk:** pistols and Bulgarian split squats run through the level, Nordic negatives come every week with a full Nordic readiness check about once a month, and toes-to-bar come every week.

**Validators.** `tools/validate_progressions.py` allows trained full levers only in Level 7 and only under the conditions above, adds eight start gates, keeps the 42-class gap before each full hold, and adds Level 7 rules for deloads, the benchmark week and showcase, the twice-weekly one-arm and pressing paths, rung spacing, the 55-minute limit and "Start when" text on every new rung. `validate_curriculum.ps1`, `validate_morning_class.py` and `validate_animations.ps1` now expect seven levels, 700 classes and 150 movements (151 since round 2 of the pro reviews added the kick-up to the wall).

**Five new movements**, each with a regression, a "Start when" gate and a side-view guide (the typewriter guide faces the viewer, since the slide is sideways): typewriter pull-up, towel-assisted one-arm pull-up, one-arm pull-up negative, deficit wall handstand push-up and freestanding handstand push-up negative.

These remain coaching judgments, not a validated protocol, and an in-person coach review is still advised.

## Applied in 2.4.0: two independent pro-athlete reviews (25 October 2026)

Two reviewers checked the schedule independently, with no access to earlier conclusions: a street-workout athlete and coach, and a former elite gymnast who coaches hand balancing. Each asked whether the rung-to-rung transitions and the combinations inside classes and weeks lead an ordinary adult to the promised skills. Their reports are in `docs/streetgym-pro-reviews-2026-10.md`. Both found the same main gaps:
- the human flag stopped at the tuck;
- rungs started before the library's own "Start when" doses had ever been prescribed;
- statics came only once a week from Level 4;
- shoulder-extension work disappeared just as the back lever got longer;
- Nordic curls, toes-to-bar and handstand push-ups faded out;
- there were strength cliffs around the source blocks;
- Level 3 had no deload.

One deterministic pass applied the accepted fixes to the static session sources. The pass and its change log are archived with the ignored suite artifacts.

**Rungs wait for their gates.** `tools/validate_progressions.py` now holds 16 start gates taken from the movement library, for example "three 10-second advanced-tuck holds" before the one-leg front lever. A rung may only start after an earlier class has prescribed that dose in the sets the class actually performs. The new first classes are:

| Rung | First class |
|---|---|
| One-leg front lever | L4:92 |
| Straddle front lever | L6:23 |
| One-leg back lever | L5:5 |
| Straddle back lever | L6:26 |
| Advanced-tuck planche | L5:17 |
| One-leg dragon negatives | L4:9 |
| Full dragon negatives | L5:2 |
| Dragon flag | L5:58 |
| Strict muscle-up | L3:63 |

The strict muscle-up waits until chest-to-bar pull-ups have reached 3 × 5. Before Level 5 it stays at six repetitions or fewer per class.

**Human flag past the tuck.**
- Straight-leg side planks run twice a week from Level 3 in place of the knee side plank.
- The straddle flag starts in the last phase of Level 6, with a single full-flag readiness check.
- Flag practice sits on the two weekdays of each phase that least often train levers or muscle-ups. The assembler leaves it out of any class that still does.

**Pulling tendons.**
- A feet-supported German hang comes before every back-lever hold.
- The back lever comes before the muscle-up in the same class, except on test and relay days. (Round 2 reversed this: the strict muscle-up now comes first, while fresh.)
- Two new Level 3 deload weeks (classes 43–49 and 85–91) cut sets to 60% at RPE 5.
- Existing deload classes keep 70% of their sets.
- Recovery classes hold no skill or advanced work.

**Frequent statics and basics.**
- From Level 4 the front lever and the planche come twice a week through small two-set doses, and the back lever comes weekly.
- Nordic negatives come every week from L4:58, with a full Nordic readiness check in Level 6.
- Toes-to-bar come every week from Level 5.
- Overhead pressing comes twice a week from Level 4, with wall handstand push-ups weekly in Level 6. They never share a class with planche holds.
- Straight-body hollow holds replace the tucked hollow from L3:26.
- Handstand cartwheel exits are practised before any balance attempt, and a weekly wall line is kept in Levels 5–6.

**Bridges into the source blocks.** The last 30 Level 1 classes now prescribe chin-ups, floor push-ups, dips, pike push-ups, tuck L-sits and skin-the-cat, so the Level 2 source workouts no longer arrive as a cliff. Level 2 pull-ups build toward Level 3. (Round 2 went further: the Level 2 source block now runs as classes 41–70.)

**Order and length.**
- A harder rung comes before an easier one, and planche holds before bent-arm pressing.
- Warm-ups hold only preparation.
- Listed minutes now cover the class as prescribed, practice included, and stay at 55 or less outside test days and the level-end showcase classes.
- The set-cap projection gives second sets to leg and hinge work first, so the steps listed last are no longer the ones always trimmed.

**Seven new movements:** the straight-leg side plank, hollow body hold, feet-supported German hang, handstand cartwheel exit, straddle human flag, full human flag and full Nordic curl. Each has a side-view guide.

**Skills.**
- Every skill now shows 0–100 skill points, and each group (Pull, Push, Legs, Core) shows the average score of the skills the program teaches in it. Finishing every class brings all four groups to 100. (Superseded on 27 October 2026 by ticked-set credit, tests and a weighted overall score: see `SKILLS_SCORE.md`.)
- Each skill carries a difficulty from 1 (Beginner) to 5 (Elite): a street-workout coach's grade of each rung (`SkillBook.DEFAULT_GRADES`), which never drops up a ladder. Novice is the first strict benchmark (pull-up, dip); Elite is kept for the one-arm pull-up negative, full front lever, straddle and full planche, freestanding handstand push-up negative and full flag. A rung that joins a ladder without a grade is estimated from the level that first teaches it, at least Intermediate for advanced movements and kept between the rungs around it. The level alone was a poor grade: the program never schedules the negative dip (it showed as Elite) and first schedules wall push-ups in Level 3 deloads (every push-up showed as Intermediate or harder).
- Ladders keep the coaching order the reviews asked for, rather than re-sorting by first appearance.
- Source workouts now earn credit for their main work, not only for their practice block.

These remain coaching judgments, not a validated protocol. An in-person coach review is still advised before selling the program as coaching.

## Coaching-progression revision (25 October 2026)

A coaching review asked whether the 600 daily classes lead to their own long-term skills. Three problems stood out. Skills advanced by class number while basic strength stopped rising after Level 3: average pull-up repetitions per set were 7.8, 6.0, 7.3 and 7.4 across Levels 3-6. Handstand and human flag, two main goals, received the smallest dose (one 8-15-second set about twice a week, unchanged for 100 classes), while planche, a supporting goal, received four to five times as many sets. And several rungs moved faster than tendons adapt: advanced-tuck to one-leg back lever took seven classes. The revision keeps the everyday small-class format, the RPE caps, the set ceilings, the deload weeks and one fixed shared sequence. This section supersedes the September practice timetable described below.

**Daily skill practice.** Handstand practice follows the warm-up in every training class, and human-flag practice comes twice a week. Each level has three phases (classes 1-35, 36-70 and 71-100). The dose rises by time or by the next rung, one variable at a time; Level 4, for example, moves from 3 x 30 s chest-to-wall holds to 3 x 40 s, then 2 x 45 s plus two toe pulls. Recovery classes skip practice. Practice sets count toward the same class ceiling, so the strength block shortens and class length stays about the same. Per 100 classes, handstand practice rises from 15-28 to 142-385 sets and flag practice from 21-29 to 42-87.

**Patient rungs.** Every lever, planche and dragon-flag rung first appears at least 42 classes (six weeks of daily classes) after the previous one. One-leg front lever now starts at class 278, advanced-tuck back lever at 282, one-leg back lever at 340 and one-leg dragon negatives at 269. Level 2 builds the back lever with skin-the-cat and tuck entries only, strict muscle-ups start in Level 3 (class 225), and candlestick rolls are rehearsed before the first dragon negative. Movement cards state when to start each rung, for example "Start when three 10-second advanced-tuck holds are clean." Full front lever, full back lever, straddle planche and full planche stay in the sequence as single readiness-check sets that fall back to the trained rung; they are not targets.

**Tendon load.** No three consecutive classes carry three or more straight-arm hold sets. Three back-lever and muscle-up classes swapped places within their week at the same RPE; where no swap was safe, one class's holds became rows or entries, starting with back-lever holds because they load the biceps tendon most. The first half of Level 3 caps straight-arm holds at six sets per class, and from Level 4 a class holds at most five planche sets.

**Strength that keeps rising.** An overhead-pressing ladder (pike push-up, elevated pike push-up, wall handstand push-up negative, wall handstand push-up) appears at least weekly from Level 3. Archer pull-ups and archer push-ups start in the second half of Level 4, on fundamentals days only. The L-sit ladder (tuck, one-leg, full) appears weekly from Level 3, and seated pike lifts replace dead bugs. Hinge and hamstring work (sliding leg curls, Nordic curl negatives, single-leg Romanian deadlifts) appears weekly from Level 2 and usually twice weekly from Level 3; Bulgarian split squats and pistols continue through Level 6. Additions respect the ten-advanced-set ceiling. Deload classes keep the easier overhead rung, and readiness checks, assessments and test weeks keep their authored content.

**Joint preparation.** Every training class now prepares the wrists; before, only about a third did. Prone Y-T-W raises appear twice a week, and squat-jump landing practice twice a week from Level 2. The Foundations source classes replace kipping pull-ups with slow negatives and the kipping swing with a hollow-arch swing, because kipping before strict strength is a common route to shoulder pain.

**Library.** Fourteen movements bring the library to 138, each with a side-view guide: archer pull-up, archer push-up, elevated pike push-up, wall handstand push-up negative, wall handstand push-up, one-leg L-sit, L-sit, seated pike leg lift, Bulgarian split squat, sliding leg curl, Nordic curl negative, single-leg Romanian deadlift, prone Y-T-W raise and squat jump. The pull-up guide no longer lifts the shoulders above the bar mid-pull.

The session JSON stays static. One deterministic authoring pass made these edits and rewrote 21 titles or descriptions whose named skill moved; the pass and its change log are archived with the ignored suite artifacts. `tools/validate_progressions.py` and `tools/validate_progression_motions.py` keep the rules from regressing. These remain coaching judgments, not a validated protocol. The app still has no placement test, so an experienced athlete also starts at Foundations, and an in-person coach review is still advised before selling the program as coaching.

## Calisthenics-first refinement (5 September 2026)

The shared program mixes calisthenics, supported skill practice and complementary flexibility, with rotating emphasis for variety. The 600-session sequence and its exercise choices remain intact. Handstand preparation is included from Level 1, developing through box pike, chest-to-wall work, toe releases and brief balance practice.

An audit found that some Foundations cards prescribed more work sets than later levels: Level 1 session 87 had 27 main/practice sets. The effective class now has 14. Level 6 session 2 retains its longer-lever work with 20 main sets instead of 23.

The explicit limits in `curriculum/class_progression.json` allow 14, 16, 18, 18, 20 and 20 main/practice sets across Levels 1-6, with three sets per authored exercise in Levels 1-2 and four later. Original-source strength projections retain their stricter three-set cap. These are conservative coaching choices, not scientifically established optimal numbers. They limit excess volume without making every day hard or requiring more sets just because a level advances.

The assembler distributes the available sets across the existing ordered exercises, retaining every movement and at least one set. It adds no work and preserves each retained set's repetitions, hold time, tempo, rest and regression; per-set dose ladders retain their original prefix. All 510 authored catalog sessions carry a separate `training_main` projection, and 177 have reduced sets in this revision. Their original `main` prescriptions remain available for audit. The app prioritizes `training_main`, then `training_content` for the original-source strength classes. Existing authored-data checks and additional effective-class checks cover both views.

Flexibility remains a complementary closing block rather than a competing training route. Existing stretch durations already rise through the levels and are preserved. One-sided stretch instructions now identify each timed set as one side, alternating sides between sets and alternating the starting side between sessions when necessary. This makes the dose clear without doubling session duration. Comfortable range progresses gradually; flexibility is not trained at the strength session's RPE target.

The side-plank preparation figure now stacks its supporting elbow beneath the shoulder. Additional sampled animation checks cover straight supporting arms, head clearance, box-foot contact, wall-toe contact and release during handstand practice. These checks and visual review assess the illustrations; they cannot certify an athlete's technique.

## Morning-class revision (September 2026)

The intended audience is recreational athletes building from fundamentals through intermediate strength, with the complete advanced skill ladders retained. Foundations effort is capped at RPE 6, Levels 2-4 at RPE 7, and later authored sessions at RPE 8. Planche, straddle and full-lever variations remain in the later progression as short practice ceilings, with easier regressions. The most advanced full-planche/full-lever exposures use at most three short sets; completing a level never requires mastery of those shapes. Early standalone muscle-ups use assistance, lever raises use tuck holds, and windshield-wiper work uses knee raises. Progress follows control and tolerance, with no deadline.


The app now implements the requested class-style daily practice, with no goal selection or deadline for any skill. Front lever, back lever, muscle-up, human flag and handstand are the main long-term skills; planche and dragon flag remain supporting goals. The seven levels specify practice opportunities and easier options, not readiness guarantees.

The source-preservation statements below describe the audit record. The effective app prescriptions now differ from the 90 original sessions: the original `content` is retained unchanged, while 66 strength sessions use a conservative `training_content` projection and 24 use an authored recovery routine on session days four and seven (65 and 25 since the round-2 reorder moved the Level 2 source block to classes 41-70). Strength and skill practice together now use the level ceilings above, with at most three sets per exercise, 12 repetitions or 30-second holds. Former max sets are capped at eight repetitions and all targets remain ceilings at RPE 7 or below. Set allocation retains every exercise before adding second and third sets. Rest is at least 60 seconds and is also offered between exercises. Existing authored sessions retain their original main prescriptions for audit and their recovery waves; the app uses the workload projection described above.

An explicit seven-session timetable adds one brief human-flag practice set on days one and five and one handstand practice set on days three and six. Existing RPE 4-or-lower recovery sessions receive no extra skill work. These are easy technique exposures at RPE 5 or below, with supported regressions; they are not extra maximal skill attempts. The timetable is written in JSON and mechanically expanded during assembly. This exception to the original per-session-only authoring format is intentional: the daily class remains deterministic, inspectable and shared by everyone.

Handstand practice moves from overhead alignment to supported box pike, chest-to-wall control, light toe releases and brief balance attempts. Human-flag practice moves from kneeling side plank to grounded split-grip support, foot-assisted tuck support and brief tuck practice. Long-leg/full flags are a later coaching progression, not a required endpoint of the 600-session catalog. A controlled exit is a prerequisite for inversion and unsupported work.

This is an evidence-informed coaching plan, not a scientifically validated 600-day protocol. General resistance-training evidence supports consistent, individualized loading; specific skill ladders and the timetable are coaching judgments. Daily recovery practice supports the habit without prescribing hard daily loading.

Additional coaching references: [GMB handstand progression](https://gmb.io/handstand/), [GMB wall-to-balance practice](https://gmb.io/freestanding-handstand/), and [School of Calisthenics on human-flag pushing/pulling preparation](https://www.schoolofcalisthenics.com/blog/home-gym-workout).

## What the APK actually contains

The supplied Base APK defines 379 workout slots across three levels, but complete exercise details exist for only 90: Level 1 workouts 1-40, Level 2 workouts 1-30, and Level 3 workouts 1-20. (In the app, Level 2's source workouts run as classes 41-70 since round 2 of the pro reviews; `catalog_metadata.json` keeps the slot map.) The remaining 289 records are entitlement placeholders. They contain identifiers and ordering metadata, but no hidden names, descriptions, exercises, sets, or videos.

The immutable extraction is `street_gymnastic_programs_levels.json`, protected by a fixed SHA-256 check. It retains the original English and Polish fields as forensic evidence. The bundled app catalog is an English-only projection: authentic English prescriptions are preserved while Polish fields are removed before packaging.

## Balanced 700-workout catalog

The app contains exactly 700 concrete workouts: 100 in each of seven levels.

- 90 `apk_authentic` workouts with details preserved from the Base APK
- 180 `reconstructed_placeholder` workouts newly authored for included empty Pro slots
- 30 `llm_extension` workouts filling Level 3 slots 71-100
- 400 `new_level` workouts across Levels 4-7

The original Level 1 and Level 2 contain 109 empty placeholder slots above the app's 100-workout-per-level boundary. They remain in the forensic export, and the corresponding authored research drafts remain auditable in the repository, but they are deliberately outside the balanced app sequence. No extracted record is destroyed or misrepresented.

Nothing labeled reconstructed, extended, or new is claimed to be recovered PRO content.

Every new workout is written as static JSON with explicit movements, sets, repetitions or hold times, rests, tempo, warm-up, and cooldown. Unilateral repetitions and one-sided hold durations say `/side` directly in the JSON; a plain 90/90 hip-switch repetition is explicitly defined as one alternating arrival. Skill and advanced movements include an explicit regression; end-of-chain basics without a lower rung are scaled through range, support, or dose. PowerShell scripts only merge and validate the authored records; they do not generate workout content. The app also has no runtime-random workout generator.

Release validation covers all 719 authored source records, including the 109 archived research drafts, as well as the 700-workout app catalog. It also requires skill fallbacks to be rehearsed earlier in the fixed sequence before they are first relied upon.

## One guided daily sequence

The Today screen assigns the first unfinished workout in catalog order. Future sessions can be read in the level browser, but they cannot be started early. Completing the assigned card advances the sequence.

The five goals develop in parallel rather than as separate user-selected paths. Validation uses rolling windows, not only stored week boundaries. Within each level's authored portion, every consecutive seven authored sessions include a basic pull-up or row, a basic push-up, a dip or support, legs, core, and at least one recovery-like day capped at RPE 4. From Level 3 onward, every such authored window also exposes muscle-up, front lever, back lever, dragon flag, and planche/elevated-plank work in parallel. Complete stored authored weeks are checked as well. Preparation and stretching remain daily requirements.

Each newly authored card prescribes specific variations. If a skill or advanced variation cannot be performed with clean control, its listed regression replaces it for the same assigned sets. Explicit authored regression labels are validated never to point to a harder descendant; unknown movements, self-references, and cyclic regression chains are also rejected. For an end-of-chain basic with no lower rung, range, support, or dose is reduced. Authentic APK workouts retain their extracted prescription and instead show prominent conservative scaling guidance. The rolling seven-session guarantees above apply only to authored content, not to those source-faithful authentic prescriptions. Readiness checks are considered together as feedback for the continuing sequence; they are not permission to abandon one goal for another.

## Seven-level progression

1. **Foundations** — clean pulling, pushing, squatting, hinging, trunk control, active hangs, support, wrists, shoulders, hips, and ankles.
2. **Bar Strength** — stronger pulls, pull-over control, high pulling, straight-bar support, dips, and assisted transitions.
3. **Skill Transition** — strict muscle-up development plus structured lever, dragon-flag, and planche entry work.
4. **Skill Control** — reliable tuck shapes, joint-tolerant straight-arm strength, and continued basics.
5. **Intermediate Strength** — longer lever shapes, cleaner transitions, stronger dragon-flag work, and deeper planche control.
6. **Confident Calisthenics** — successive advanced-practice waves, collective checks, deloads, and permanent fundamental maintenance.
7. **Mastery** — brief full front and back levers, the full human flag, freestanding handstand push-up negatives and one-arm pull-up groundwork, each with its easier rung in the same class.

Each level contains 100 sessions. Difficulty waves upward gradually, then falls through recovery and consolidation cards before another build.

## Skill ladders

- **Muscle-up:** active hang and rows -> pull-ups and dips -> high pulls and straight-bar support -> assisted transitions and negatives -> low-kip/strict practice -> clean repeatable singles and density work.
- **Front lever:** hollow body and scapular control -> tuck rows and holds -> advanced-tuck and asymmetric shapes -> longer-lever or straddle practice -> brief full-shape exposures with the listed regression.
- **Back lever:** support and shoulder preparation -> controlled skin-the-cat/German-hang range -> tuck holds -> advanced-tuck and asymmetric shapes -> longer-lever practice while shoulders remain comfortable.
- **Dragon flag:** posterior pelvic tilt and hollow holds -> reverse crunch and hip lift -> controlled negatives -> bent-knee holds and repetitions -> straighter-lever negatives and full-quality repetitions.
- **Planche/elevated plank:** incline plank and scapular push-up -> wrist-tolerant leans -> pseudo-planche push-ups and tuck support -> advanced-tuck and longer leans -> brief high-skill exposures with the listed regression.

These ladders are coaching judgments, not scientifically validated promises or deadlines.

## Daily variation without daily maximal loading

A typical seven-session wave rotates emphasis:

1. Pulling and front-lever preparation
2. Legs and dragon-flag/core work
3. Pushing and planche preparation
4. Active recovery and mobility
5. Muscle-up and back-lever preparation
6. Moderate mixed fundamentals
7. Easy aerobic activity, stretching, or low-fatigue technique

Titles, grips, stances, tempos, pauses, ordering, accessories, mobility focus, and session formats vary to reduce monotony. The basics deliberately recur. Recovery cards are real scheduled activity, not hard sessions with softer names.

This follows the practical direction of the 2026 ACSM position stand: consistency, regular training of major muscle groups, individualization, and useful bodyweight resistance. The review also reports that constant failure training and elaborate periodization are unnecessary for most healthy adults, so authored sessions preserve repetitions in reserve and use straightforward loading waves.

Sources:

- [ACSM 2026 resistance-training update](https://acsm.org/resistance-training-guidelines-update-2026/)
- [ACSM resistance-training position stand](https://pmc.ncbi.nlm.nih.gov/articles/PMC12965823/)
- [WHO physical-activity recommendations](https://www.who.int/initiatives/behealthy/physical-activity)

## Preparation and stretching

Every daily card starts with movement-specific preparation and ends with calm stretching or down-regulation. The 90 source workouts receive an authored warm-up and stretching overlay. Recovery days use longer shoulder, wrist, hip, and ankle mobility.

A gymnastics-specific systematic review examined dynamic and static warm-up strategies. A separate meta-analysis found that static stretching improves flexibility while showing no additional flexibility benefit beyond modest cumulative doses, supporting repeatable cooldown doses instead of marathon stretching sessions.

Sources:

- [Gymnastics warm-up systematic review and meta-analysis](https://doi.org/10.52082/jssm.2024.156)
- [Static-stretching dose systematic review and meta-analysis](https://pubmed.ncbi.nlm.nih.gov/39614059/)

## Load and readiness rules

- New sessions never exceed target RPE 8.
- Recovery, restore, and mobility days never exceed RPE 4.
- No authored sequence contains more than three consecutive RPE 7+ sessions.
- At RPE 8, main work is capped at 20 total sets and 10 advanced-category sets.
- Automatic between-set rest cannot consume more than 85% of the scheduled time.
- Skill work stops before shape, symmetry, speed, or joint position breaks down.
- Progression normally changes repetitions, hold time, assistance, or lever length one variable at a time.
- German-hang and inversion range is never forced.

The 90 authentic workouts remain a prescription-faithful English projection of the extracted data and therefore do not inherit the new-session caps. The app places a prominent warning on their overview and active screens: treat “max” as a technical stop, keep two or three repetitions in reserve, cap effort near RPE 8, reduce range, add assistance, scale sets/reps, and omit behind-the-neck, head-banger, or painful movements.

The app cannot assess apparatus stability, technique quality, injury history, or medical readiness. Its schedule is a training map, not a guarantee that a harder variation is appropriate.

## Injury-aware boundaries

Advanced calisthenics makes the wrist, elbow, and shoulder weight-bearing and exposes them to repeated loading. The catalog includes wrist/scapular preparation, regression chains, controlled range, and movement-specific safety notes. Use sound equipment and appropriate spotting. Stop for sharp pain, instability, numbness, dizziness, unusual symptoms, or loss of controlled range.

These safeguards are consistent with reviews describing upper-extremity and overuse injury patterns in gymnastics. They do not replace assessment by a qualified coach or clinician, particularly after injury or with a medical condition.

Sources:

- [Common upper-extremity gymnastics injuries and return-to-play protocols](https://pmc.ncbi.nlm.nih.gov/articles/PMC12088353/)
- [Systematic review of artistic-gymnastics injury prevention](https://pmc.ncbi.nlm.nih.gov/articles/PMC12652705/)

World Gymnastics' Code of Points was used only to cross-check recognized strength/hold terminology. Competitive element values are not used as recreational readiness standards or completion promises.

- [World Gymnastics Men's Artistic Code of Points 2025-2028](https://www.gymnastics.sport/publicdir/rules/files/en_1.1%20-%20MAG%20CoP%202025-2028.pdf)
