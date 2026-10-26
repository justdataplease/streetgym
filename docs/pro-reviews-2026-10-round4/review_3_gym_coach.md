# Street Gymnastics: final coach verdict (review 3, gymnastics and hand-balancing coach)

I read all 700 classes and the movement library in `classes_digest.txt`, and checked the raw JSON for the authentic sets. All counting was done by scripts in `round4\r3\`: per-class and per-week reps for each movement pattern, seconds for each skill family, spike detection against the previous 21 classes, durations, gates and session order. My duration estimate tracks the app's own `minutes` field within about 1 minute across 610 classes, so I used it for the authentic classes, where `minutes` is null.

---

## 1. Overall verdict

**Ship after fixes.** I found no blockers. There are five majors, all quick to fix: four are dose or placement edits in the data, and one is a short text and display change.

**Answer to the owner.** Yes, it makes sense, and a normal person who keeps showing up can do calisthenics with it. It is a real gymnastics-style ladder: hangs and supports, then the basics, then bar transitions, then tuck shapes, then one-leg and straddle shapes, then brief full shapes. Every step has an easier rung, the "Start when" gates match the schedule, deloads come every 28–35 classes, and the handstand is taught in the right order (wall holds, exit, toe pulls, kick-ups, balance). A consistent adult will get:

- strong pull-ups, dips, push-ups and pistols;
- a strict muscle-up (around Level 3–4);
- a freestanding handstand (Levels 5–7);
- a dragon flag;
- a back lever up to straddle or full.

Most will level off at the advanced-tuck or one-leg front lever, the tuck or advanced-tuck planche and the tuck or straddle flag. That is acceptable, but only if the class tells them clearly to stay on their own rung (F5).

What I would not ship as is:

- the first 6–14 weeks are too easy for the most common downloader, someone fit whose only weakness is pull-ups (F1, F2);
- there are three dip-volume spikes that are classic shoulder-injury setups (F3);
- the step into Level 4 and Level 5 pull-ups is one notch too steep for the RPE the classes ask for (F4).

**Short answers to the five questions**

1. **Starting points.** Level 1 fits true beginners but is too easy for fit adults who are only short on pull-ups. Level 2 fits. Level 3 fits once a few authentic doses are trimmed (see section 2).
2. **Easiest to hardest.** The rise is mostly steady.
   - Problems: one real drop at the L1:041 seam (F2), dip spikes in L3 week 2, at L3:036 and at L3:064 (F3), and an over-steep first week in L4 and in L5 (F4).
   - Handstand ladder: the pace (exit L3:036, toe pull L3:071, kick-up L4:001, balance L4:036) is right for wrists and for a consistent athlete. It has two thin spots (m2).
3. **Achievable.** Yes for the basics, the muscle-up, the handstand, the back lever and the dragon flag. The full front lever, full flag and planche are ceilings for most people, which is fine with F5.
4. **Sense.** Sessions are well built: practice skill first, then strength, then accessories and core. Every wrist-heavy class has wrist prep in its warm-up (0 misses).
   - Weeks: L1–L5 run 5 loaded days with 2 easy days. L6–L7 run 6 loaded days; that is dense but acceptable at that stage.
   - One ordering fault: handstand push-ups come last after levers or nordics (m3).
5. **Challenge without injury.** It is challenging enough from Level 2 on, and safe overall. The remaining injury risk sits almost entirely in F3, F4 and m1.

---

## 2. Per starting point (first 2–3 weeks)

| Start | Verdict | One line |
|---|---|---|
| **Level 1** | **Fits the true beginner; too easy for the pull-limited fit adult** | A deconditioned adult (5 push-ups, 0 pull-ups) gets a safe on-ramp: L1:001–021 is 2-set hangs and supports, inverted rows 2x10 from L1:009, foot-assisted pull-ups 2x5 and half-range dips 2x4 from L1:012, about 25 min a day with 2 walk days. But anyone with 20+ push-ups and 30+ squats who has fewer than 5 pull-ups (most women and many men) gets at most 24 push-up reps and 2x12 squats per class for 100 classes. |
| **Level 2** | **Fits** | 22 push-ups / 6 pull-ups / 40 squats: L2:001 pull-ups 3x4 r120 is about 65% of max, L2:002 dips 3x6 with the assisted rung, L2:005 is the first RPE 7 day, chest-to-bar starts at L2:022. Weeks 1–2 have 6 loaded days with pulling on 4 of them; volumes are small, so it is fine. |
| **Level 3** | **Fits after the F3 and m1 trims; as is, weeks 2–3 are slightly too hard** | 35 push-ups / 11 pull-ups / 60 squats: band and negative muscle-ups from L3:001 (the pullover rung is listed). The problems: 123 dips in week 2, with 16 reps at 5-second bottom holds in L3:013, after a placement that never asks about dips; pull-ups 10 then 9 with 60 s rest at L3:016; 42 pull reps at L3:020; six classes of 57–67 min; wall handstand 2x30s from day 1 with no inversion history. |

---

## 3. Findings

### F1 — Major — Placement and audience fit — L1:001–040 (and L1:041–100)
- **Problem:** "The weakest answer decides" sends every adult with fewer than 5 pull-ups to L1:001, whatever their push-ups and squats. That is the largest group of downloaders. For anyone already somewhat active, Level 1 is a warm-up for 10–14 weeks, which works against the owner's "not too easy" goal and against retention.
- **Evidence:**
  - L1:001–021 is 2x20s hangs, front-support raises 2x4, bar support 2x15s and squats 2x12 at about 25 min.
  - The largest push dose anywhere in Level 1 is push-ups 2x12 (L1:027, 029, 036–038). Squats never exceed 3x14 (L1:070).
  - Days 4 and 7 of every week are 10-minute walks.
- **Fix:** add one placement rule. If push-ups are 20+ and squats 30+ but pull-ups are 0–4, start at **L1:041**. That class is already an entry-style "post_legacy_reentry_assessment", and the assisted pull-up and row ladder from there (assisted pull-ups 3x4–6, eccentrics from L1:076, chin-ups 3x3–4, pull-ups 2x3 at L1:097) is exactly what this person needs. Together with F2, L1:041–100 becomes a proper climb. No dose or cap change.

### F2 — Major — Easiest-to-hardest (drop outside a deload) — L1:027–040 → L1:041–098
- **Problem:** at the seam between the authentic block and the reconstructed block, push-ups and dips fall back and take 8 weeks to recover. These are regular RPE 6 days, not a deload. The authentic block also reaches full-range dips too early for a true beginner.
- **Evidence:**
  - **Push-ups:** 2x12 at L1:036–038, then 2x6 at L1:043/046, 2x7 at L1:050/053, 2x8 at L1:057, 2x7 at L1:071. They only return to 2x12 at L1:099.
  - **Dips:** half range until L1:026, then full range 2x4–2x8 from L1:027 (2x8 at L1:040). After the seam: assisted dips 2x5 at L1:043, parallel dips 2x3 at L1:050/057, eccentric 2x3 at L1:059–080, 2x5 at L1:085. They only return to 3x6 at L1:099.
- **Fix (sets unchanged):**
  - L1:027, 029–031, 034, 036–037, 040: keep the authentic "Half range: elbows to a right angle" note on every Parallel-Bar Dip set and cap them at 2x6 (L1:040 goes from 2x8 to 2x6).
  - L1:043 and L1:046: pushup 2x6 → 2x8.
  - L1:050 and L1:053: 2x7 → 2x8.
  - L1:057: 2x8 → 2x9.
  - L1:060: 1x8 → 1x10.
  - L1:071: 2x7 → 2x9.
  - L1:074: 1x7 → 1x10.
  - L1:092 and L1:095 (deload): 2x5 and 2x6 → 2x7.
  - L1:050 and L1:057: parallel_dip 2x3 → 2x5 (half range).
  - L1:085 and L1:087: parallel_dip 2x5 → 2x6.

### F3 — Major — Shoulder and elbow load (dip spikes) — L3:008, L3:009, L3:013, L3:036, L3:064
- **Problem:** dips are the movement most likely to give recreational adults anterior-shoulder and sternum trouble, and the placement never asks about them. Three spots more than double the recent dip volume in a single class or week.
- **Evidence:**
  - **L3 week 2 has 123 dips**, against 39 in L3 week 1 and a peak of 94 in an L2 week:
    - L3:008 has 34 dips, including 1x10 bar dips with 5-second low and top holds;
    - L3:009 has 20 slow dips;
    - L3:013 has 44 dips (bar dips 10, then parallel dips 8 with 5-s low and top holds, 10, 8 with holds, then another 1x8).
  - **L3:036:** straight-bar dip 3x10 plus parallel dip 3x12 is **66 dips**, plus pause push-ups 2x15. The largest dip class in the previous 21 was 28 (L3:019).
  - **L3:064:** parallel dip 3x14 plus straight-bar dip 2x11 is **64 dips**, plus pseudo-planche push-ups 3x7. The previous 21-class maximum was 26 (L3:051). That week totals 121 dips, and L3:065 adds straight-bar dips 2x9 the next day.
- **Fix:**
  - L3:008: delete the standalone "Bar Dips / 5 sec low + 5 sec extension" 1x10 set (34 → 24).
  - L3:009: slow bar dips 10/12 push-ups/10 → **8**/12/**8**.
  - L3:013: change the 5-second holds to 2 seconds and delete the extra "Dips / On Parallel Bars 1x8" (44 → 36).
  - L3:036: straight_bar_dip **3x8**, parallel_dip **1x12**, pause_pushup 2x12 (66 → 36).
  - L3:064: parallel_dip **2x12**, straight_bar_dip **2x8** (64 → 40).
  - Week 2 then totals about 100 dips and week 10 about 97, both in line with L2 and L4 weeks. All of these reduce sets, so caps stay safe.

### F4 — Major — L4–L5 rise (pull-ups at 10 and 11 per set) — L4:001–035, L5:001–035, L5:003
- **Problem:** the rise is right in direction but starts one notch too high. Rests are too short for 10+ rep sets, and the RPE labels contradict the program's own checkpoints. Repeated near-failure pulling next to muscle-ups and levers is how elbows (medial elbow and biceps tendon) get irritated.
- **Evidence:**
  - L3:035–100 trains pull-ups 3x8 at r90–120. The readiness set at L3:065 is 1x10 and the L4 checkpoint at L4:083 is 1x12, so during Level 4 the program itself expects roughly 10–12 reps at RPE 7 for a single set.
  - Against that, L4:001 asks 3x10 r120 labelled **RPE 6**, with band muscle-ups, tuck front-lever holds, tuck front-lever rows 3x7 and a feet-elevated row set in the same class. L4:006, L4:020 and L4:034 ask 2x10 at **r75**.
  - L5:001 asks 3x11 at r75 (RPE 6) plus dips 3x12 at r75.
  - L5:003 is the first 4-set push day: dips 4x12, pseudo-planche push-ups 4x7, elevated pike push-ups 4x9, tuck planche 3x7s. It gives L5 week 1 **102 dips**, against 54–90 in non-deload L4 weeks.
- **Fix (reps and rests only, sets unchanged or fewer):**
  - Pull-ups ×10 → **×8** with r105–120 in L4:001, 006, 013, 015, 020, 029 and 034 (r75 → r105).
  - ×10 → **×9** in L4:043, 057, 064 and 069 (L4:043 r90 → r105).
  - Keep 3x10 from L4:071 and the 1x12 checkpoint at L4:083.
  - L5:001, 006, 015, 020 and 029: 3x11 → **3x10**, with r75 → r120 for pull-ups and r105 for dips. L5:001 dips 3x12 → **2x12**. Keep ×11 from L5:036.
  - L5:003: dips, pseudo-planche push-ups and elevated pike push-ups go back to **3 sets** (3x12 / 3x7 / 3x9). The first 4-set version stays at L5:031.
  - Relabel L4:001 as RPE 7.
  - The result: a 3x8 → 3x9 → 3x10 → 3x11 staircase that matches the checkpoints.

### F5 — Major — Achievability and safety at the plateau (one easier rung only) — L4–L7, in 50 / 72 / 83 / 85 of 100 classes
- **Problem:** the class shows only one easier rung. `CurriculumJsonParser.movementDescription` appends a single "Regression: X". From Level 4 on, that rung is very often itself a gated skill the athlete does not have yet. The rule "use the easier rung for the same sets" then makes them do every set of a shape they can't hold cleanly, usually with an arched lower back and bent arms in levers and planche, or they drop out. This is exactly where most consistent people will level off.
- **Evidence:**
  - My script counted classes containing at least one gated step whose shown easier rung is also gated: **L4 50, L5 72, L6 83, L7 85** out of 100 each.
  - Examples (the shown rung's own gate in brackets): L6:024 straddle_front 3x3s ← one_leg_front [3x10s advanced tuck]; L7:058 full_front 3x2s ← straddle_front [3x8s one-leg per side]; L7:001 straddle_flag 3x4s ← tuck_flag [3x8s assisted tuck flag]; L7:043 straddle_planche ← advanced_tuck_planche [3x8s tuck planche].
- **Fix (no dose change):**
  1. In the step description for skill, advanced_skill and advanced_strength categories, show the chain up to three rungs down the library's `regression_id`, for example "Easier: Straddle → One-Leg → Advanced-Tuck Front Lever".
  2. Add one sentence to the readiness text: "If the easier option isn't clean either, keep stepping down its own easier option until it is, and do all the listed sets there. From Level 6 on, many steps are ceilings; training every set at your real rung is the plan, not a failure."

### m1 — Minor — Session length and near-max sets in the authentic Level 3 block — L3:006, 010, 012, 015, 016, 019, 020
- **Problem and evidence:** six authentic classes run past the 55-minute rule, because the r180–240 rests have no `minutes` value to check against. Estimates: L3:006 about 60, L3:010 about 67, L3:012 about 60, L3:015 about 62, L3:019 about 57, L3:020 about 66 min. Three classes ask near-max pulling of someone placed with 10+ pull-ups:
  - L3:016 "Pull Up" 10 then 9 at r60;
  - L3:020 has 42 pull reps, including "Pull Up / isometric 90°" 2x8 at r240 and "Half Around The World" 1x6;
  - L3:013 ends on 1x10.

  The level-end integration days L4:100 (61), L5:100 (72) and L6:099 (64) also run past 55 if they don't count as test days.
- **Fix:**
  - Rests on band muscle-up, negative muscle-up, tuck front-lever and tuck back-entry sets go from r240 to **r150** and from r180 to **r120** in L3:006, 010, 012, 015 and 019.
  - L3:010: also delete the second pullover/straight-bar-dip compound (3 sets).
  - L3:016: pull-ups 10, 9 r60 → **8, 7 r120**.
  - L3:020: isometric pull-ups 2x8 r240 → **2x5 r120**, half around the world 1x6 → **1x4 r120**, squat rest r240 → r60.
  - L3:013: final pull-up set 1x10 → **1x6**.
  - L4:100, L5:100 and L6:099: drop the push-up, dip and squat tail and cap skill rests at r120.
  - This brings all of them to 55 min or less.

### m2 — Minor — Two thin spots in the handstand ladder — L2:071–100 → L3:001–035; L4:001–070
- **Problem and evidence:**
  - Wall-handstand time triples overnight: L2:071–100 is wall_handstand 1x10s (after box-pike 1x20s), then L3:001–035 is 2x30s from the first class. L3 entrants start there with no inversion history at all.
  - Before balance attempts at L4:036 (whose gate requires "kick-ups to the wall are controlled"), there are only **five** kick-up sessions of 1x3 per side (L4:001, 008, 015, 022, 029). Then there are none until L5:036.
- **Fix:**
  - L2:086–100: wall_handstand 1x10s → 1x15s.
  - L3:001–020: wall_handstand 2x30s → **2x20s**, then 2x30s from L3:021.
  - Kick-ups: in L4:005, 012, 019, 026 and 033, replace wall_handstand 1x45s with wall_kickup 1x3/side. In L4:036, 043, 050, 057 and 064, replace handstand_toe_pull 2x10s with toe_pull 1x10s plus wall_kickup 1x3/side.
  - Set counts stay the same, which matters because these classes sit at the 18-set cap.

### m3 — Minor — Session order and weekly wrist load (handstand push-ups placed last) — L6:006, 013, 020, 027, 034, 041, 049, 076, 083, 090, 097; L7:005, 012, 019, 026, 033, 040, 047, 054, 061, 068, 075, 082, 089, 096
- **Problem and evidence:** wall_hspu_negative is the **last** step on every L6 dragon day, after nordics and dragon flags. wall_hspu (1x2 up to 2x5, 1x6 on the L7:096 benchmark) is the **last** step on every L7 back-lever day, after the German hang, full and straddle back levers, the straddle front lever and rows 3x10–12. Inverted pressing with the head over a mat should never be done tired. These L7 days also load the wrists for a sixth day in the week.
- **Fix (same doses):**
  - L6 dragon days: move wall_hspu_negative to the **first** MAIN slot.
  - L7 back-lever days: delete wall_hspu. On the non-deload L7 planche days (L7:001, 008, 015, 029, 036, 043, 057, 064, 071, 085), replace elevated_pike_pushup 2x9–12 with **wall_hspu 2x4** in the same slot, before the dips (same set count).
  - Handstand push-ups then stay at twice a week (d1 and d7) and never close a session.

### m4 — Minor — Missing gate on the negative muscle-up — first at L2:073; L2:076–097 and L3:001–013
- **Problem and evidence:** the negative muscle-up loads the shoulder through deep extension and the transition under bodyweight. It has **no "Start when"** gate, while the band and strict versions are handled carefully. It appears at L2:073 (2x1), then reaches 3x2 at L2:080 and 3x3 at L2:094 and L2:097. At that point a Level 1 graduate typically has 5–8 pull-ups.
- **Fix:** add to the negative_muscleup safety text: "Start when eight strict pull-ups and eight straight-bar dips are controlled; until then do the foot-assisted low-bar pullover for the same sets." L3 entrants (10+ pull-ups) pass it on day 1.

### m5 — Minor — Test-set wording invites grinding — L2:084, L2:098, L3:032, L3:068, L4:083, L5:083, L6:042, L6:078, L7:094
- **Problem and evidence:** checkpoint sets read like targets, with the assisted rung as the fallback. Examples: pullup 1x15 r210 ← assisted_pullup (L5:083); parallel_dip 1x21 (L6:042); pushup 1x35 (L5:083). Someone at 12 reps either grinds to failure or does 15 assisted reps, and either way the result is lost.
- **Fix:** on "assessment" steps, use this text: "Clean reps up to N; stop at the first form break and note the number. Don't top up with the easier option." No dose change.

---

## 4. Keep (already right)

- **Placement and path:**
  - One fixed path with an easier rung on every step.
  - Placement capped at Level 3, plus a "start from scratch" option.
  - Repeating a day and going back a level are both available.
- **Gates:** they are consistent with the schedule. I checked the toe pull, kick-up, balance, wall/deficit/freestanding handstand push-up, archer → typewriter → assisted one-arm → one-arm negative, pistol, lean → tuck → advanced-tuck planche, tuck → advanced → one-leg → straddle → full levers, assisted → tuck → straddle → full flag, the dragon-flag chain, the L-sit chain and the strict muscle-up. Every one is reachable before its first scheduled appearance.
- **Handstand ladder:** overhead line → box pike → chest-to-wall → cartwheel exit (L3:036) → toe pull (L3:071) → kick-up (L4:001) → balance (L4:036), in steps of about 35 classes, with the exit taught before any balance attempt and "practice window" wording. This is the right order and the right pace for wrists. It is also the most achievable showpiece skill in the app.
- **Load management:**
  - A deload every 28–35 classes.
  - Two easy days a week in L1–L5, with the weekly recovery day free of practice.
  - Level-end checkpoints that are low-volume test days.
- **Session structure:**
  - Skill practice right after the warm-up, then the hardest strength work, then accessories, then core and legs.
  - Every wrist-heavy class has wrist prep in its warm-up, and many classes end with a forearm stretch.
  - Back-lever work is always feet-supported (German hang, entries, one-leg), and every nordic step has its own easier rung.
- **Skill spacing:** planche work is spread over 2–3 days a week from leans and frog stands, levers are trained twice a week, and flag practice runs about twice a week (24–28 classes per level). That is enough to progress without overloading the elbows.
- **Level 7 design:** full levers, the full flag and the straddle planche start as readiness checks, then become brief holds with the straddle and one-leg rungs in the same class. That is the right way to train mastery shapes for adults.
- **Session caps** are respected in all 700 classes. Most sessions run 35–55 minutes; the exceptions are covered in m1.
