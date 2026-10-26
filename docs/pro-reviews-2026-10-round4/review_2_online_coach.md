# Street Gymnastics: final review by an online calisthenics coach

Reviewer lens: I coach everyday adults online, mostly office workers and app users, from the first strict push-up to their first skills. I read all 700 classes and the movement library. I counted with the scripts in `scratchpad\pro3\round4\r2\`: `load.py`, `a1_overview.py`, `a2_series.py` and `a3_patterns.py`, plus the inline checks quoted below.

---

## 1. Overall verdict: **ship after fixes**

**There are no blockers.** Four majors need fixing before the first real users arrive, because class ids and order freeze then. All four are dose and placement edits within the existing caps, plus one small app change. Each takes hours, not a rewrite.

**The owner's question: does it make sense, and can a normal person who tries do calisthenics with it?** Yes. Levels 1–3 are what I would hand a new client myself:
- a clean weekly split with real light days;
- foot-assisted and eccentric rungs everywhere;
- first chin-up singles around class 76 of Level 1;
- an honest muscle-up path from low-bar transitions through bands to the strict rep.

A consistent adult will be doing real calisthenics within 3 months and will have a band or strict muscle-up, a wall handstand and tuck levers within a year. The program gets less realistic in the second half:
- **Levels 4–5:** pull-up numbers sit about a notch above what a typical client has at days 300–500. They come after 8–9 sets of lever work and are labelled RPE 6–7.
- **Levels 6–7:** the front lever (and in Level 7 the back lever and handstand push-up) is prescribed two or more rungs beyond where most people will be. The one-step easier rung then still can't be done.
- **Levels 6–7:** the week drops to one light day.
- **Level 1 placement:** the common "20+ push-ups, 0–4 pull-ups" adult lands at Level 1, class 1, which is too easy for them.

Fix those four and the program delivers what the owner wants: hard enough, never unrealistic, and safe to follow blindly.

**Where a consistent client will plateau by day 700 (my estimate for this audience), and whether that's acceptable:**

| Skill | Most clients | Many clients | Few clients |
|---|---|---|---|
| Handstand | freestanding 10–30 s | | |
| Muscle-up | strict (men); band (many women) | | |
| Back lever | advanced tuck / one-leg | straddle | full |
| Front lever | advanced tuck | one-leg | straddle (full: under 10%) |
| Human flag | tuck | straddle | full |
| Planche | tuck | advanced tuck | |
| Dragon flag | yes (men) | | |

That is acceptable, because the program openly calls the later shapes ceilings ("Use regressions freely; there is no mastery deadline"). It holds only if the plateaued majority still trains productively in Levels 6–7, which is what M2 fixes.

---

## 2. Per starting point

| Start | Typical client | Verdict | One line |
|---|---|---|---|
| **L1, true beginner** | 5 push-ups, 0 pull-ups, 20 squats | **Fits** | Weeks 1–3 are well judged: hangs, supports, rows, foot-assisted pulls, half-range dips, squats 2x12. Sessions run about 25–30 min, with two walk days a week. Progress continues to chin-up singles by L1:076–083 and pull-ups 2x3 at L1:097. |
| **L1, strong push / weak pull** | 25 push-ups, 2 pull-ups, 40 squats | **Too easy (first 6 weeks)** | L1:001–021 has no push-ups at all (the first is L1:022, 2x6). Legs get only squats 2x12. This is the most common male download profile. See M4: start these clients at L1:041. |
| **L2** | 22 push-ups, 6 pull-ups, 40 squats | **Fits** | L2:001–021 has pull-ups 3x4, dips 3x6–7, push-ups 3x11–13 and squats 3x18–20. Pullovers and skin-the-cat are introduced, and each week has one RPE-5 technique day plus a recovery day. A client at exactly 5 pull-ups will use the foot-assisted rung on the third set, which is fine. |
| **L3** | 32 push-ups, 11 pull-ups, 55 squats | **Fits, demanding** | The original-source block L3:001–020 is dense: L3:001 has about 25 strict pull-ups, 3 band muscle-ups and 26 dips. Band muscle-ups and chest-to-wall handstand 2x30s start on day 1 with no inversion history, but the jump-muscle-up and box-pike rungs carry them. Sessions are long (see m3). |

**Thresholds:**
- **Keep the L2 rule** (push-ups 20+, pull-ups 5+, squats 30+). It matches where Level 1 ends: L1:097 has pull-ups 2x3 and chin-ups 3x4, and L1:099 has push-ups 2x12.
- **Keep the L3 rule** (push-ups 30+, pull-ups 10+, squats 50+). It matches where Level 2 ends: the L2:098 checkpoint has pull-ups 1x10, dips 1x11 and push-ups 1x16.
- **Add one entry point:** push-ups 20+ and squats 30+ but pull-ups 0–4 → start at **L1:041**, which already opens with its own "Foundation Re-entry Check" (M4).

---

## 3. Findings

### M1: Major. Pull-up targets in Levels 4–5 are a notch too high and mislabelled for effort

**Area:** easiest to hardest; achievable.

**Classes:**
- L4:001, 015, 029, 043, 057, 064, 071
- L5:001, 006, 015, 020, 029, 043, 057, 099

**Problem:**
- **Lever days.** Strict pull-ups jump from 3x8 on Level 3 lever days to 3x10 in Level 4 and 3x11–12 in Level 5. They always come *after* 6–9 sets of muscle-up, lever and row work. Some of these classes are labelled RPE 6.
- **What it takes.** A third set of 10 at RPE 7, after that pre-fatigue and with 90–125 s rest, needs a fresh max of roughly 15 or more. A consistent client at day 300–500 typically has 10–14 (men) or 3–8 (women).
- **What will happen.** Most will either grind to failure, against the RPE label, or drop the whole set to foot-assisted pull-ups, which throws away strict volume.
- **Dips are fine.** Level 3 already reached 2x12–3x14 (L3:030, 051, 064, 075), so Level 4's 3x12 is not a rise.

**Evidence:**
- **Level 3:** pull-ups 3x8 at RPE 7, after 6–10 lever, muscle-up or row sets (L3:035, 039, 042, 072, 077, 084, 091, 100). Checkpoints are 1x11 (L3:032) and 1x10 (L3:065).
- **L4:001 (RPE 6):** band_muscleup 2x2, tuck_front_hold 3x10s and tuck_front_row 3x7, then pullup 3x10 r120. The same pattern repeats at L4:015, L4:029 (3x10-10-10), L4:043 (r90), L4:057 and L4:071. L4:064 has 2x10 after 9 lever, row and chest-to-bar sets.
- **L5:001 (RPE 6):** opens fresh with pullup 3x11 r75, parallel_dip 3x12 r75 and archer_pushup 3x6/side. L5:020 has pullup 3x11 r75.
- **L5:015 (RPE 8):** one_leg_front 3x6s/side, advanced_tuck_front 3x12s and tuck_front_row 3x7, then pullup 3x11. The same at L5:029, L5:043 (r95) and L5:057. L5:099 has pullup 3x12.

**Fix (sets unchanged, so caps hold):**
- **Level 4 lever days:** pullup 3x10 → 3x8 at L4:001, 015, 029 and 043, and → 3x9 at L4:057 and 071. L4:064: 2x10 → 2x9.
- **Level 5 lever days:** pullup 3x11 → 3x9 at L5:006, 015 and 029, and → 3x10 at L5:043 and 057. L5:099: 3x12 → 3x10.
- **Fresh fundamentals days:** keep 2x10 (L4) and 2x11 (L5), but lengthen the rest:
  - L4:006, 020 and 034: r75 → r105.
  - L5:001 and 020: r75 → r120.
  - Relabel L5:001 as RPE 7.
- **App copy on pull-ups and dips:** "Up to N reps, stop with two in reserve. If a set comes up short, finish its remaining reps foot-assisted." The fallback stops being all-or-nothing.

### M2: Major. From Level 6, front-lever work (and in Level 7, back-lever and handstand-push-up work) outruns the typical client by two or more rungs, and the easier rung is only one step deep

**Area:** achievable; adherence.

**Classes:**
- L6 straddle front-lever days: L6:024, 031, 038, 045, 052, 059, 066, 073, 080, 094.
- L7 front-lever days: L7:002, 009, 016, 030, 037, 044, 058, 065, 072, 086.
- L7 back-lever days: L7:005, 012, 019, 033, 040, 047, 061, 068, 075, 089.
- L7 handstand-push-up days: L7:035, 042, 049, 063, 070, 077, 091.

**Problem:**
- **The schedule assumes fast progress.** It puts the one-leg front lever at day 392 (L4:092), the straddle at day 524 (L6:024) and the full lever at day 630 (L7:030). Most consistent adults are still at advanced tuck around day 500–700.
- **The fallback is one rung deep.** Each step shows exactly one easier rung: the app's easier chip shows a single regression name. So a straddle step falls back to a one-leg lever the client still can't hold.
- **The client's real rung barely appears.** It shows up as a single set at the end of the lever ladder.

**Evidence:** I simulated a client who stays at the advanced-tuck front lever:

| Level | Sets per week at their rung or with a fallback onto it | Sets per week where even the easier rung is too hard |
|---|---|---|
| L3–L5 | all, about 7–8 per week | 0% |
| L6 | 4.3 of 7.9 | 46% |
| L7 | 2.1 of 7.1 | 70% |

- **Example, L7:058:** full_front 3x2s (easier rung: straddle_front), straddle_front 2x10s (easier rung: one_leg_front), one_leg_front 1x8s/side, then advanced_tuck_front 1x10s. That is 1 productive set out of 7.
- **Same simulation on other skills in Level 7:**
  - a client staying at the one-leg back lever: 30% of back-lever sets unusable;
  - a client staying at wall handstand-push-up negatives: 34% of handstand-push-up sets unusable.
- **Not a problem elsewhere:** planche, flag, muscle-up and the handstand itself are fine (0–8%).

**Fix (net sets 0 or +1 per class; every class stays ≤ 20):**
- **L6 straddle days (listed above):** advanced_tuck_front 1 set → 2 sets, and straddle_front minus 1 set (4 → 3, 3 → 2).
- **L7 front-lever days (listed above):**
  - one_leg_front 1x8s/side → 2x8s/side;
  - advanced_tuck_front 1x10s → 2x10s;
  - straddle_front minus 1 set;
  - full_front no more than 2 sets (L7:058, 065, 072, 086: 3 → 2).
  - Example: L7:058 stays at 18 sets.
- **L7 back-lever days (listed above):**
  - drop the 1 straddle_front set and the wall_hspu sets (see m4);
  - one_leg_back 1 → 2 sets/side;
  - add advanced_tuck_back 1x10s.
- **L7 handstand-push-up days:** keep one wall_hspu set in every class as the floor rung. For example, at L7:035 deficit_wall_hspu 3x2 becomes 2x2 plus wall_hspu 1x4.
- **App (small):**
  - Let the easier chip step down again (an "easier again" tap follows `regression_id` once more).
  - Remember the rung last used per movement, so the next class opens on it. The SkillBook ladders already exist.

### M3: Major. Levels 6–7 lose the second light day: six straight RPE 6–8 days, with heavy elbow-flexor work on four of them

**Area:** sense of each week; burnout and injury.

**Classes:**
- L6 day-7 classes: L6:007, 014, 021, 035, 049, 063, 070, 077, 098.
- L7 day-7 classes: L7:007, 014, 021, 035, 042, 049, 063, 070, 077, 091.
- L7 day-6 classes: L7:006, 013, 020, 027, 034, 041, 048, 055, 062, 069, 076, 083, 090, 097.

**Problem:**
- **Light days drop to one.** Levels 1–5 give about 2 classes at RPE 4 or below per week (L4 and L5: exactly 2 every week). L6 gives 1.2 and L7 1.05.
- **Six hard days in a row.** Every L6 and L7 week runs day 5 → day 3 at RPE 6–8. For example, L7:005 is RPE 7, L7:006 RPE 7, L7:007 RPE 6, L7:008 RPE 7, L7:009 RPE 8 and L7:010 RPE 7, before the rest at L7:011.
- **The heavy-pulling days pile up in Level 7:**
  - day 2: straddle or full front lever plus pull-ups 3x10;
  - day 3: strict muscle-ups 4x4 plus archer or typewriter pull-ups 3x5/side;
  - day 5: straddle or full back lever plus German hang;
  - day 6: typewriter 3x3/side, assisted one-arm 3x2–3/side or one-arm negatives (L7:020, 034, 041, 048, 062, 069, 076, 090, 097).
- **The worst pairing.** Day 5's full back lever sits directly before day 6's one-arm work, which is a distal-biceps and medial-elbow stress. In year two, with jobs and an average age around 30–45, this is where my clients' elbows and adherence break.

**Fix:**
- **Lighten day 7 in Levels 6 and 7** (the classes listed above):
  - set RPE to 5;
  - keep the handstand-push-up variant first, at its current sets (it is the day's skill);
  - everything else at 2 sets or fewer: dips 2x10, one leg exercise, toes-to-bar 1 set;
  - drop the archer push-ups;
  - at most 9 main sets.
  - This restores two light days a week, as in Levels 4–5.
- **Move the one-arm pulling in Level 7.** Put the week's one-arm step (archer, typewriter, assisted one-arm or one-arm negative, now on day 6) onto day 3 in place of its archer or typewriter sets, with the same sets. Day-3 classes: L7:003, 010, 017, 031, 038, 045, 059, 066, 073, 087. On day 6, replace it with hanging_leg_raise 2x8.
- **Result:** heavy unilateral pulling happens once a week, and the back-lever day is followed by two pulling-free days.

### M4: Major. Placement sends the commonest adult profile to Level 1, class 1, where the first six weeks are too easy

**Area:** starting points.

**Classes:** L1:001–040; placement.

**Problem:**
- **Who lands there.** Under "weakest answer decides", anyone with fewer than 5 pull-ups goes to L1:001, even with 20–30 push-ups and 40+ squats. That is a very common profile for active men.
- **What they get.** In L1:001–021 their push and leg work is front-support raises 2x2–6, parallel-bar support 2x15–20s, half-range dips 2x4–6 and squats 2x12 (L1:001, 003, 005, 009, 013, 015, 017, 019). There are no push-ups until L1:022.
- **Why it matters.** The first two weeks are the highest-churn window for an app.

**Evidence:**
- L1:041 ("Foundation Re-entry Check", RPE 4) starts a full pull / legs / push / bar / mixed block.
- That block covers every pattern in L1:001–040: hangs, supports, rows, assisted and eccentric pulls, assisted and full dips, push-ups and knee raises.

**Fix:**
- **Placement rule:** if push-ups ≥ 20 and squats ≥ 30 but pull-ups are 0–4 → start at **L1:041**. Everything else is unchanged.
- **App:** allow a mid-level start for this one case.
- **Bonus:** these clients also skip the L1:040 → 041 seam (m1).

### Minors (5)

**m1: The L1:040 → 041 seam steps backwards for about five weeks, and the week counter restarts**
- **Evidence:**
  - Push-ups go from 2x12 at L1:036–038 to 2x6–8 at L1:043–061.
  - Dips go from 2x6–8 at L1:031–040 to parallel_dip 2x3 at L1:050 and 057, then eccentric_dip 2x3 from L1:059 to L1:080.
  - Weekly push-up reps fall from about 80 (L1:022–042) to 21–28 (L1:043–077).
  - Week labels restart at L1:041 ("w1d1") and L3:021 ("w1d1"), and jump from L2:070 ("w10d7") to L2:071 ("w6d6").
- **Fix:**
  - Cap the projection of L1:027–040 at push-ups 2x8 and dips 2x5, with anything above that on the easier rung. Those doses are above Level 1's RPE 6 for a 0–9 push-up client anyway.
  - Raise L1:043, 046, 050 and 053 push-ups to 2x8.
  - Number weeks continuously within each level.

**m2: Handstand cliff between L2 and L3**
- **Evidence:** wall_handstand is 1x10s for 30 straight classes (L2:071–100), then 2x30s from L3:001. That is six times the inverted time overnight.
- **Fix:**
  - L2:071–085: 1x15s.
  - L2:086–100: replace the pike_handstand_hold 1x20s set with a second wall set, giving 2x20s. This keeps sets equal (L2:081 and L2:095 are already at the 16 cap).
  - L3:001–014: 2x25s, then 2x30s from L3:015.

**m3: Session length**
- **Evidence:**
  - The L3 original-source classes rest r180–r240 after most exercises. L3:006, 010, 012, 015 and 020 come out at about 58–64 min, of which 37–43 min is rest.
  - Several stored "minutes" values exceed 55: L4:100 (61), L5:100 (72), L6:002 (57), L6:003 (60), L6:099 (64), and L6:074 (60, for a 15-set mobility day).
- **Fix:**
  - L3:001–020: rests above 150 s → r150.
  - Recompute the stored minutes from the content.
  - Trim L5:100 and L6:099 to 8 movements (drop the push-up, dip and squat singles).

**m4: Inverted pressing placed when fatigued**
- **Evidence:**
  - wall_hspu_negative 1x4 is the *last* step of the L6 dragon days (L6:006, 013, 020, 034, 041, 049, 062, 076, 083, 097).
  - wall_hspu 2x4–5 ends every L7 back-lever day (L7:005, 012, …, 096).
  - wall_hspu_negative 2x3 opens the L5:006 *pull* day, ahead of the front lever.
  - Lowering to the head is the one place I never want sloppy reps.
- **Fix (keeps handstand-push-up work twice a week):**
  - **L5:006:** delete the set. The L5 fundamentals days already open with it.
  - **L6 dragon days:** delete wall_hspu_negative. On the L6 planche days (L6:002, 009, 016, 030, 037, 044, 058, 065, 072, 079, 093), replace elevated_pike_pushup with wall_hspu_negative at the same sets, placed right after the planche steps.
  - **L7 back-lever days:** delete wall_hspu, which frees the sets used in M2. On the L7 planche days, replace elevated_pike_pushup 2x9–12 with wall_hspu 2x4, placed right after the planche steps.

**m5: Planche on back-to-back days in Levels 4–5**
- **Evidence:**
  - Every L5 week puts advanced or tuck planche 2x3–7s on day 2 (L5:002, 009, 016, 030, 037, 044, 058, 065, 072, 093), directly before the 15-set planche day on day 3. L5:003, for example, has tuck_planche 3x7s, pseudo_planche_pushup 4x7, dips 4x12 and elevated_pike_pushup 4x9.
  - L4 does the same at L4:037, 072, 086 and 093.
  - That puts straight-arm wrist and elbow load on consecutive days.
- **Fix:** on those day-2 classes, replace the planche step with lsit 2x10–12s (L5) or one_leg_lsit 2x8s/side (L4). Sets are unchanged.

---

## 4. Keep: what is already right

- **One path, with an easier rung on almost every step.** The gates are written as concrete standards ("three 10-second tuck holds", "ten strict pull-ups, five chest-to-bar and ten straight-bar dips"). The readiness copy says it outright: "a new workout number is not a skill-readiness test".
- **Placement by the weakest answer, capped at Level 3.** The L2 and L3 thresholds match where the previous level ends.
- **Level 1's reconstructed block.** Pull / legs / push / active recovery / bar / mixed / recovery is a textbook beginner week, with foot-assisted, eccentric and incline rungs, and chin-up singles before pull-up singles.
- **The muscle-up path:** low-bar transition (L1:045) → pullover → jump and negative → band → strict readiness check (L3:065) → strict sets. Each step has the right easier rung.
- **The handstand path teaches the exit before balance:** box pike → chest-to-wall → cartwheel exit (L3:036) → toe pulls → kick-ups → balance attempts. The handstand push-up ladder is gated by negatives. The daily handstand micro-dose is the right habit for app users.
- **Shoulder-extension prep for the back lever starts early and supported:** skin-the-cat from L1:069, feet-supported tuck entries and the German hang.
- **Loading structure:** a 3:1 build/deload rhythm in Levels 4–7, test weeks placed just before deloads, and test sets treated as readiness checks, not targets.
- **Caps are respected.** My count found 0 classes over the per-level set cap and 0 authored exercises over the per-exercise cap. Most classes run 30–50 min.
- **Legs and trunk are not neglected.** There are about 8–10 leg sets a week at every level: split squats, then pistols, Nordic negatives and single-leg RDLs. The dragon-flag and L-sit ladders are sound.
- **Level 6's message, "use regressions freely; there is no mastery deadline",** is exactly right for this audience.
