# Skills and the overall score (29 October 2026, round 11)

Skills work like belts. Each rung of a ladder (pull-up, planche, handstand and so on) has five levels: **Beginner, Intermediate, Advanced, Master and Elite**. A level is earned with sets at its target, done in good form. Nothing is unlocked by attending, by skipping ahead or by a one-off test.

This replaces the 27 October model: 100 points per skill from prep and practice, readiness tests, "Log your max" and `SkillTestLog`.

## What is a skill

A skill is something done on one's own, from a dead hang to a full planche (the owner's call, 28 October 2026). Assisted, supported and partial versions, negatives and drills are progressions: they stay in the classes as steps and easier options, listed as their family's `foundations`, which never become rungs.

Round 9 set **37 skills** (the owner's choice on 29 October, from the claude-1 proposal): the 35 that at least four coaches backed, plus the dead hang and the hollow body hold as early belts. 185 levels in all. Twenty former skills are now movements only: the active hang, chin-up, typewriter pull-up, chest-to-bar, bar support, support hold, straight-bar dip, pause, close and archer push-ups, elevated pike push-up, planche lean, frog stand, handstand balance, the one-leg front and back levers, the one-leg L-sit, knee raise, sliding leg curl and Nordic curl. The squat is new. The families: pull-up (dead hang, pull-up, archer, one-arm), muscle-up, front lever, back lever, dip, push-up (push-up, one-arm), handstand push-up (pike, wall, deficit), planche (pseudo-planche push-up, tuck, advanced tuck, straddle, full), handstand (wall, freestanding), pistol (squat, pistol), hanging core (leg raise, toes-to-bar), L-sit (L-sit, V-sit), dragon flag (hollow body, dragon flag) and the human flag.

Round 11 brings **40 skills** in 16 families, 200 levels in all. The one-arm handstand tops the handstand ladder (wall, freestanding, one-arm), and the shrimp squat (rear foot held) and the Hefesto are families of their own, one rung each. Their steps are movements: the straddle handstand, the supported and freestanding weight shifts, the fingertip-assisted one-arm handstand, the hover and the straddle one-arm handstand; the assisted partial, assisted, floating-leg and assisted foot-held shrimp squats; and the foot-assisted supinated German hang, behind-bar support, Korean dip, partial Hefesto and Hefesto. The shrimp steps share the pistol's single-leg-squat pattern, so they stay foundations of the shrimp family and never join the pistol ladder.

## What counts

- Only the sets ticked in a finished class count, and only in the practice and main sections (`WorkoutFlow.credits`).
- Each set keeps its dose (`WorkoutFlow.doses`), the reps or seconds done in the skill's measure:
  - by default, the plan's reps or seconds;
  - if the athlete logged a number with "Did more or fewer?" (`SetDone.kt`), that number;
  - for a "max" set, the logged reps, else 0;
  - for an easier option measured the other way (a tuck front lever row for a timed tuck hold, a support hold for straight-bar dips, and so on), the logged reps or seconds of the option, else 0. Switching the option clears what was logged for the other movement.
- The set logger asks in the measure of the movement actually done (`WorkoutFlow.measure`), and asks straight away when a set has no plan's number to count.
- Doses are saved on `WorkoutSession.doses` and synced with cloud progress. Older sessions without doses count the program's prescription (`SkillStep.reps`/`seconds`).
- Program days, repeated days and the athlete's own workouts all count. A class counts once it ran at least 20% of its planned minutes.
- Skipping ahead gives nothing: days placed past by the placement test, Complete day and Complete level earn no sets (`Scores.credits`).
- A set done as the easier option counts for the easier movement when it is a skill.
- Readiness-check sets count for nothing.
- Harder days scale the plan's reps and holds by 1.25, so their sets reach targets sooner.
- Each skill's page says what a set must be to count, from the movement library's `counts` text (the coaching review, 29 October 2026). A hold starts once the shape is set and ends when it breaks. A rep starts still and finishes at lockout, without a kip or swing. The rules also pin down the shapes whose difficulty varies most:
  - the planche lean and pseudo-planche push-up are measured against the same floor line every time;
  - the V-sit's legs are at least 45 degrees above level;
  - the pike and deficit handstand push-ups have a set foot height and a 10 cm deficit;
  - archer movements keep the guiding arm straight;
  - a Nordic curl counts only without the hands.

## Levels (`SkillStandards`, `SkillBook`)

Each rung has a standard: whether it is measured in reps or seconds, whether it's done one side at a time (the weaker side counts), a grade from 1 (easy) to 5 (extreme), and five targets.

Targets are **working sets**: what a trained athlete does for one set inside a class, one or two reps in reserve, not a single maximal effort (round 9). Thirty sets of a 40-rep pull-up is a career, not a belt.

| Level | Sets needed at the target | Pull-up | Push-up | Dip | Wall handstand |
|---|---|---|---|---|---|
| Beginner | 10 | 3 | 6 | 4 | 20 s |
| Intermediate | 20 | 6 | 12 | 10 | 30 s |
| Advanced | 30 | 10 | 20 | 15 | 45 s |
| Master | 30 | 15 | 30 | 20 | 60 s |
| Elite | 30 | 20 | 40 | 25 | 75 s |

Every target is in `SkillStandards.kt` and `docs/CURRICULUM_PLAN.md`. The rarest skills have their Elite set where a committed adult can reach it inside the program (the realism check, 29 October 2026, retuned in round 11 so no hold starts under 3 s): full planche 3/3/4/5/6 s, one-arm pull-up 1/1/1/2/3 a side, Hefesto 1/1/2/3/4, one-arm handstand 3/4/5/7/10 s a side, straddle planche 3/4/5/6/8 s, full front lever 3/4/5/7/10 s, full back lever 3/5/8/12/15 s, full flag 3/4/5/6/8 s a side, strict muscle-up 1/2/4/6/8 and V-sit 3/4/5/6/8 s. The shrimp squat asks 2/3/5/7/10 a side and the freestanding handstand 5/15/30/45/60 s. From Advanced up every level takes 30 sets, so no two of those levels share a target; Beginner to Advanced may share one and differ by their sets.

- A set over a higher target counts for the lower ones too, so 30 sets at the Elite target earn all five levels at once, Master included, on a program day or an own workout. Each level needs the one below it.
- Beginner is the hook: about three quarters of a coach's entry standard, and only 10 sets. The owner asked for a first badge early; levels from Intermediate up are hard on purpose.
- A level held on a harder rung of a ladder is held on every easier rung too, never higher (round 10, `SkillStatus.carriedBy`). Elite on the full back lever makes the straddle, advanced tuck and tuck back levers Elite. The skill's page says "Held through" and names the rung.
  - Why: the next rung is harder at every level. Thirty sets of a 12 s full back lever mean a tuck held far longer than its 20 s, so a progression below the shape you hold can never show a lower belt. The owner asked that nothing look strange between a full skill and its progressions.
  - It is the same level, never more. Until 29 October a harder rung at Beginner showed the easier rungs as Master; round 8 dropped that jump, and round 10 keeps it dropped.
  - The next level of a carried rung still takes its own sets. A carried rung with no sets of its own stays out of "closest to the next level".
- Each level is its target many times over. Elite on the push-up means 30 separate sets of 100, far more than the single performances published standards compare. The help says so.
- No Beginner target asks for a longer set than the plan's own longest one on a rung the plan trains 30 times or more (`SkillBookTest`).

## Following the program as written

Results for someone who does every class of the ten levels as written and trains nothing extra (counted by `tools/build_curriculum.py` from the classes; scores with the app's values and score version 3's ceiling of 887):

| End of level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Overall score (of 1000) | 13 | 30 | 46 | 76 | 110 | 136 | 195 | 285 | 454 | 726 |
| Levels earned (of 200) | 11 | 20 | 28 | 43 | 56 | 69 | 83 | 105 | 129 | 165 |
| Skills at Beginner or more (of 40) | 7 | 10 | 13 | 21 | 23 | 27 | 31 | 36 | 40 | 40 |

- The first badge comes at class 11 (hollow body Beginner); the push-up and squat follow at class 13.
- Every level brings new badges, and every skill reaches Beginner inside the plan, the last being the one-arm handstand at L9:090.
- Every skill reaches its Elite target by the end of Level 10 (the owner: "at least the max should be achievable, it's ok if not the sets"). 38 reach it in a set of their own, the one-arm handstand, shrimp squat and Hefesto in Level 10 record sets, and the dead hang and hanging leg raise hold Elite through the pull-up and toes-to-bar.
- A progression peaks while it is still trained, before the next rung takes over. No record set sits beside a harder rung of its ladder (`validate_progressions.py`).
- Elite takes 30 sets at the target, so the plan alone reaches Elite on 13 skills: the push-up, squat, hollow body, pull-up, dip, the pseudo-planche push-up, the freestanding handstand, the pistol, toes-to-bar and the dragon flag, plus the dead hang, wall handstand and hanging leg raise through the harder rung of their ladders. The rest of Elite comes from repeating blocks and the athlete's own workouts, by design. The skills help says so.

## The overall score (`OverallScore`)

Each rung has a value (`SKILL_VALUES`), anchored on a strict pull-up at 10:

| Rung | Value |
|---|---|
| Strict pull-up | 10 |
| Tuck front lever | 15 |
| Shrimp squat | 14 |
| Full back lever | 50 |
| Hefesto | 50 |
| Full front lever | 100 |
| Full one-arm pull-up | 110 |
| One-arm handstand | 110 |
| Straddle planche | 110 |
| Full planche | 200 |

How it adds up:
- A rung earns a fifth of its value per level, so Elite earns all of it.
- Each family counts its best rung.
- The overall is 1000 × the families' sum ÷ the sum of every family's top value.
- The app shows it out of 100 with one decimal; the leaderboard keeps the 1000 scale.

| Tier | Score |
|---|---|
| Beginner | 0–74 |
| Novice | 75–199 |
| Intermediate | 200–424 |
| Advanced | 425–649 |
| Elite | 650–849 |
| World-class | 850–1000 |

## Display (`SkillMedals.kt`)

Each skill is its figure on a rounded square:
- **Outline:** shows the rung's grade, from green (easy) to red (extreme).
- **Rank shield:** a game-style insignia hanging from the square's foot: a peaked shield with a gold rim and a glossy graphite face. Beginner has one gold chevron band and Intermediate two. Advanced has three stars and Master one big star under a stacked crest. Elite has the big star with wings and a taller crest.

## Exams

On an exam day, a target starts ticked only if all of the following hold (`WorkoutFlow.examTests`):
- every set was ticked;
- no set was done as the easier option;
- no logged number fell short of the plan.

The athlete can still change the answer.

## Leaderboard

- The score document (version 3 since round 11) holds:
  - `overall`;
  - `families`: ten times each family's points;
  - `verified`: the rungs at Beginner or above.
- The board function (`firebase/functions/boards.py`) drops any document whose overall doesn't add up from its families. Its family ceilings must match the app's; `SkillBookTest` checks this.
- Round 9 dropped the Nordic family, but the deployed function kept dividing by all 15 ceilings (767), so the app kept Nordic's 24 in the score's maximum (`OverallScore.RETIRED_CEILINGS`) and a score topped out at 969, shown 96.9. Round 11 needs a deploy for its new families anyway: version 3 of the score drops Nordic from `FAMILY_CEILINGS` and `RETIRED_CEILINGS` together, raises the handstand family to 110 and adds shrimp 14 and hefesto 50. The 16 ceilings add up to 887, and every family's top at Elite is the whole 1000 again. The dip family keeps its top value of 12 on the parallel dip. A deploy is needed for any change to a family's top value.
- Until the rules and function are deployed, the old function drops version 3 scores, and the old rules refuse them: ship the app and the deploy together.
- Boards: `overall_*`, plus one `family_<id>_*` board per family.
- `firestore.rules` caps the overall at 1000 and the families at 16, and accepts only version 3.
