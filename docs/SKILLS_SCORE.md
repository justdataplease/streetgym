# Skills and the overall score (29 October 2026, round 10)

Skills work like belts. Each rung of a ladder (pull-up, planche, handstand and so on) has five levels: **Beginner, Intermediate, Advanced, Master and Elite**. A level is earned with sets at its target, done in good form. Nothing is unlocked by attending, by skipping ahead or by a one-off test.

This replaces the 27 October model: 100 points per skill from prep and practice, readiness tests, "Log your max" and `SkillTestLog`.

## What is a skill

A skill is something done on one's own, from a dead hang to a full planche (the owner's call, 28 October 2026). Assisted, supported and partial versions, negatives and drills are progressions: they stay in the classes as steps and easier options, listed as their family's `foundations`, which never become rungs.

Since round 9 there are **37 skills** (the owner's choice on 29 October, from the claude-1 proposal): the 35 that at least four coaches backed, plus the dead hang and the hollow body hold as early belts. 185 levels in all. Twenty former skills are now movements only: the active hang, chin-up, typewriter pull-up, chest-to-bar, bar support, support hold, straight-bar dip, pause, close and archer push-ups, elevated pike push-up, planche lean, frog stand, handstand balance, the one-leg front and back levers, the one-leg L-sit, knee raise, sliding leg curl and Nordic curl. The squat is new. The families: pull-up (dead hang, pull-up, archer, one-arm), muscle-up, front lever, back lever, dip, push-up (push-up, one-arm), handstand push-up (pike, wall, deficit), planche (pseudo-planche push-up, tuck, advanced tuck, straddle, full), handstand (wall, freestanding), pistol (squat, pistol), hanging core (leg raise, toes-to-bar), L-sit (L-sit, V-sit), dragon flag (hollow body, dragon flag) and the human flag.

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

Every target is in `SkillStandards.kt` and `docs/CURRICULUM_PLAN.md`. The rarest skills have their Elite set where a committed adult can reach it inside the program (the realism check, 29 October 2026): full planche 2/2/2/3/4 s, one-arm pull-up 1/1/1/2/3 a side, straddle planche 2/3/4/5/6 s, full front lever 2/3/4/6/8 s, full flag 2/3/4/5/6 s a side, strict muscle-up 1/2/3/4/5 and V-sit 2/3/4/5/6 s. From Advanced up every level takes 30 sets, so no two of those levels share a target; Beginner to Advanced may share one and differ by their sets.

- A set over a higher target counts for the lower ones too, so 30 sets at the Elite target earn all five levels at once, Master included, on a program day or an own workout. Each level needs the one below it.
- Beginner is the hook: about three quarters of a coach's entry standard, and only 10 sets. The owner asked for a first badge early; levels from Intermediate up are hard on purpose.
- A level held on a harder rung of a ladder is held on every easier rung too, never higher (round 10, `SkillStatus.carriedBy`). Elite on the full back lever makes the straddle, advanced tuck and tuck back levers Elite. The skill's page says "Held through" and names the rung.
  - Why: the next rung is harder at every level. Thirty sets of a 12 s full back lever mean a tuck held far longer than its 20 s, so a progression below the shape you hold can never show a lower belt. The owner asked that nothing look strange between a full skill and its progressions.
  - It is the same level, never more. Until 29 October a harder rung at Beginner showed the easier rungs as Master; round 8 dropped that jump, and round 10 keeps it dropped.
  - The next level of a carried rung still takes its own sets. A carried rung with no sets of its own stays out of "closest to the next level".
- Each level is its target many times over. Elite on the push-up means 30 separate sets of 100, far more than the single performances published standards compare. The help says so.
- No Beginner target asks for a longer set than the plan's own longest one on a rung the plan trains 30 times or more (`SkillBookTest`).

## Following the program as written

Results for someone who does every class of the ten levels as written and trains nothing extra (counted by `tools/build_curriculum.py` from the classes; scores with the app's values and the leaderboard's 767 ceiling):

| End of level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Overall score (of 1000) | 12 | 24 | 56 | 83 | 118 | 162 | 193 | 281 | 465 | 698 |
| Levels earned (of 185) | 8 | 16 | 30 | 44 | 57 | 76 | 87 | 106 | 126 | 156 |
| Skills at Beginner or more (of 37) | 7 | 10 | 13 | 20 | 23 | 28 | 30 | 35 | 37 | 37 |

- The first badge comes at class 10 (squat Beginner); the hollow body and push-up follow by class 16.
- Every level brings new badges, and every skill reaches Beginner inside the plan, the last being the full planche at L9:065.
- Every skill reaches its Elite target by the end of Level 10 (the owner: "at least the max should be achievable, it's ok if not the sets"). 34 reach it in their working sets, the one-arm pull-up in one Level 10 record set, and the tuck and advanced tuck back levers hold Elite itself through the full back lever.
- A progression peaks while it is still trained, before the next rung takes over: the advanced tuck front lever at 15 s in Level 7, the tuck flag at 15 s in Level 6, the advanced tuck planche at 12 s in Level 8, the L-sit at 30 s as Level 9 begins and the wall handstand at 75 s in Level 5. No record set sits beside a harder rung of its ladder (`validate_progressions.py`).
- Elite takes 30 sets at the target, so the plan alone reaches Elite on 16 skills: the basics, the strict muscle-up, the archer pull-up, the dragon flag and the full back lever, plus the dead hang through the pull-up and the tuck, advanced tuck and straddle back levers through the full back lever. The rest of Elite comes from repeating blocks and the athlete's own workouts, by design. The skills help says so.

## The overall score (`OverallScore`)

Each rung has a value (`SKILL_VALUES`), anchored on a strict pull-up at 10:

| Rung | Value |
|---|---|
| Strict pull-up | 10 |
| Tuck front lever | 15 |
| Full back lever | 50 |
| Full front lever | 100 |
| Full one-arm pull-up | 110 |
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

- The score document (version 2) holds:
  - `overall`;
  - `families`: ten times each family's points;
  - `verified`: the rungs at Beginner or above.
- The board function (`firebase/functions/boards.py`) drops any document whose overall doesn't add up from its families. Its family ceilings must match the app's; `SkillBookTest` checks this.
- Round 9 dropped the Nordic family, but the deployed function still divides by all 15 ceilings (767). So the app keeps Nordic's 24 in the score's maximum (`OverallScore.RETIRED_CEILINGS`), and a score's top is 969, shown 96.9. The dip family keeps its top value of 12 on the parallel dip. The next functions deploy can drop Nordic from `FAMILY_CEILINGS` and `RETIRED_CEILINGS` together; a deploy is needed for any change to a family's top value.
- Boards: `overall_*`, plus one `family_<id>_*` board per family.
- `firestore.rules` caps the overall at 1000 and the families at 15. The rules and the function don't change with this model.
