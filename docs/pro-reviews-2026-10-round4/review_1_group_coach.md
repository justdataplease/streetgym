# Street Gymnastics: final verdict from a group-class coach

Reviewer: pro calisthenics athlete who runs group classes for working adults.
Basis: every line of `classes_digest.txt` plus counts from `curriculum_v2.json`. The scripts are in `round4/r1/` (`load.py`, `summary.py`, `weeks.py`, `chains.py`, `fund.py`, `seam.py`, `weekly.py`; per-class table `per_class.tsv`).

## 1. Overall verdict: SHIP AFTER FIXES

**Does everything make sense, and can a normal person who tries do calisthenics with it?**
Yes. A consistent adult who starts here will build real basics in the first year. Within about 1.5 years most will have a strict muscle-up, a freestanding handstand, a straddle back lever and at least an advanced-tuck or one-leg front lever. The design is right:
- one path;
- concrete "Start when" gates;
- a daily skill dose;
- regular deloads;
- the easier rung kept in the same class;
- nothing reckless (supported German hang, cartwheel exit before balance work, HSPU negatives before HSPU, and no full planche prescribed).

What stops me shipping it as is are the seams between blocks, not the design:
1. The typical Level 1 entrant is somewhat active but has fewer than 5 pull-ups, and gets 3 weeks without a single push-up.
2. At L1:041 the program gets easier for a month.
3. The first L3 weeks and the opening of L4 and L5 ask for pull-up sets 3–5 reps beyond what the level's own checkpoints imply. The fallback, a foot-assisted pull-up, is far too big a step down.
4. L6–L7 drop from two easy days a week to one exactly when the elbow-heavy skills arrive.

