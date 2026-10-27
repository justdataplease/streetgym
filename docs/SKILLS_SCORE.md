# Skills and the overall score (27 October 2026)

A pro-athlete review of the Skills tab found that skill points measured attendance, not ability. Credit came from the prescribed sets of any finished class, ticked or not. Readiness checks unlocked a skill just by attending, and "Complete level" taps unlocked skills too. Elite rungs showed about 85/100 before they had ever been trained. The leaderboard summed raw points, so it ranked volume. This revision changes what counts, splits each skill's points, and adds a weighted overall score.

## What counts

- Only the sets ticked in a finished class count (`WorkoutSession.credits`, built by `WorkoutFlow.credits`), and only in the practice and main sections.
- A class counts once it ran at least 40% of its planned minutes.
- Classes saved before ticks were kept count their prescription.
- Days marked complete, placed past, repeated or custom earn nothing (`Scores.credits`). Skills and the leaderboard use the same credits, so the two screens agree.
- An exercise can be switched to its easier option in either workout view. Its sets then count for the easier movement.
- A readiness check earns no points. After the class it asks "Did you hit it?", and a Yes counts as a passed test.

## Each skill's 100 points (`SkillBook`)

- **Prep, up to 50.** Weighted points from the rung's own sets, the easier rungs below it and the family's foundations. The target is what the schedule delivers up to the class that first prescribes the rung.
- **Practice, up to 50.** 25 points for each of the first two credited classes that trained the rung itself.

So following the program fills prep on the introducing class, and the second class that trains the rung brings it to 100. A rung worth less than 15 in the overall score is achieved at 100. A rung worth 15 or more is only "Trained" at 100 and needs a passed test.

A test is taken from the skill sheet, at most once per rung every 24 hours, or answered at a readiness check. Tests are kept in `SkillTestLog` and synced with cloud progress.

Achieving a rung achieves every easier rung of its family, compared by value, not by ladder position.

## Ladder fixes

- The pseudo-planche push-up is a planche rung.
- The pull-over and the Bulgarian split squat are foundations. Foundations never join a ladder.
- The handstand ladder runs: wall handstand, kick-up, exit, toe pull, balance attempts, freestanding handstand.
- The muscle-up ladder puts the jump muscle-up before chest-to-bar.
- Grades were rebalanced; Elite is now kept for the full front lever, straddle and full planche and the full one-arm pull-up.
- Four new top rungs sit past the program: the one-arm pull-up, one-arm push-up, freestanding handstand and V-sit.
- A set's points follow its rung's grade (10 to 20).

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

The score is built per family:
- A family counts its hardest achieved rung's value.
- It adds half the gap to its next rung, in proportion to that rung's 0–100 points. Training alone never reaches the next value.
- The overall is 1000 × the families' sum ÷ the sum of every family's top value. That sum is 767 today.

Tiers:

| Tier | Score |
|---|---|
| Beginner | 0–74 |
| Novice | 75–199 |
| Intermediate | 200–424 |
| Advanced | 425–649 |
| Elite | 650–849 |
| World-class | 850–1000 |

Following the program and passing each test as it comes:

| End of level | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| Overall score | 77 | 153 | 241 | 346 | 438 | 548 | 813 |

Attending every class without passing a test tops out at 262.

## Leaderboard

- The score document (version 2) holds `overall`, `families` (ten times each family's points) and `verified`.
- The board function (`firebase/functions/boards.py`) drops any document whose overall doesn't add up from its families. The family ceilings there must match the app's; `SkillBookTest` checks this.
- Boards: `overall_*`, plus one `family_<id>_*` board per family.
- `firestore.rules` caps the overall at 1000 and the families at 15.
- The rules and the function need a deploy before the new app version publishes scores.
