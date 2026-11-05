# Skills, ranks and points (31 October 2026, 2.10.0)

Skills work like belts. Each rung of a ladder (pull-up, planche, handstand and so on) has five ranks, **Rank 1 to Rank 5**, each worn as its shield. A rank is earned with sets at its target, done in good form. Nothing is unlocked by attending, by skipping ahead or by a one-off test. In the code the ranks keep their old names (`SkillLevel` Beginner, Intermediate, Advanced, Master and Elite); the app says Rank 1–5 since 2.10.0 (the owner: "change the chevron level, say rank").

**Points** are the one number on Skills and the leaderboard (2.10.0, the owner: "the more points, the overall score increases, even when doing custom workouts"): every counted set earns its reps times how hard its movement is, and every rank adds a bonus. They replace the overall score out of 100, its tiers (Beginner to World-class), the Pull, Push, Legs and Core scores and the per-skill values.

This replaces the 27 October model: 100 points per skill from prep and practice, readiness tests, "Log your max" and `SkillTestLog`.

## What is a skill

A skill is something done on one's own, from a dead hang to a full planche (the owner's call, 28 October 2026). Assisted, supported and partial versions, negatives and drills are progressions: they stay in the classes as steps and easier options, listed as their family's `foundations`, which never become rungs.

Round 9 set **37 skills** (the owner's choice on 29 October, from the claude-1 proposal): the 35 that at least four coaches backed, plus the dead hang and the hollow body hold as early belts. 185 levels in all. Twenty former skills are now movements only: the active hang, chin-up, typewriter pull-up, chest-to-bar, bar support, support hold, straight-bar dip, pause, close and archer push-ups, elevated pike push-up, planche lean, frog stand, handstand balance, the one-leg front and back levers, the one-leg L-sit, knee raise, sliding leg curl and Nordic curl. The squat is new. The families: pull-up (dead hang, pull-up, archer, one-arm), muscle-up, front lever, back lever, dip, push-up (push-up, one-arm), handstand push-up (pike, wall, deficit), planche (pseudo-planche push-up, tuck, advanced tuck, straddle, full), handstand (wall, freestanding), pistol (squat, pistol), hanging core (leg raise, toes-to-bar), L-sit (L-sit, V-sit), dragon flag (hollow body, dragon flag) and the human flag.

Round 11 brings **40 skills** in 16 families, 200 ranks in all. The one-arm handstand tops the handstand ladder (wall, freestanding, one-arm), and the shrimp squat (rear foot held) and the Hefesto are families of their own, one rung each. Their steps are movements: the straddle handstand, the supported and freestanding weight shifts, the fingertip-assisted one-arm handstand, the hover and the straddle one-arm handstand; the assisted partial, assisted, floating-leg and assisted foot-held shrimp squats; and the foot-assisted supinated German hang, behind-bar support, Korean dip, partial Hefesto and Hefesto. The shrimp steps share the pistol's single-leg-squat pattern, so they stay foundations of the shrimp family and never join the pistol ladder.

## What counts

- Only the sets ticked in a finished class count, and only in the practice and main sections (`WorkoutFlow.credits`).
- **The 30% rule** (the owner, 2.10.0: "it only counts if 30% of the time passes for each exercise"; it replaces the old rule that a whole class had to run 20% of its planned minutes). An exercise's ticked sets count, for ranks and points alike, only once its ticks took at least 30% of its planned time (`WorkoutFlow.counted`, `Points.timeShare`):
  - a set's planned time is its work, a hold's seconds or 3 s a rep (a "max" set as ten reps), both sides when it is per side, plus the rest before it (`WorkoutFlow.plannedSeconds`);
  - the app keeps when each set was ticked (`ActiveWorkout.tickedAt`); the time from one tick to the next goes to the set ticked, and sets ticked together, such as a whole section, share it by their planned time;
  - pull-ups, 4 sets of 6 with 2-minute rests, are planned at about 7 minutes and count after about 2; ticking all four in 20 seconds earns nothing;
  - a class started before 2.10.0 kept no tick times and counts every tick.
- Each set keeps its dose (`WorkoutFlow.doses`), the reps or seconds done in the skill's measure:
  - by default, the plan's reps or seconds;
  - if the athlete logged a number with "Did more or fewer?" (`SetDone.kt`), that number;
  - for a "max" set, the logged reps, else 0;
  - for an easier option measured the other way (a tuck front lever row for a timed tuck hold, a support hold for straight-bar dips, and so on), the logged reps or seconds of the option, else 0. Switching the option clears what was logged for the other movement.
- The set logger asks in the measure of the movement actually done (`WorkoutFlow.measure`), and asks straight away when a set has no plan's number to count.
- Doses are saved on `WorkoutSession.doses` and synced with cloud progress. Older sessions without doses count the program's prescription (`SkillStep.reps`/`seconds`).
- Program days, repeated days, ready workouts and the athlete's own workouts all count.
- Skipping ahead gives nothing: days placed past by the placement test, Complete day and Complete level earn no sets and no points (`Scores.credits`).
- A set done as the easier option counts for the easier movement when it is a skill.
- Readiness-check sets count for nothing.
- Harder days scale the plan's reps and holds by 1.25, so their sets reach targets sooner.
- Each skill's page says what a set must be to count, from the movement library's `counts` text (the coaching review, 29 October 2026). A hold starts once the shape is set and ends when it breaks. A rep starts still and finishes at lockout, without a kip or swing. The rules also pin down the shapes whose difficulty varies most:
  - the planche lean and pseudo-planche push-up are measured against the same floor line every time;
  - the V-sit's legs are at least 45 degrees above level;
  - the pike and deficit handstand push-ups have a set foot height and a 10 cm deficit;
  - archer movements keep the guiding arm straight;
  - a Nordic curl counts only without the hands.

## Ranks (`SkillStandards`, `SkillBook`)

Each rung has a standard: whether it is measured in reps or seconds, whether it's done one side at a time (the weaker side counts), a grade from 1 (easy) to 5 (extreme), and five targets.

Targets are **working sets**: what a trained athlete does for one set inside a class, one or two reps in reserve, not a single maximal effort (round 9). Thirty sets of a 40-rep pull-up is a career, not a belt.

| Rank | Sets needed at the target | Pull-up | Push-up | Dip | Wall handstand |
|---|---|---|---|---|---|
| Rank 1 (Beginner) | 10 | 3 | 6 | 4 | 20 s |
| Rank 2 (Intermediate) | 15 | 6 | 12 | 10 | 30 s |
| Rank 3 (Advanced) | 20 | 10 | 20 | 15 | 45 s |
| Rank 4 (Master) | 25 | 15 | 30 | 20 | 60 s |
| Rank 5 (Elite) | 30 | 20 | 40 | 25 | 75 s |

Every target is in `SkillStandards.kt` and `docs/CURRICULUM_PLAN.md`. The sets are five more each rank (review 11, `SkillLevel.sets`). The rarest skills have their Rank 5 set where a committed adult can reach it inside the program (the realism check, 29 October 2026, retuned in round 11 so no hold starts under 3 s): full planche 3/3/4/5/6 s, one-arm pull-up 1/1/1/2/3 a side, Hefesto 1/1/2/3/4, one-arm handstand 3/4/5/7/10 s a side, straddle planche 3/4/5/6/8 s, full front lever 3/4/5/7/10 s, full back lever 3/5/8/12/15 s, full flag 3/4/5/6/8 s a side, strict muscle-up 1/2/4/6/8 and V-sit 3/4/5/6/8 s. The shrimp squat asks 2/3/5/7/10 a side and the freestanding handstand 5/15/30/45/60 s. Where two ranks share a target they differ only by their sets.

- A set over a higher target counts for the lower ones too, so 30 sets at the Rank 5 target earn all five ranks at once, on a program day or an own workout. Each rank needs the one below it.
- Rank 1 is the hook: about three quarters of a coach's entry standard, and only 10 sets. The owner asked for a first badge early; the ranks above are hard on purpose.
- A rank held on a harder rung of a ladder is held on every easier rung too, never higher (round 10, `SkillStatus.carriedBy`). Rank 5 on the full back lever makes the straddle, advanced tuck and tuck back levers Rank 5. The skill's page says "Held through" and names the rung.
  - Why: the next rung is harder at every rank. Thirty sets of a 12 s full back lever mean a tuck held far longer than its 20 s, so a progression below the shape you hold can never show a lower belt. The owner asked that nothing look strange between a full skill and its progressions.
  - It is the same rank, never more. Until 29 October a harder rung at Beginner showed the easier rungs as Master; round 8 dropped that jump, and round 10 keeps it dropped.
  - The next rank of a carried rung still takes its own sets.
- Each rank is its target many times over. Rank 5 on the push-up means 30 separate sets of 40, far more than the single performances published standards compare. The help says so.
- No Rank 1 target asks for a longer set than the plan's own longest one on a rung the plan trains 30 times or more (`SkillBookTest`).

## Following the program as written

Results for someone who does every class of the ten levels as written, every set as planned on Normal, and trains nothing extra. They cover the hand-written program (Round X, 2.12.0). Ranks are counted with the app's rules (the belt counter in `tools/build_curriculum.py`) from the classes. Points use the 2.10.0 rules, rounded per class as the app saves them, from the points simulation of 5 November 2026, which reproduces the 2.11.6 table exactly.

| End of level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Points | 11,556 | 28,042 | 47,614 | 70,018 | 95,704 | 126,275 | 156,539 | 191,411 | 228,429 | 273,077 |
| Ranks reached (of 200) | 7 | 17 | 26 | 42 | 59 | 74 | 90 | 113 | 141 | 186 |
| Skills at Rank 1 or more (of 40) | 5 | 10 | 13 | 19 | 23 | 26 | 32 | 36 | 40 | 40 |

- The first rank comes at class 26 (hollow body, Rank 1); the dead hang follows at 46, the squat at 48 and the push-up at 64. Level 1 is gentler than in 2.11 (the first rank was at class 11), and its classes are shorter: the minutes now match the app's own timing.
- Every level brings new ranks, and every skill reaches Rank 1 inside the plan, the last being the one-arm handstand at L9:095.
- Every skill reaches its Rank 5 target at least once inside the plan, and gets at least 21 of the 30 Rank 5 sets there (review 11, the owner's 70% rule; `validate_progressions.py`).
- A progression peaks while it is still trained, before the next rung takes over. No record set sits beside a harder rung of its ladder (`validate_progressions.py`).
- Rank 5 takes 30 sets at the target, so the plan alone reaches it on 26 skills, the dead hang and the pseudo-planche push-up through a harder rung of their ladders. The rest comes from repeating blocks and the athlete's own workouts, by design. The skills help says so.

## Points (`Points`, `WorkoutFlow.points`)

- **Training.** Every counted practice and main set earns its reps times the difficulty of the movement done, 1 (easy) to 5 (extreme), from the movement library's grade (`CurriculumCatalog.movementGrades`; the easier option's grade when the athlete chose it; 1 for a movement without one).
  - A hold earns a rep for every 3 seconds (`Points.SECONDS_PER_REP`); a set done on each side counts both sides.
  - The number is what the athlete logged with "Did more or fewer?", up to twice the plan's (`Points.LOGGED_CAP`), else the plan's. A "max" set counts the logged reps, up to 50, and nothing unlogged; a practice window counts the logged hold, else its planned time, up to 180 s.
  - Warm-up, cool-down and readiness checks earn nothing; only sets that pass the 30% rule count.
- **Ranks.** Every rank a skill holds adds 100 times its difficulty (`Points.chevrons`): a push-up rank is 100, a full planche rank 500. A rank held through a harder rung counts too.
- **Saved per class.** A finished class keeps its points (`WorkoutSession.points`), synced with cloud progress, so a deleted own day keeps what it earned. Classes saved before 2.10.0 have none; their sets still count toward ranks, and so toward the ranks' points.
- **All time and this month** (`HomeActivity.pointsTotal`): every class's points plus the ranks'. This month's are the month's classes and the ranks reached since the month began (the ranks from the classes before it, against the ranks now). The month is the UTC month, as the leaderboard keys it (`Points.month`).
- **Where it shows.** Skills: the number alone, a tap away from the help. After a class: "Workout completed · +142 points". The leaderboard: both totals.
- **What a class earns**, following the program: a typical class is about 130 points in Level 1, 240 in Level 5 and 310 in Level 10; a ready workout 33 to 414, typically 100 to 220, so no single workout is worth farming. Over the whole program about four fifths come from training and a fifth from ranks.

## Display (`SkillsScreen.kt`, `SkillMedals.kt`)

Skills shows the points on a dark card and, below it, every family's ladder (the owner, 2.10.0: "only show the overall score as points, nothing else, and below the chevrons"). No tier, group scores, strongest families, rank count, legend, closest list or group filter.

Each skill is its figure on a rounded square, with its name and "Rank 3" (or "No rank yet") under it:
- **Outline:** shows the rung's grade, from green (easy) to red (extreme).
- **Rank shield:** a game-style insignia hanging from the square's foot: a peaked shield with a gold rim and a glossy graphite face. Rank 1 has one gold chevron band and Rank 2 two. Rank 3 has three stars and Rank 4 one big star under a stacked crest. Rank 5 has the big star with wings and a taller crest. The owner keeps the shields as they are.

## Exams

An exam tests one fresh set of every skill family the block trained, 12 to 15 tests from Level 4, in a fixed order: balance, the holds, the bent-arm strength, the basics, the trunk (review 6, `docs/CURRICULUM_PLAN.md`). Missing the exam's headline skill (`TrainingProgram.headline`), or three targets or more, offers to repeat the block (`ExamResult.offersRepeat`); any other miss is tested again at the next exam.

On an exam day, a target starts ticked only if all of the following hold (`WorkoutFlow.examTests`):
- every set was ticked;
- no set was done as the easier option;
- no logged number fell short of the plan.

The athlete can still change the answer.

## Leaderboard

- **Two boards** (the owner, 2.10.0): **All time**, shown first, and **This month**, each worldwide or for the athlete's country. Plus and Pro appear on them; the server can't check a tier, so the app only publishes for them.
- **The score document** `scores/{uid}`, version 4: `v`, `username`, `country`, `points` (all time, 0 to 100,000,000), `month` (the UTC month, "2026-10"), `month_points` (0 to `points`) and `updated_at` (the server's time). The app publishes it after a change, at most every 10 minutes (`Scores.shouldPublish`); a hidden profile takes it off.
- **Boards** (`firebase/functions/boards.py`): `all_world` and `all_<country>` rank `points`; `month_world` and `month_<country>` rank `month_points` of the documents whose `month` is the current one, so they start empty on the 1st. The function also rebuilds when the month turns with no score changed, and deletes boards it no longer builds (the family boards on its first version 4 run). Older versions are skipped until the app republishes.
- **Profiles.** Anyone can pick a username in Settings › Profile, unique ignoring case (`usernames/{key}`), without Google: an anonymous Firebase user holds it, and Connect Google links that user so the name stays on a new phone. `firestore.rules` lets any signed-in user, anonymous included, write `usernames`, its own `users/{uid}` and `scores/{uid}`; cloud progress (`users/{uid}/data`) stays Google only.
- **Deploy.** The rules and function must go out with the Play app that publishes version 4: `firebase deploy --only firestore,functions` from `streetgymnastics/firebase`, the owner's command. Until then the deployed function drops version 4 scores and the rules refuse them. GitHub builds have no Firebase configuration and are unaffected.