All the fixes stay within the set caps and need no new movements. I found nothing that blocks the release. Every class respects the set cap and the sets-per-exercise cap, deloads come every 22–36 classes, and only level-end, test, baseline or recovery days run over 55 minutes (by the app's own estimate).

## 2. Per starting point

| Start | Verdict | One line |
|---|---|---|
| **L1** (0–4 pull-ups or <20 push-ups or <30 squats) | **Fits a true beginner. Too easy for the typical L1 entrant** | Someone with 10–25 push-ups gets no push-up until L1:022 (M1). After that it progresses well, to chin-ups 3x4 and pull-ups 2x3 by L1:097, apart from the L1:041 dip (M2). |
| **L2** (5–9 / 20–29 / 30–49) | **Fits** | Pull-ups 3x4 r120, push-ups 3x12–13, dips 3x6 and squats 2–3x18–20 sit at RPE 6–7. Chest-to-bar 3x2 (L2:022) and paused dips keep it moving in weeks 3–4. |
| **L3** (10+ / 30+ / 50+) | **Fits L3:001–012. Too hard in weeks 2–3 at the threshold** | L3:013/015/016/020 ask for 10 pull-ups at the end of a class, 10 then 9 at 60 s rest, and about 42 pull-ups in one class. That fits someone with 13–15 pull-ups, not 10 (M3). |

## 3. Findings (majors, then 5 minors; no blockers)

### M1: Major. Placement and the L1 start (L1:001–021)
**Problem.** Everyone under 5 pull-ups lands on L1:001, and that is most app users. Most of them are "somewhat active", with 10–25 push-ups and 30–50 squats. For that person the first 3 weeks sit around RPE 3–4. That is where people quit.

**Evidence.**
- L1:001 is two 20 s hangs, front-support raises 2x4, a support hold 2x15s and squats 2x12.
- L1:009–021 add inverted rows 2x10, foot-assisted pull-ups 2x5–8 and half-range dips 2x4–6.
- There is no push-up until L1:022 (2x6), and two of every seven days are 10-minute walks.

**Fix.** Add a second entry point on the same path, with no content change:
- A Level 1 result with push-ups ≥10 and squats ≥30 (pull-ups 0–4) starts at **L1:022**.
- Everyone else starts at L1:001.

L1:022–040 (negatives 2x2–6, push-ups 2x6–12, dips with a rung) is the right first month for that person.

### M2: Major. A difficulty drop at the L1 seam (L1:027–040 → L1:041–075)
**Problem.** The original-source block front-loads pushing and pulling. The reconstructed block after it restarts lower. From L1:041 to L1:068 (4 weeks plus a deload) the athlete pushes and pulls less than at L1:036–040, and pull-up negatives disappear for 35 classes.

**Evidence.**

| Movement | L1:022–040 (original source) | L1:041–075 (reconstructed) |
|---|---|---|
| Push-ups | 2x12 on L1:027/029/036/037/038 | 2x6–8 on L1:043/046/050/053/057; 1x8 on L1:060; 2x7 on L1:071 |
| Dips | Full parallel dips 2x6–8 on L1:031–040 (L1:040 is 2x8) | Assisted 2x5–6 on L1:043/045/052; parallel 2x3 on L1:050/057 |
| Pull-up negatives | 2x2–6 | None until L1:076 (2x3) |
| Weekly push-up reps | About 84 (L1:036–040) | About 36 (L1:043–049) |

**Fix.** Make all three lines rise steadily, within the 14-set cap:
- **(a)** Show the source "Parallel-Bar Dip" in L1:012–040 as `assisted_dip`, with the same sets and reps. A 0–9 push-up beginner should not be doing 2x8 full dips on day 40. The reconstructed line (assisted → negative → parallel 2x5 at L1:085 → 3x6 at L1:099) then takes over cleanly.
- **(b)** Push-ups go to 2x8 on L1:043/046/050/053 and 2x10 on L1:057/060/071/074. The incline rung stays the same.
- **(c)** On L1:045/052/059, replace `assisted_pullup 3x4–6` with `eccentric_pullup 3x3 <-assisted_pullup`. The set count stays the same.

### M3: Major. The pull-up dose at L3 entry (L3:013, 015, 016, 020, all original source)
**Problem.** The source L3 block assumes about 13–15 pull-ups. The placement threshold is 10, and an L2 graduate's last checkpoint is 1x10 (L2:098). These plain "Pull Up" steps show no easier option, because only the steps renamed Strict Pull-Up get one.

**Evidence.**
- **L3:013** ends with 1x10 r60. Before that come about 15 pull-ups, 3 band muscle-ups, 3 negative muscle-ups and about 44 dips.
- **L3:015** puts 10 pull-ups between two sets of 10 bar dips.
- **L3:016** asks for 10 pull-ups, then 9 at 60 s rest.
- **L3:020** totals about 42 pull-ups: 8, then 4/4/4, then a 90° isometric 2x8 at r240, then 6 around-the-world.
- Compare L3's own normal dose of 3x8 r105 and its checkpoint of 1x11 (L3:032).

**Fix.**
- In L3:001–020, cap every plain pull-up or chin-up set at 8 reps, the same cap the app already applies to the source's old max sets:
  - L3:013: 1x10 becomes 1x8.
  - L3:015: 10 becomes 8.
  - L3:016: 10,9 r60 becomes 8,7 r90.
  - L3:020: the isometric 2x8 becomes 2x5.
- In these classes, show "Easier option: Foot-Assisted Pull-Up" on every plain Pull Up or Chin Up step, and "Easier option: Straight-Bar Support" on Bar Dips. A placed L3 athlete has never done straight-bar dips, yet L3:001 asks for 10.

### M4: Major. The pull-up dose in L4–L5 (L4:001–076, L5:001–043)
**Answer to the owner's question.** Three sets of 10 is the right L4 *destination*, but the wrong L4:001 *opener*.
- Dips at 12 are fine. They have been at 12 since L3:030, and holding them there is useful while pressing moves on to pseudo-planche push-ups and handstand push-ups.

**Problem.**
1. **A cliff.** L3's normal dose is 3x8 r105, with a checkpoint of 1x11. L4:001 jumps to 3x10 r120, about 25% more. Staying at RPE 7 on that needs a max of about 14–15. It then stays flat at 3x10 for 100 classes.
2. **Paused and slow tempos at 10–11 reps.** A 22X2 tempo means 6–7 s per rep. At that tempo, 3x10 or 3x11 is worth more than 15 normal reps.
3. **75 s rests** on 10–11-rep sets.
4. **A fallback that is far too easy.** A foot-assisted pull-up on a low bar is about a 50% step down for someone with 11–13 pull-ups. Many L1 starters will be at 10–13 by day 300, especially women and heavier men.

**Evidence.**
- L4:001/015/029/043/057/071: 3x10. L4:015 is at 22X2 r125; L4:043 is at r90.
- L4:006/020/034: 2x10 r75. L4:020 is at 22X2.
- L4:013: 2x10 at 31X1.
- L5:001: 3x11 r75, labelled RPE 6.
- L5:015: 3x11 22X2. L5:020: 3x11 22X2 r75. L5:043: 3x11 r95.
- The checkpoints are L4:083 at 1x12 and L5:083 at 1x15.

**Fix.**
- **L4 ramp:**
  - 3x8 at normal tempo on L4:001/008/015.
  - 3x9 on L4:029/043.
  - 3x10 from L4:057.
  - Chin-ups follow the same steps, 2 reps lower.
- **Tempo sets in L4–L5:**
  - At 22X2 or 41X1: 3x6–7 (for example L4:015 at 3x6 22X2; L5:015 and L5:020 at 3x7 22X2, with L5:020 at r120).
  - At 31X1: 2x8 (L4:013, L5:097).
- **No pull-up set of 10 or more reps with less than 105 s rest:**
  - L4:006/020/034: 2x10 r75 becomes 2x8 r105.
  - L5:001: 3x11 r75 becomes 3x10 r105.
  - L5:043: 3x11 r95 becomes 3x10 r110.
- **L5:** 3x10 on L5:001–021, 3x11 from L5:029, and 3x12 at L5:099 as now.
- **Fallback:** for pull-up sets of 8+ reps in L4–L7, the easier rung becomes `<-chinup`. The chin-up keeps `<-assisted_pullup`.
- Keep the checkpoint singles as they are.

### M5: Major. Weekly load and order in L6–L7
**Problem.** L4–L5 weeks have two easy days (d4 and d7). L6–L7 have only d4, and d7 becomes a full "fundamentals" session. That means six straight days at RPE 6–8 every week, with 10–15% more weekly sets than L5. It happens exactly when straddle and full levers, 16-rep strict muscle-up sessions, one-arm pull-up work and freestanding handstand push-up negatives arrive. In L7, the one-arm pulling on d6 also comes the day after the straddle and full back-lever day (d5). This is where I see elbow and biceps-tendon trouble in adults with jobs.

**Evidence.**
- **Six-hard-day runs:**
  - L6: 005–010, 012–017, 033–038, 040–045, 061–066.
  - L7: 005–010, 012–017, 033–038, 040–045, 061–066, 068–073, 089–094.
- **L6's d7 classes hit the 20-set cap** (L6:007/014/021/035). They hold handstand push-ups 3x2–3, feet-elevated rows 3x10, dips 3x12–13, push-ups 3x20, squats 2x32 and toes-to-bar 2x8, about 54 min, yet are labelled "reserve" or RPE 6.
- **Weekly sets:** L4 about 105–110, L5 about 112–120, L6 about 123–132.
- **L7 d5 → d6:**
  - L7:061 (full back lever 3x2s, straddle 2x10s, feet-elevated rows 3x10) is followed by L7:062 (assisted one-arm pull-ups 3x2/side).
  - L7:068 is followed by L7:069 (one-arm 3x3/side).

**Fix.**
- **Make d7 an easy day** in the non-deload weeks of L6 (L6:007/014/021/035/042/049/063/070/077/098) and L7 (L7:007/014/021/035/042/049/063/070/077/091):
  - RPE 4–5 and no more than 10 sets.
  - Keep 2 handstand push-up skill sets and toes-to-bar 2x8, plus one set each of dips and push-ups.
  - Drop the squat, row and archer volume.
- **L7 d6:**
  - Remove the archer and typewriter sets, which d3 already trains.
  - Move the assisted one-arm pull-ups and one-arm negatives (L7:062/069/076/083/090) to the next d2 class, replacing two of its pull-up sets. For example, L7:065 becomes chin-up 1x10 plus assisted one-arm 2x3/side.
  - Pull days become d2, d3 and d5, with the d4 rest between them.

### Minors (5)
- **m1: L5 week 1 has its days swapped.** L5:001 (fundamentals) and L5:006 (front lever) are swapped compared with every other L4/L5 week. Front-lever work therefore lands on L5:005, 006 and 008, 3 of 4 days, and pull-ups 3x11 come twice in 6 days.
  - **Fix:** swap the content of L5:001 and L5:006.
- **m2: The front lever and muscle-up stall in L4.** The advanced-tuck front lever stays at 3x8s from L3:056 to L4:078, about 120 classes. Strict muscle-ups stay at 3x2 or 2x3 for all of L4.
  - **Fix:**
    - Advanced-tuck front lever 3x9s from L4:029, then 3x10s from L4:057.
    - Introduce the one-leg front lever (2x4s/side) at L4:071 instead of L4:092. The 6-week gate is already met, and the three-10-second gate is met from L4:057.
    - Strict muscle-ups 3x3 from L4:057.
- **m3: L1's handstand dose is close to zero for 70 classes.** Practice is overhead_line 2x15s (standing, arms overhead) on L1:001–070.
  - **Fix:** from L1:022, practise `pike_handstand_hold 1x10s <-overhead_line` plus overhead_line 1x20s (the L1:071 dose).
- **m4: One easier rung is not enough at L6–L7.** Most athletes will plateau at the advanced-tuck or one-leg front lever and at the tuck flag. Facing `straddle_front 4x8s <-one_leg_front`, they need to go two rungs down.
  - **Fix:** add one sentence to the fallback text: "If the easier option isn't clean either, use *its* easier option for the same sets."
- **m5: Some RPE labels don't match the dose.**
  - L5:003 is 15 sets of pressing labelled RPE 6: tuck planche 3x7s, pseudo-planche push-ups 4x7, dips 4x12 and elevated pike push-ups 4x9.
  - L6:001 opens L6 with strict muscle-ups 4x3 r150 labelled RPE 6, then drops back to 3x3 r165–180.
  - **Fix:** L5:003 uses 3 sets of each (12 sets), and L6:001 uses strict muscle-ups 3x3 r165.

## 4. Is it achievable, and where people plateau

Classes are counted from L1:001, so L2:001 is class 101 and L3:001 is class 201.

| Goal | Path (first class) | Where most consistent adults land | Acceptable? |
|---|---|---|---|
| Muscle-up | Low bar from L1:045 → band from L2:076 → strict check at L3:065, training from L3:077 | 1–5 strict muscle-ups during L3–L5 | Yes |
| Handstand | Wall from L2:071, balance attempts from L4:036 (287 classes) | A 10–30 s freestanding hold during L5–L6 | Yes, the best-dosed skill |
| Back lever | Tuck L3:029 → straddle L6:026 → full L7:033 | Straddle for many, full for some | Yes |
| Front lever | Advanced tuck L3:035 → one-leg L4:092 → straddle L6:024 → full L7:030 | Advanced tuck or one-leg for most, full for a minority | Yes, since the lower rungs stay in the class (add m4) |
| Human flag | Ground support L2:001 → tuck L5:073 → straddle L6:072 → full L7:071 | Tuck or straddle | As a ceiling, yes. It is the thinnest dose of the five (twice a week, about 10–30 s of holds) and needs stall bars or a pole |
| Pull-ups | 3x4 at L2:001 → 3x8 in L3 → 3x10–12 in L4–L7 | 10–14 for many L1 starters by class 300–400 | Only after M4 |
| Planche and dragon flag (supporting) | Tuck planche L4:017, dragon flag L5:058 | Tuck or advanced-tuck planche; a dragon flag for many | Yes |

## 5. Keep
- **Placement.** The weakest answer decides, it caps at L3, and "start from scratch" is offered.
- **The daily practice block** right after the warm-up: handstand most days, flag twice a week. Micro-doses, practised fresh.
- **Gates written as numbers**, such as "three 10-second holds" or "six weeks after the first advanced-tuck", and readiness checks as single test sets with a fallback.
- **The muscle-up ladder:** low-bar transition, pullover, jump, negative, band, strict.
- **Back-lever prep:** the supported German hang and skin-the-cat come first.
- **Handstand prep:** the cartwheel exit is taught before balance attempts, and handstand push-up negatives come before full reps.
- **Realistic ceilings:** full planche is never prescribed, and the straddle planche and full lever, flag and one-arm work only appear late, as brief holds with the easier rung in the same class.
- **Load management:** deloads every 22–36 classes, RPE rising by level, every class within the set and per-exercise caps, and nearly all classes 30–55 minutes.
- **The L4–L5 week template** (front lever / dragon-legs / planche / easy / muscle-up and back lever / fundamentals / easy), with tempo waves for variety. Only the pull-up doses need fixing (M4).
- **Leg and hamstring work** (nordic curls, pistols, single-leg RDLs) and the trunk progression (hollow, L-sit, toes-to-bar, dragon flag).
