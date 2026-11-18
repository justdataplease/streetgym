# Street Gym for iPhone: full-functionality checklist

Every feature of the Android app (2.14.1, versionCode 58), one line each, in the order a user meets them: the iPhone app is done when every line is ticked.
`[x]` is on iPhone now (in `ios-streetgym`, checked in its code), `[ ]` is to build. Pointers name the Android file under `android-streetgym/app/src/main/java/com/justdataplease/streetgym/` (→ the iPhone file where it is built); **engine**: the rule is already in `android-streetgym/shared/`; **iPhone API**: the Apple framework it needs; **owner**: it can't be finished without the owner.
Written 18 Nov 2026; updated the same day after rounds 1 to 5.

## Count

| Area | Features | On iPhone | To build | Waiting on the owner |
|---|---:|---:|---:|---:|
| 1. Opening the app | 8 | 6 | 2 | 0 |
| 2. First launch: the strength test | 17 | 0 | 17 | 0 |
| 3. Today | 23 | 19 | 4 | 1 |
| 4. The class page | 11 | 11 | 0 | 0 |
| 5. Line-man animations | 12 | 12 | 0 | 0 |
| 6. Training a class: focus view | 18 | 18 | 0 | 0 |
| 7. Training a class: list view | 13 | 13 | 0 | 0 |
| 8. Finishing, repeats and exams | 15 | 15 | 0 | 0 |
| 9. Training › Levels | 19 | 19 | 0 | 0 |
| 10. Training › My workouts | 22 | 0 | 22 | 0 |
| 11. Training › Library | 11 | 0 | 11 | 0 |
| 12. Progress › History and the streak | 14 | 14 | 0 | 0 |
| 13. Progress › Skills | 11 | 11 | 0 | 0 |
| 14. Timer | 8 | 0 | 8 | 0 |
| 15. Training › Parks | 15 | 0 | 15 | 1 |
| 16. Progress › Leaderboard | 14 | 0 | 14 | 9 |
| 17. Settings | 22 | 21 | 1 | 0 |
| 18. Themes | 5 | 5 | 0 | 0 |
| 19. Plans and purchases | 22 | 0 | 22 | 8 |
| 20. Ads and consent | 11 | 0 | 11 | 4 |
| 21. Account, sign-in, cloud backups and restore | 17 | 0 | 17 | 17 |
| 22. Notifications and sounds | 6 | 2 | 4 | 0 |
| 23. Home-screen widget | 10 | 0 | 10 | 7 |
| 24. Sharing and progress analytics | 6 | 0 | 6 | 5 |
| 25. Accessibility and languages | 12 | 8 | 4 | 0 |
| 26. Anything else | 2 | 0 | 2 | 0 |
| **All** | **344** | **174** | **170** | **52** |

"Waiting on the owner" counts lines still to build whose last step needs the owner; most can be built and tested first with a test double (a `.storekit` file, Google's demo ad units, a made-up profile).

## 1. Opening the app

- [ ] Loading screen: a figure walks (one time in three, does pull-ups) over "Loading your training…" (ui/HomeActivity.kt renderLoading, ui/ParkSceneView.kt WALK and PULL; iPhone shows an empty page)
- [ ] Load error: the logo, "Training plan unavailable", its message and Try again (HomeActivity.renderLoadError; iPhone shows one plain line)
- [x] Five tabs, Today, Training, Progress, Timer, Settings, with their icons, in each theme's tab bar (HomeActivity.rebuildBottomNavigation, ui/ThemeDecor.kt → Shell.kt TabBar)
- [x] Title bar: the brand mark on a tab, ‹ Back on a nested screen, the title in the theme's display face (HomeActivity.buildShell, screenTitle → Shell.kt TitleBar)
- [x] Screens keep clear of the Dynamic Island and the home indicator (HomeActivity.applySystemBarInsets → App.kt)
- [x] Back swipe from the left edge on a nested screen (HomeActivity.navigateBack → BackSwipe.kt, in Compose: a drag from the left 24 dp, 64 dp to the right, goes back; off while a dialog shows)
- [x] App name "Street Gym", the icon, the launch colour (res/values/strings.xml → iosApp/project.yml, Assets.xcassets)
- [x] Progress kept on the phone, offline (data/progress/SharedPreferencesProgressRepository.kt → data/StoredProgress.kt in UserDefaults)

## 2. First launch: the strength test

- [ ] Shown on Today, in place of the brand card and the day card, while nothing is done, no class is in progress and the test isn't answered; any progress settles it for good (HomeActivity.askPlacement; **engine** Placement.shouldAsk)
- [ ] Header: "FIRST, A QUICK TEST", "How strong are you today?" and the warm-up prompt (ui/PlacementView.kt placementCard)
- [ ] Three counters, Pull-ups, Push-ups, Squats: a big number to type (up to 3 digits) with − and + under it (PlacementView.countTile)
- [ ] The keyboard's Next moves to the next counter; Done on the last runs Calculate (countTile)
- [ ] Form line: "Strict: chin over the bar · chest to a fist above the floor · thighs below parallel, heels down." (placementCard)
- [ ] Calculate, enabled once all three are filled; "Enter all three, then tap Calculate." until then (placementCard renderResult)
- [ ] Changing a count hides the result until Calculate is tapped again (placementCard)
- [ ] "Skip and start at Level 1" (placementCard)
- [ ] The start on tape: "YOUR START", "Level 2, day 22 · Bar Strength", tilted in Concrete and Wheatpaste (PlacementView.startTape; **engine** Placement.starts, start)
- [ ] Info card: From your skills, Opens with, Assumes you can do (the exam's targets), Closer to the next start ("3 more pull-ups → Level 2"), "Only a guide…" (placementCard; **engine** Placement.opening, assumed, better)
- [ ] "Start at …", and "Start from scratch" when the start isn't Level 1, day 1 (placementCard)
- [ ] "Already hold skills?" fold (+ / −): the level tests of Levels 3 to 9 the counts pass, each with its targets; a tap picks one, a second clears it (placementCard; **engine** Placement.SKILL_TESTS, meets, skillStart)
- [ ] A start past the tier is held to its last level: "Level N needs Pro, so you start at Level M, the last one your plan opens." (placementCard; **engine** Placement.capped)
- [ ] The page scrolls to the result after Calculate (placementCard)
- [ ] Confirming marks every earlier class done as placed, with no points or ranks (HomeActivity.startPlacement; **engine** Placement.skipped)
- [ ] Counts and the picked test kept while Today redraws (HomeActivity placementAnswers, placementSkill, placementShown)
- [ ] "Share my progress" note and switch close the screen (ui/ShareProgressView.kt shareProgressNote)

## 3. Today

- [x] Brand card: "DAILY CALISTHENICS" and "We choose the workout. You just train." (HomeActivity.renderDashboard → TodayScreen.kt)
- [ ] Park scene in the brand card: the figure walks in and does the day's three movements, holds the final skill and fades; three friends cheer; a new routine each local day (ui/ParkSceneView.kt STORY, ui/ParkStory.kt, which uses java TimeZone: port it)
- [ ] A tap on the park scene starts it again; so does coming back into view (ParkSceneView.replay, updateAnimationState)
- [ ] With a username, "Well done, <name>!" rises in a cloud as the final skill lands (ParkSceneView.username, BubbleStyle.CLOUD; test with a made-up name; **owner**: a real profile needs Firebase)
- [x] Today line: the section name, the streak's flame with its number, this week's seven days (HomeActivity.todayLine, ui/StreakUi.kt streakStrip → StreakViews.kt)
- [x] Flame lit once today's workout is done, unlit with its number, a dashed 0 once ended; trained days filled, a carried rest day pale, today outlined and underlined (ui/StreakFlame.kt, StreakUi.StreakWeekView → StreakViews.kt; **engine** Streak)
- [x] The week row starts on the region's first weekday (data/progress/StreakDays.kt snapshot → AppModel.kt LocalDays, NSCalendar.firstWeekday read in MainViewController)
- [x] Large text also gives the smaller flame and boxes (todayLine: width ≥ 360 and font scale ≤ 1.15 → TodayScreen.roomyStreak: a 320 dp row and the font scale)
- [x] A tap on the streak opens the streak sheet (§12) (HomeActivity.todayLine, StreakUi.showStreakSheet → TodayScreen, StreakViews.StreakSheet)
- [x] Day card head in each theme: Concrete's tape and big class number, Night Park's stencil and level chip, Wheatpaste's poster (ui/ThemeDecor.kt todayHeader → TodayHeader.kt)
- [x] Label over the title: "Repeat: …" during a repeat, "Repeating from Level N" or "Repeating for the exam" during a replay (renderDashboard → TodayScreen, AppModel.Today.label)
- [x] Class title and "Workout 12 of 100 • About 35 min • RPE 6" (renderDashboard, ui/DisplayText.kt programMeta → DisplayText.kt)
- [x] "How do you feel today?" Easier / Normal / Harder with its note (HomeActivity.intensityRow → TodayScreen.IntensityRow; **engine** Intensity)
- [x] Today's pick survives a restart and is Normal again the next day (ui/AppSettings.kt intensityToday → data/AppSettings.kt, the same keys)
- [x] Start workout, or Resume while the class is in progress (renderDashboard → TodayScreen)
- [ ] A repeat, a Library workout or an own workout in progress takes Today's card until it is finished or ended (renderDashboard canRun, repeating; the repeat does on iPhone, the other two come with them in round 7)
- [x] Complete day, with "Marks the day done without training. It earns no points or ranks." (renderDashboard → TodayScreen)
- [x] Complete day asks first: "Complete this day?", the class name and the note, Cancel / Complete (HomeActivity.requestCompleteDay → ClassScreen.CompleteDayDialog, on Today, the class page and the list)
- [x] A tap on the day card opens the class page (renderDashboard → TodayScreen)
- [x] A class in a level the tier doesn't open: Unlock with the tier's sticker; the card and Unlock open the offer (renderDashboard unlockButton → Kit.UnlockButton; the offer is its heading and line with Cancel until the plans of round 10)
- [x] Every class done: "Sequence complete" and its line (renderDashboard → TodayScreen)
- [x] Progress card: "12 / 1000 workouts" and a bar; a tap opens Progress (renderDashboard → TodayScreen)
- [x] Back on Today after midnight, the flame is drawn for the new day (HomeActivity.onResume streakDrawnOn → AppModel.refreshDay, on each return to the app and every 30 s on Today)

## 4. The class page

- [x] Title; "Level N • Workout 12 of 100 • About 35 min • RPE 6"; the description, or "A balanced session…" when it has none (HomeActivity.renderOverview → ClassScreen.kt)
- [x] "Easier" or "Harder" in that line when today's pick changes the class's strength work (renderOverview → ClassScreen; **engine** Intensity.scales)
- [x] "Easy / Moderate / Hard for you" badge in green, orange or red (renderOverview, ui/EffortUi.kt effortText, effortColor → ClassScreen, AppModel.effortOf; **engine** WorkoutEffort)
- [x] Safety line "Keep 2–3 reps in reserve and stop for sharp joint pain." (HomeActivity.safetyLine → ClassScreen)
- [x] "Safety notes ›" ends the safety line and opens the readiness note with the class's readiness and safety notes (HomeActivity.safetyLine → ClassScreen.SafetyLine)
- [x] Warm-up, Skill practice, Strength, Flexibility & cooldown: each exercise with its number, name, set count, notes (Safety and Easier option on their own lines) and every set (addExerciseSection, exerciseNotes, DisplayText.setPrescription → ClassScreen.ExerciseSection)
- [x] "Needs: pull-up bar + box" under an exercise that needs equipment (ui/EquipmentView.kt equipmentNeeds → DisplayText.equipmentNeeds; **engine** EquipmentNeeds)
- [x] Today's class ends with the intensity row, Start workout or Resume with the play icon, Complete day and its note (renderOverview → ClassScreen)
- [x] A class not reached yet shows only its name, "This daily plan is locked." and a greyed "Assigned later in the sequence" (renderOverview → ClassScreen)
- [x] A done class that can't be repeated now: "You completed this workout…" and a greyed "Workout completed" (renderOverview → ClassScreen)
- [x] A class in a locked level: name, meta and Unlock with the tier's sticker, no exercises (renderOverview → ClassScreen; Unlock opens the offer's heading and line until round 10)

## 5. Line-man animations

- [x] Side-view line figure from exercise_animations.json: fixed bone lengths, hands or feet pinned, a smooth coached rep (ui/ExerciseAnimationView.kt, MotionCurve → figure/Motion.kt, FigureDraw.kt, FigureRenderer.kt)
- [x] Front view beside the side view at one scale, captioned SIDE and FRONT, or END for a body seen end-on (ui/FrontView.kt, ExerciseAnimationView → figure/FrontView.kt, FigureRenderer.kt)
- [x] Bar, parallel bars, posts, wall and floor drawn with the figure; the hands in tape yellow on Concrete, the theme's light colour elsewhere (ExerciseAnimationView, FrontView, ui/UiKit.kt AppColors → figure/)
- [x] Phase label in the corner (Pull, Hold, Lower, Hang… "Hold · breathe", "Lower 3–5 s") and a progress strip (ExerciseAnimationView, res/values/strings_motion.xml → figure/)
- [x] Range marker that lights when the movement reaches it ("Chin over bar", "Chest to bar"…, 21 in all) (ExerciseAnimationView → figure/)
- [x] In the focus view's panel, for the set shown (FocusWorkoutView.setScreen → FocusScreen.SetPage)
- [x] On every exercise card of the class page and the list view (HomeActivity.exerciseAnimation → ClassScreen.ExerciseAnimation)
- [x] "Preview 1/3: <variation> - tap to change" under an exercise with no movement id and several variations (HomeActivity.exerciseAnimation → ClassScreen.ExerciseAnimation)
- [x] Moving tile: the clip in a small rounded tile, framed on the whole cycle, for the exercise picker, level rows and workout cards (ExerciseAnimationView.setBadgeStyle moving, EffortUi.WorkoutTile → ExerciseFigure.WorkoutTile, on the level rows; the picker and cards come with their screens)
- [x] Still tile holding the end pose, for the skill medals (setBadgeStyle, ui/SkillMedals.kt skillFigure → ExerciseFigure with a badge, on Skills' medals: SkillMedals.SkillMedal)
- [x] A workout's tile plays its hardest exercise, a moving one before a hold of the same grade, ringed in its effort colour (EffortUi.tileExercise → AppModel.tileOf; **engine** WorkoutEffort.hardest)
- [x] Reduced motion: a still pose; nothing runs off screen (ExerciseAnimationView, UiKit.systemAnimationsEnabled → LocalReduceMotion; **iPhone API**: UIAccessibilityIsReduceMotionEnabled, read at launch)

## 6. Training a class: focus view

- [x] Starting while another class is in progress asks "Another workout is active", Start this one / Cancel (HomeActivity.requestStart → Shell.kt)
- [x] A class that can't start now says "Finish today's class first." (requestStart → Shell.kt)
- [x] Opens on the first set neither done nor skipped; Resume after closing the app lands on the same set (ui/FocusWorkoutView.kt render → data/Training.kt)
- [x] Strip: the section, Finish, the class's sets drawn per theme (boxes, track, tally), "Set 7 of 27" (FocusWorkoutView.strip, ThemeDecor.SetProgressView → FocusScreen.kt Strip, SetProgress)
- [x] Exercise name with Details; "Set 2 of 3 · side 1"; the dose large (stencilled in Night Park); the variation and tempo cue (FocusWorkoutView.setScreen → SetPage)
- [x] Details sheet: set and dose, description, Tempo, Safety, Easier option with the readiness note, Start criteria, Close (FocusWorkoutView.showDetails → DetailsSheet)
- [x] "Did more or fewer?" opens "What did you do?": a number, Save, As planned, Cancel; the label then reads "Did 12 reps" or "Held 45 s" (ui/SetDone.kt → LogDialog, setDoneLabel)
- [x] Done · Next on a max set, a practice window or an easier option measured the other way asks for the number first (setScreen → SetPage)
- [x] "Easier option: X" swaps in the easier movement, "Doing the easier option: X" swaps back; numbers logged for the other movement are cleared (easierToggle, HomeActivity.switchEasier → Training.setEasier)
- [x] Without an easier movement, "Easier: <note>" opens Details (FocusWorkoutView.easierChip → SetPage)
- [x] Timed set: "Start 30 s" counts down in the timer face; a per-side hold then offers "Start side 2" (FocusWorkoutView.startHold, rounds → Training.startHold)
- [x] Back reopens the previous set and unticks it, greyed on the first set (FocusWorkoutView.back → Training.back)
- [x] Skip leaves the set for later (FocusWorkoutView.skip → Training.skip)
- [x] Rest fills the screen: "Rest", the countdown as large as fits, "Next: <exercise> · set N", +15 s, Skip rest (FocusWorkoutView.restScreen → RestPage)
- [x] No rest after the class's last set (FocusWorkoutView.done → Training.done)
- [x] Summary: "Class complete" or "End of class", "Started N min ago", "N sets skipped", Finish workout, Back, Do skipped sets (FocusWorkoutView.summaryScreen → SummaryPage)
- [x] End session, asking "End this session?", from the list view as on Android (HomeActivity.requestAbandon → WorkoutList)
- [x] The tab bar is hidden and the screen stays on (HomeActivity.renderFocus, FocusWorkoutView keepScreenOn → Shell.kt, MainViewController idleTimerDisabled)

## 7. Training a class: list view

- [x] A title-bar button switches Focus ↔ List during a workout; the choice is kept (HomeActivity.bindWorkoutViewAction, switchWorkoutView; AppSettings.workoutView → Shell.TitleBar, data/AppSettings.kt)
- [x] Status card: the repeat note on a repeat, the title, "Started N min ago", "N of M sets" and a bar (HomeActivity.renderActive → WorkoutList.kt)
- [x] Rest line in the card: "Ready for the next set", or "Rest • 45 sec" counting down (renderActive, scheduleRestUpdates → WorkoutList)
- [x] Complete day in the card with its note, except on a repeat (renderActive → WorkoutList)
- [x] Safety line with Safety notes › (renderActive → WorkoutList)
- [x] Section headers with "3/8" and a box that ticks or clears the whole section (renderActive sectionCheck → WorkoutList, Training.tickSection)
- [x] Exercise cards: name, line man, notes, the Needs line, the easier option toggle (renderActive → WorkoutList.ExerciseCard)
- [x] Set rows: a box, "Set N" and the prescription; a tap anywhere on the row ticks it (renderActive → WorkoutList)
- [x] Ticking a set starts its rest; the class's last set clears it (renderActive → Training.tick)
- [x] Ticking a max set, a practice window or an easier option measured the other way asks "What did you do?" (renderActive → WorkoutList)
- [x] The logged label under a credited set; a tap changes it (renderActive doneText → WorkoutList)
- [x] Finish workout and End session at the bottom (renderActive → WorkoutList)
- [x] The tab bar stays, so the Timer tab can be opened mid-workout (HomeActivity.showScreen, openRoot → Shell; the Timer itself is §14)

## 8. Finishing, repeats and exams

- [x] Finishing with sets left asks "Finish before every set?" (HomeActivity.requestFinish → FocusScreen)
- [x] "How hard did that feel?": RPE 1–10, the class's target picked first; Finish or Cancel (HomeActivity.askEffortAndFinish → EffortDialog)
- [x] Saving: only exercises that ran 30% of their planned time count, for skill credits, doses and points (askEffortAndFinish → Training.finish; **engine** WorkoutFlow.counted, credits, doses, points)
- [x] Note "Workout completed · +N points", with "· N day streak" when the class lit today's flame (askEffortAndFinish → Shell.kt)
- [x] After finishing, Progress opens, on the tab last picked (askEffortAndFinish → Shell.kt)
- [x] A done class offers "Repeat this day" near the top with the repeat note, and at the bottom after "You completed…", the intensity row and Focus · List (renderOverview, repeatButton → ClassScreen)
- [x] A repeat in progress: title "Repeat workout", Resume on its button, no Complete day, "Repeat saved · +N points"; the place in the program stays (screenTitle, renderActive, askEffortAndFinish → Shell, TodayScreen, WorkoutList)
- [x] Exam card on an exam class's page: what it asks, or "Last taken <date>: N of M targets reached.", "Not yet: …", "Passed on …" (HomeActivity.examCard → Exams.kt ExamCard; **engine** ExamLog)
- [x] After an exam class: "Exam: tick the targets you reached", fully ticked targets already ticked, Save results (HomeActivity.askExam → Exams.kt ExamDialog, Training.finish; **engine** WorkoutFlow.examTests)
- [x] Targets of switched-off skills or missing equipment are left out of it (askEffortAndFinish → Training.finish, read from Android's setting keys)
- [x] Every target reached: "Exam passed", "The next block starts with your next class." (askExam → Shell)
- [x] The headline target or three or more missed: "Not every target yet", Repeat N classes / Keep going (askExam, askRepeatBlock → Exams.kt RepeatBlockDialog; **engine** ExamResult.offersRepeat)
- [x] Repeat N classes: Today goes back to the first class after the previous exam and comes through the exam again (askRepeatBlock → AppModel.repeatBlock; **engine** LevelReplay, kept by StoredProgress under Android's names)
- [x] Otherwise "Exam saved: not every target yet. Those are tested again at the next exam." (askExam → Shell)
- [x] A repeated exam saves its results but offers no block repeat (askEffortAndFinish → Training.finish)

## 9. Training › Levels

- [x] Switch at the top: Levels / My workouts / Library / Parks, kept while the app runs (HomeActivity.renderLevels → TrainingScreen.kt, Shell; the other three tabs come in rounds 7 to 9)
- [x] Intro line over the levels (renderLevels → TrainingScreen)
- [x] Replay card: "Repeating from Level N" or "Repeating a block before its exam", "You stopped at …", Return to my furthest day (HomeActivity.replayCard → TrainingScreen.ReplayCard)
- [x] Ten level cards: number, name, the first sentence of its description, Plus or Pro beside the name when locked, "N of 100 complete", a bar (HomeActivity.levelCard → TrainingScreen.LevelCardView)
- [x] Concrete: the current level in tape yellow with a hard shadow, the levels ahead dashed (ThemeDecor.decorateLevel → TrainingScreen)
- [x] Night Park: a route line through the levels, a station by each: ✓ done, ringed current, open ahead (ThemeDecor.RouteRail → TrainingScreen.routeRail)
- [x] Wheatpaste: tilted posters, the current one blue, finished ones stamped DONE (decorateLevel, ui/ThemeDrawables.kt StampDrawable → TrainingScreen.doneStamp)
- [x] A tap on a level opens its classes, titled "Level N workouts" (levelCard, screenTitle → Shell, LevelScreen.kt)

### A level's classes

- [x] Header: name, the whole description, "N of 100 complete" (switched-off classes count), a bar (HomeActivity.renderWorkouts → LevelScreen.LevelHeader)
- [x] A locked level: its tier tag under the header, which opens the offer (renderWorkouts → LevelScreen; the offer's plans come in round 10)
- [x] Level 1 as the current level: "Already train? Complete Level 1 to start Level 2 now…" (renderWorkouts → LevelScreen)
- [x] Complete level on the current level: "Complete this level?" (Mark all 100 days… and its note), then Training (renderWorkouts → LevelScreen, AppModel.completeLevel)
- [x] "Go back to this level" on an earlier level: "Go back to Level N?", Go back; Today starts again at its first class (HomeActivity.requestLevelReplay → LevelScreen, AppModel.goBackTo; **engine** LevelReplay, DayAssignment)
- [x] A replay ends by itself once it reaches the furthest class (HomeActivity.levelReplay → AppModel.levelReplay; **engine** DayAssignment.replaying)
- [x] Rows: the moving tile of the hardest exercise ringed in its effort colour, the class number, the name on two lines, meta with "Easy for you" (ui/ProgramListAdapter.kt → LevelScreen.ClassRowView)
- [x] Row marks: ✓ done, NEXT, LATER, SWITCHED OFF (row greyed), Plus or Pro on a locked class not done (ProgramListAdapter → LevelScreen; switches set in Settings come in round 5)
- [x] Classes not reached hide their meta (ProgramListAdapter → LevelScreen)
- [x] Exam rows: "Exam", "Exam · passed", "Exam · not yet" (ProgramListAdapter → LevelScreen)
- [x] A tap on a row opens its class page; a locked class not done opens the offer (renderWorkouts → LevelScreen)

## 10. Training › My workouts

- [ ] Intro, "N of M saved and custom days" (3, 30 or 300 by tier), and the over-limit note (HomeActivity.renderMyWorkouts; **engine** Tier.savedDays, SavedLimit)
- [ ] List chips: Favorites, the athlete's lists, "+ New list" (with a lock on Free); the row scrolls sideways (ui/WorkoutListsUi.kt listChips)
- [ ] New list: a name dialog refusing a blank, too long or taken name; the new list opens (WorkoutListsUi.listNameDialog; **engine** WorkoutLists.nameProblem)
- [ ] New list on Free opens the Plus offer, at Plus's 3 lists the Pro offer, at Pro's 10 "You've made the 10 lists of Pro…" (HomeActivity.roomForAnotherList; **engine** SavedLimit.canAddList)
- [ ] An open list: Rename list, Delete list ("Only the list goes…"), its days, "No days in this list yet…" (renderMyWorkouts, listActions)
- [ ] On Free the lists are kept but hidden, and come back with Plus (renderMyWorkouts; **engine** LibraryState)
- [ ] Create a workout opens the builder; on Free it wears the Plus sticker and opens the offer (renderMyWorkouts, tierButton; **engine** Tier.customDays)
- [ ] Your workouts and Saved days, each with "Nothing here yet." when empty (renderMyWorkouts)
- [ ] Workout cards: tile, name, "Your workout · 5 exercises · About 20 min · Easy for you" (HomeActivity.libraryCard)

### The heart

- [ ] Heart at the top right of a class page: outline, or filled red with a pop when saved (HomeActivity.heartButton)
- [ ] Free: a tap saves to Favorites ("Saved to Favorites") or removes ("Removed from My workouts") (heartButton)
- [ ] At the limit: the Saved offer ("You've used all 3 saved and custom days"); on Pro "You've reached the 300…" (HomeActivity.roomForAnother)
- [ ] Plus and Pro: "Save to lists": Favorites always ticked, each list with its day count, a full list refused with a note, a name field with Add, "Remove from My workouts" for a saved day, Save ("Saved to Favorites and Travel", "Lists saved") (WorkoutListsUi.showListPicker; **engine** SavedLimit.canAddToList)
- [ ] An own workout's heart only picks lists, and Free shows none on it (renderOverview, heartButton)

### Own workouts

- [ ] Builder "Your workout": a name field of up to 60 characters (HomeActivity.renderEditDay)
- [ ] Exercise cards "1. Name", "3 × 8 reps · rest 60 s", with Up, Down, Sets and Remove (renderEditDay, prescription)
- [ ] Add exercise: search every movement of the program; rows with a moving tile, category · equipment or "No equipment"; "No exercise matches." (HomeActivity.pickExercise, PickerRow; **engine** ExerciseLibrary.search)
- [ ] Sets dialog: Sets, Reps / Seconds, the amount and the rest; 1–20 sets, 1–600, rest 0–600 (HomeActivity.editSets)
- [ ] Save workout needs a name and one exercise, then opens the workout's page (renderEditDay)
- [ ] Own workout page: "Your workout · N exercises · About N min", effort, safety line, Edit workout, Delete workout ("Delete this workout?"; its history stays; ends it if in progress) (renderOverview)
- [ ] Starts any time, at Normal, with every skill and equipment on; never moves the program on (HomeActivity.canStart, intensityFor, leftOutBy; **engine** CustomDays)
- [ ] A deleted one reads "Deleted workout" in History (HomeActivity.historyRow)

## 11. Training › Library

- [ ] Intro: "Ready workouts for days off the program…" (HomeActivity.renderReadyWorkouts)
- [ ] Effort chips: Any effort, Easy / Moderate / Hard for you, each in its colour when picked (renderReadyWorkouts, WorkoutListsUi.filterChips; **engine** WorkoutEffort)
- [ ] Grade chips: All levels, Beginner, Intermediate, Advanced, Master, Elite (renderReadyWorkouts)
- [ ] Focus chips: Any focus, the groups and the skill families that have workouts (renderReadyWorkouts, READY_FOCUS)
- [ ] "N workouts" with Clear filters when filtered; "No workout matches. Try fewer filters." (renderReadyWorkouts)
- [ ] Workouts under their group headings; Aches & posture opens with its doctor-first note (renderReadyWorkouts)
- [ ] Cards: tile, name, the Pro tag below Pro, "Intermediate · 6 exercises · About 30 min · Moderate for you", the description (libraryCard, readyMeta)
- [ ] Neck at the Desk, Upper Back Reset, Chest Opener and Low Back Basics open on every tier, untagged (HomeActivity.readyLocked; **engine** TrainingProgram.free)
- [ ] Below Pro, a Pro workout opens the Ready offer, and its page shows only the name, meta and Unlock (libraryCard, renderOverview)
- [ ] Ready workout page: meta, effort, description, safety line, exercises, heart, Start workout; no Complete day, no intensity row (renderOverview)
- [ ] Runs as written, any time: Normal, every skill on; earns points and ranks; never completes a class (HomeActivity.canStart, switchesFor; **engine** ReadyWorkouts)

## 12. Progress › History and the streak

- [x] Progress switch: Skills / History / Leaderboard, kept while the app runs (HomeActivity.renderProgress → ProgressScreen.kt; the Leaderboard tab waits for round 13)
- [x] Month card: month and year, ‹ › and a sideways swipe, from the first month with history to this one (ui/HistoryCalendarView.kt → HistoryScreen.kt; data/progress/HistoryCalendar.kt → data/HistoryCalendar.kt on kotlinx-datetime, pinned to java.util.Calendar by HistoryCalendarTest)
- [x] "8 workouts this month" or "5 workouts in October" (HistoryCalendarView.showMonth → HistoryScreen)
- [x] This month's streak line: a small flame, "12 DAY STREAK" on tape and "Best 20", or "Best streak: N days"; a tap opens the sheet without See history (StreakUi.streakLine → StreakViews.StreakLine)
- [x] Weekday names starting on the region's first weekday (HistoryCalendarView → HistoryScreen, NSCalendar.firstWeekday)
- [x] Days: trained disc, pale disc for marked only, ring for today, halo for the picked day, a small ring for a repeat, a diamond for an own workout, faint future days that can't be tapped (HistoryCalendarView.DayCell → HistoryScreen.DayCell)
- [x] Legend: Workout, Repeat, Your workout, Marked complete (HistoryCalendarView.legend → HistoryScreen.Legend)
- [x] A tap on a day lists only its classes under "Wednesday, November 5" with "All of November ›"; tapping it again brings the month back (HistoryCalendarView.pick, bindPanel → HistoryScreen.HistoryList)
- [x] The month's classes under the grid, newest day first; more than 3 marked on one day read "N days marked complete" (bindPanel → HistoryList)
- [x] Empty: "Finish a workout and its day is marked here.", "No workouts this month.", "No workouts on this day." (bindPanel → HistoryList)
- [x] Row: the class name, "Completed Nov 5, 2026, 7:30 PM • 42 min", "Repeat · …", "· Harder", "Placed by your test · …", "Marked complete · …"; a tap opens the class (HomeActivity.historyRow → HistoryScreen.HistoryRow, ProgressText.historyDate; "Deleted workout" for an own day that is gone)
- [x] The month and picked day stay when coming back from a class (HistoryCalendarState → HistoryScreen.HistoryCalendarState, kept by Shell)
- [x] Streak sheet: a big flame, "12 day streak", "Streak ended" or "No streak yet", "Best: N days", today's line ("Done for today." …), the week row, the rule, See history, Close (StreakUi.showStreakSheet → StreakViews.StreakSheet; **engine** Streak)
- [x] See history opens Progress › History (HomeActivity.todayLine → Shell)

## 13. Progress › Skills

- [x] Points card: "POINTS" and the total; a tap explains points and ranks (ui/SkillsScreen.kt skillsContent → SkillsScreen.kt, AppModel.points; **engine** Points)
- [x] Ranks legend: the five shields named Rank 1 to Rank 5; a tap explains them (SkillsScreen.rankLegend, SkillMedals.RankBadgeView → SkillsScreen.RankLegend, SkillMedals.RankBadge)
- [x] "How do points and ranks work?" with the rules, OK (SkillsScreen.showSkillsHelp → SkillsHelpDialog; Android's \" read as a quote, a new rule of the string copy)
- [x] A ladder per family: its name and its medals scrolling sideways (skillsContent → SkillsSection; **engine** SkillBook, SkillProgress)
- [x] Medal: the figure on a tile outlined by difficulty, green to red; the rank shield on its bottom-right corner; Elite on a night tile (ui/SkillMedals.kt skillMedal, skillFigure → SkillMedals.SkillMedal)
- [x] Rank shields: one band, two bands, three stars, a big star, a winged star with a crest (SkillMedals.RankBadgeView → SkillMedals.RankBadge)
- [x] Five blocks to the next rank under the medal, then the name (SkillsScreen.skillCell, SegmentBar → SkillCell, SegmentBar)
- [x] Plus or Pro on the top edge of a skill first taught in a locked level, until it has a rank (skillCell, lockFor → SkillCell, AppModel.lockFor)
- [x] Skill sheet: the movement playing, the medal, the name, "<family> ladder · rung 3 of 6", Part of Plus/Pro, Difficulty, Rank, Held through, Sets ticked, Best set, Next with what it takes and a bar (SkillsScreen.showSkillSheet → SkillSheet)
- [x] Skill sheet below: every rank with ✓ and "10 sets of 12+ reps", What counts, On schedule, Builds on, Next rung, Close (showSkillSheet → SkillSheet)
- [x] Ranks and points count trained classes only, never placed or marked ones (HomeActivity.skillProgress, leaderboard/Leaderboard.kt Scores.credits → AppModel.skillProgress, Scores)

## 14. Timer

- [ ] Timer / Stopwatch / Alarm, the last one kept (ui/TimingPanel.kt, timer/TimingStore.kt)
- [ ] Timer: big digits of even width; a tap sets hours, minutes and seconds; "Choose a duration" for none (TimingPanel.durationPicker, tabular)
- [ ] Timer: Start, Pause, Resume and Reset; the line under it says Counting down, Time's up, Paused or "Tap the time to change it" (TimingPanel.refresh; timer/TimingState.kt Countdown)
- [ ] Presets 30 sec, 1 min, 3 min, greyed while it runs (TimingPanel.build)
- [ ] A running timer or stopwatch carries on with the app closed (TimingStore)
- [ ] Stopwatch: hundredths, Running / Paused / Ready, Start / Pause / Resume, Lap / Reset, laps newest first, up to 100 (TimingPanel; TimingState Stopwatch)
- [ ] Alarm: the time in the phone's 12- or 24-hour format; a tap picks it; "Wed, 5 Nov · One time"; Set alarm / Turn off (TimingPanel.alarmPicker; TimingState.nextAlarm)
- [ ] The alarm follows a change of clock or time zone (timer/TimingAlerts.kt TimingReceiver)

## 15. Training › Parks

- [ ] Free: "More than 70,000 outdoor workout parks…", "Example: parks in Athens", a still map of 12 Athens parks under the layer with Plus and Unlock; a tap opens the Parks offer; a list of five where the map can't start (HomeActivity.renderParksExample; data/parks/Parks.kt EXAMPLE)
- [ ] Plus: "Loading parks…", then a Map / List switch, kept (HomeActivity.renderParks)
- [ ] Search: "City or park name" and Search; every word must match, nearest first once located (HomeActivity.searchParks, Parks.search)
- [ ] Map: OpenFreeMap tiles, dark on Night Park; green pins in clusters with counts ("26k"); a tap on a cluster zooms in (ui/ParksMap.kt; **iPhone API**: MapLibre iOS)
- [ ] A tap on a pin: a card with its name, distance and Directions (ParksMap.showCard)
- [ ] Location button: a blue dot with the 5 nearest parks in view; the button is grey until located (ParksMap.showNearby; **iPhone API**: CoreLocation, approximate, with its Info.plist text)
- [ ] The map opens on the athlete: location is asked once, then only from the button (renderParks)
- [ ] A search on the map frames the matches; one match opens its card; none says "No park matches." (searchParks, ParksMap.focus)
- [ ] Under the map: "© OpenStreetMap contributors. Your location isn't sent.", and the map's ⓘ credit (renderParks)
- [ ] List: "73,303 workout parks worldwide…", Parks near me, "Nearest to you" or "Matches for “x”", "Tap a park for directions…", rows with "350 m away" or "2.4 km away", the OpenStreetMap credit (renderParks)
- [ ] A tap on a park, or Directions, opens the phone's maps app at it (HomeActivity.openPark; iPhone: Apple Maps link)
- [ ] "Finding your location…", "Couldn't get your location. Search by city instead.", "Location is off for Street Gym…" (HomeActivity.locateForParks)
- [ ] The last known fix shows at once; a fresh one follows when it is over 10 minutes old (locateForParks)
- [ ] Leaving the tab keeps the map, so coming back shows it at once (HomeActivity.setBody)
- [ ] Parks ship with the app (assets/spots.tsv); a weekly check takes the quarterly file when it isn't far smaller (data/parks/ParksUpdater.kt; **owner**: parks_url comes with the Firebase iPhone setup)

## 16. Progress › Leaderboard

- [ ] Intro: "Points from your training, all time and this month, worldwide and by country." (HomeActivity.renderLeaderboard)
- [ ] Free: a board's shape (20 grey rows) under the layer with Plus and Unlock, filling the screen (HomeActivity.leaderboardSample, lockedLayer)
- [ ] A build without Firebase: "The leaderboard isn't available in this build." (renderLeaderboard)
- [ ] All time / This month (renderLeaderboard; leaderboard/Leaderboard.kt Boards.id; **owner**: Firebase iPhone app, as for every board line)
- [ ] World / 🇬🇷 Greece, when the profile or the phone's region gives a country (renderLeaderboard; **owner**: Firebase)
- [ ] Own line: "You're #N · X points", "You're in the top N% · X points", "Train to earn points on this board." (renderLeaderboard, Boards.standing; **owner**: Firebase)
- [ ] Own line without a visible profile: "Pick a username in Settings…", "Choose a username and a country…", "You're hidden from the leaderboard…" (renderLeaderboard; **owner**: Firebase)
- [ ] Top 100 rows: "#1", flag and username, points; the athlete's own row highlighted (renderLeaderboard; Apple 1.2: a filter for usernames and a way to report and block one; **owner**: Firebase)
- [ ] "No one is on this board yet." (renderLeaderboard; **owner**: Firebase)
- [ ] "1,234 players · updated <date> · boards refresh every 6 hours" (renderLeaderboard; **owner**: Firebase)
- [ ] "Loading the leaderboard…", "Couldn't load the leaderboard. Check your connection."; a board stays on the phone 6 hours (leaderboard/LeaderboardRepository.kt; Boards.fresh; **owner**: Firebase)
- [ ] The line on how points are earned, under the board (renderLeaderboard)
- [ ] Plus and Pro with a visible profile publish their score after a finished class, at most every 10 minutes; hiding takes it off; Reset publishes from zero (HomeActivity.publishScore; Scores.shouldPublish; **owner**: Firebase iPhone app)
- [ ] This month's board starts again on the 1st, UTC (**engine** Points.month)

## 17. Settings

- [x] Order: Profile (§21), Theme (§18), Plus and Pro (§19), During your workout, Skills in your classes, Equipment you have, Your data (§24), Start over, Train safely, About (HomeActivity.renderSettings → SettingsScreen.kt; Profile, Plus and Pro and Your data take their places with rounds 12, 10 and 13, Ad privacy with round 11)
- [ ] Simulate tier, debug builds only: Default / Free / Plus / Pro (renderSettings; billing/Entitlement.kt setSimulatedTier)

### During your workout

- [x] Keep screen awake, on by default (AppSettings.keepScreenAwake; Android's focus view keeps the screen on whatever it says → SettingsScreen.kt, WorkoutScreen)
- [x] Rest timer sound, on by default: the beep at the end of a rest and of a timed set (AppSettings.soundCues → SettingsScreen, Training)
- [x] Workout view: Focus · List, also above Start on a class page (FocusWorkoutView.workoutViewSetting → ClassScreen.WorkoutViewRow, in Settings too)

### Skills in your classes

- [x] The intro, and a row per skill: its name, what is on (All steps on, Switched off, "Switched off · X stays", "Up to X") and a switch (ui/SkillSwitchesView.kt skillSwitchesCard → SettingsScreen.SkillSwitchRow; **engine** SkillSwitches, SkillProgressions.switchable)
- [x] A tap on the row: the skill's sheet with its top rung playing, the intro, every rung as "Step N" with a switch or "Always on: a basic" and "On the way: …"; off takes the harder rungs, on brings the easier back; Done (SkillSwitchesView.showFamilySwitches → FamilySwitchesSheet; **engine** SkillProgressions.LINES)
- [x] In the class page and list view, a switched-off exercise is greyed with "Switched off · Handstand" (HomeActivity.addExerciseSection, renderActive, leftOutBy → ClassScreen, WorkoutList, AppModel.leftOutBy)
- [x] Doing it anyway asks "Handstand is switched off" … Continue; it then counts in this workout (HomeActivity.askSwitchedOff → FocusScreen.SwitchedOffDialog, AppModel.allowAnyway; forgotten when the class is started afresh, as Android's offAllowed)
- [x] The focus view leaves those sets out; its summary says "N sets left out: switched off in Settings" with "Do them anyway" (HomeActivity.renderFocus, FocusWorkoutView.summaryScreen → Training.state, FocusScreen.SummaryPage; **engine** WorkoutFlow.without)
- [x] A class with nothing left, by skills or equipment, counts as passed: Today steps over it, its row reads SWITCHED OFF, its page says so (HomeActivity.passedIds, renderOverview → AppModel.passed, switchedOff, ClassScreen; as on Android the page shows the note where it opens in full: in a replay or to inspect; **engine** NextClass.passed)
- [x] Skills: the medal greyed and "Switched off"; its sheet says so (SkillsScreen.skillCell, showSkillSheet → SkillCell, SkillSheet, AppModel.skillOff)
- [x] Own and ready workouts run as written, whatever is switched off (HomeActivity.switchesFor, leftOutBy → AppModel.leftOutBy; the workouts themselves come in rounds 7 and 8)

### Equipment you have

- [x] The intro and seven switches, Pull-up bar, Low bar, Parallel bars, Box, bench or chair, Wall, Pole or stall bars, Resistance band, each with its hint and exercise count, or "Switched off: classes leave these out"; a tap on the row flips it (ui/EquipmentView.kt equipmentCard → SettingsScreen.EquipmentRow; **engine** Equipment, EquipmentSwitches)
- [x] Exercises that need it are greyed with "Switched off · Pull-up bar"; doing one anyway asks first with the equipment message (HomeActivity.leftOutBy, askSwitchedOff → AppModel.leftOutBy, SwitchedOffDialog)
- [x] Skills whose movement needs it grey out (HomeActivity.lacksEquipment → AppModel.lacksEquipment)

### Start over, Train safely, About

- [x] Start over: its text (with an account: "Your leaderboard points and any cloud copy reset too."), Reset progress, "Reset all progress?", Reset (HomeActivity.confirmReset → SettingsScreen, ResetDialog; the account's words come with the account, round 12)
- [x] Reset erases classes, history, points, ranks and exams, keeps My workouts, the theme and purchases, says "Progress reset. Choose where to start." and brings the strength test back (confirmReset, SharedPreferencesProgressRepository.reset → AppModel.reset, StoredProgress.reset with "reset_at"; placement_done is cleared under Android's key, and the test itself shows once round 6 builds it)
- [x] Train safely card (renderSettings → SettingsScreen)
- [x] About: "Street Gym: Daily Calisthenics", its text, "Version 1.0" (renderSettings → SettingsScreen; the version from the Info.plist's CFBundleShortVersionString)
- [x] Ten taps on the version: "N taps to developer settings", then Developer settings with Inspect all days, Hide developer settings, Done (ui/DeveloperMenu.kt → SettingsScreen.VersionTaps, DeveloperDialog)
- [x] Inspect all days: any class opens read-only with the "Inspect only" badge and note, nothing to start or complete (HomeActivity.renderOverview, renderWorkouts → ClassScreen, LevelScreen, AppModel.inspects)

## 18. Themes

- [x] Concrete & Tape (the default), Night Park (dark) and Wheatpaste, with their colours, fonts, corners, outlines and page grain on the screens built so far (ui/StreetTheme.kt, ThemeDecor.kt, ThemeDrawables.kt → theme/StreetTheme.kt, Kit.kt)
- [x] Dialogs, sheets and notes in each theme's style (Android AlertDialog themes → Overlay.kt)
- [x] Settings › Theme: three rows, each with a swatch, its name in its own face, a summary, and the one selected (ThemeDecor.themePicker → SettingsScreen.ThemePicker)
- [x] The picked theme is saved and every screen follows at once (AppSettings.theme → data/AppSettings.kt, "streetgym_settings.theme"; an old backup's dark switch opens on Night Park)
- [x] Status bar dark on the light themes, light on Night Park (HomeActivity.configureWindow → WorkoutDevice.statusBar, MainViewController: the window's overrideUserInterfaceStyle; type-checked on Windows, seen only on the Mac)

## 19. Plans and purchases

- [ ] Settings panel "Plus and Pro" with the current plan ("You're on Free: Levels 1–3, with ads." …) (ui/TiersPanel.kt)
- [ ] Plus: the store's price, "one payment, for life", the features, Get Plus, "Plus is yours." (TiersPanel; **iPhone API**: StoreKit 2; **owner**: product `plus` in App Store Connect; test first with a .storekit file)
- [ ] Pro: the Pro for life price, the features, Get Pro for life, "Or €29.99 a year", the renewal note naming the App Store, "Pro is active." or "Pro is yours for life." (TiersPanel; StoreKit 2; **owner**: `pro_life` and the yearly subscription; Apple 3.1.2: price, period, Terms of Use and Privacy Policy links)
- [ ] Manage subscription while Pro yearly runs (TiersPanel; **iPhone API**: StoreKit showManageSubscriptions; **owner**: sandbox check)
- [ ] Buttons wait for the store's price; "Connecting to …", "purchases are unavailable" (TiersPanel.render; **owner**: real products for the last check)
- [ ] Pending payment: "Payment pending. It unlocks when … confirms it." (TiersPanel; StoreKit pending, Ask to Buy; **owner**: sandbox check)
- [ ] Restore purchases: "Pro restored…", "Plus restored…", "No purchase found…", "Couldn't reach…" (TiersPanel; Apple requires the button; **iPhone API**: AppStore.sync; **owner**: sandbox check)
- [ ] Redeem a promo code (TiersPanel.askPromoCode; iPhone: App Store offer codes in StoreKit's redemption sheet; **owner** makes the codes)
- [ ] Ownership cached for offline use; only a refund or a lapsed subscription takes it away; nothing is deleted when a plan ends (billing/Entitlement.kt reconcileEntitlement; **iPhone API**: Transaction.currentEntitlements; **owner**: sandbox refund and lapse)
- [ ] A purchase, refund or restore redraws the open screen and removes the ad cards at once (HomeActivity.entitlementChanged, ads/ManagedNativePlacement.kt)
- [ ] Gates: Levels 4–6 Plus, 7–10 Pro; Plus and Pro without ads; saved days, lists, own workouts and ready workouts by tier (**engine** LevelAccess, Tier, SavedLimit)
- [ ] Offer dialog: the tier badge, the heading and body, only the plans that unlock it, Cancel (HomeActivity.showPaywall, TiersPanel paywall)
- [ ] Offer "Level N is part of Plus" with the lever, planche, flag and handstand line (TiersPanel Paywall.Level)
- [ ] Offer "Level N is part of Pro" with the Levels 7 to 10 line (TiersPanel Paywall.Level)
- [ ] Offer "Ready workouts are part of Pro" with its line (TiersPanel Paywall.Ready)
- [ ] Offers "Parks near you are part of Plus" and "The leaderboard is part of Plus" (TiersPanel Paywall.Parks, Leaderboard)
- [ ] Offers "You've used all N saved and custom days" and "Your own workouts are part of Plus" (TiersPanel Paywall.Saved, Custom)
- [ ] Offers "Your own lists are part of Plus" and "You've made all 3 lists of Plus" (TiersPanel Paywall.Lists)
- [ ] Offer "Remove ads" with "Plus ends the ads with one payment…" (TiersPanel Paywall.Ads)
- [ ] Unlock: the main button with the tier's tilted sticker on its corner (HomeActivity.unlockButton, tierButton)
- [ ] Locked layer: the content dimmed, Unlock centred on it, a tap anywhere opens the offer (HomeActivity.lockedLayer)
- [ ] Review code: hold the About title 2 s, type the code, Pro for 30 days (ui/ReviewCodeDialog.kt, billing/ReviewAccess.kt; Apple 2.3.1: leave it out on iPhone, reviewers buy in the sandbox)

## 20. Ads and consent

- [ ] Native ad card closing every page on Free: Today, Levels, My workouts, Library, the Parks list, a level's classes, a class page, the list workout, the builder, Skills, History, Leaderboard, Timer, Settings (HomeActivity.endAd; **iPhone API**: Google Mobile Ads SDK, GADNativeAdView in UIKitView; **owner**: AdMob iPhone app and units)
- [ ] Cards inside pages: a level's classes after the third, Skills after the fourth ladder, a class page and the list workout between sections (renderWorkouts AdRowAdapter, skillsContent, addExerciseSection, renderActive)
- [ ] No card on the Parks map or in the focus view (renderLevels, renderFocus)
- [ ] The card: theme colours, round corners, "Ad", advertiser, icon, headline, body, media, the button and AdChoices (monetization/NativeAds.kt render)
- [ ] Cards on workout pages wait for consent instead of raising the form (ads/ManagedNativePlacement.kt)
- [ ] Full-screen ad on Free at most every 5 minutes of use, at the next change of screen or tab or a finished section (ads/WorkoutAdBreak.kt, AdBreakClock.kt; **owner**: interstitial unit)
- [ ] Never in the first 10 s after opening, during a rest, while a timer, stopwatch or alarm runs, over an offer or consent form, or between a workout and its Timer; missed breaks don't add up (AdBreakPolicy, HomeActivity.adBreakNow, openRoot)
- [ ] "♥ REMOVE ADS" in the title bar of every screen but a workout, on Free; it opens the Remove ads offer (HomeActivity.toolbarRemoveAds)
- [ ] Consent form before the first ad request; ads follow the answer (monetization/NativeAds.kt updateConsent, advance; **iPhone API**: UMP SDK, then the ATT prompt (NSUserTrackingUsageDescription); ads still show without tracking; **owner**: iPhone consent message)
- [ ] Settings › About › Ad privacy: Google's privacy choices, or "No ad privacy choices are available right now." (NativeAds.showPrivacy; **owner**: the consent message)
- [ ] Google's demo units in debug builds (README, build.gradle.kts)

## 21. Account, sign-in, cloud backups and restore

- [ ] Settings › Profile appears only in a build with Firebase (account/Cloud.kt available; **owner**: Firebase iPhone app and GoogleService-Info.plist)
- [ ] No profile: "Pick a username for the leaderboard. No sign-in needed." and Choose a username, which signs in anonymously; "Couldn't reach the server…" offline (ui/AccountSettingsView.kt pickUsername; Cloud.signInAnonymously; **owner**: Firebase, as for every line here)
- [ ] Profile dialog: username (3–20 letters, digits, _ or .), country with flags (the phone's region first), Save; "Use 3–20…", "That username is taken.", "Couldn't save…" (AccountSettingsView.editProfile; leaderboard/Leaderboard.kt Usernames, Countries; account/Profile.kt Profiles.save; Apple 1.2: filter names; **owner**: Firebase)
- [ ] With a profile: flag and username, the country, "Show me on the leaderboard" switch, Edit profile (AccountSettingsView.render; **owner**: Firebase)
- [ ] Free sees "The leaderboard shows Plus and Pro members." (render; **engine** Tier.account; **owner**: Firebase)
- [ ] Connect Google with the tier's line (Pro: username, progress and My workouts; others: the username) (render, account/GoogleAccount.kt; **iPhone API**: GoogleSignIn, Firebase Auth; Apple 4.8: Sign in with Apple beside it; **owner**: iPhone OAuth client, Apple provider)
- [ ] Connecting keeps the same user; a Google account that has its own takes over, releases this phone's profile and claims the name again if it has none (AccountSettingsView.connect; **owner**: Firebase)
- [ ] Without Google: "Without Google, your username stays on this phone only." where sign-in isn't set up; Delete profile ("Delete your profile?"), which frees the name (AccountSettingsView.confirmDeleteProfile, Profiles.release; **owner**: Firebase)
- [ ] Connected: "Google: name@gmail.com" (render; **owner**: Firebase)
- [ ] Pro: "Progress saved to your account · synced <date>" or "Progress not synced yet.", Sync now, "Couldn't sync…" (render; account/CloudSync.kt; **owner**: Firebase)
- [ ] Free and Plus, connected: "Your progress stays on this phone. Cloud backups and restore are part of Pro." (render; **owner**: Firebase)
- [ ] Sign out, and "Signing out keeps everything on this phone." (GoogleAccount.signOut; **owner**: Firebase)
- [ ] Delete account: "Delete your account?", sign in again, then cloud progress, the My workouts copy, score, username and the user go; "Account deleted." (AccountSettingsView.confirmDelete; Apple 5.1.1(v) requires it in the app; **owner**: Firebase)
- [ ] Pro backup and restore: completed days, history with every set, exams and My workouts merge both ways with the account, so a new phone gets them (CloudSync; **engine** ProgressMerge, LibraryCodec; **iPhone API**: Firebase iOS SDK, Firestore; **owner**)
- [ ] Sync a few seconds after a change and at most every 6 hours on opening; what arrives redraws Today, Training and Progress (HomeActivity.cloudChanged, afterSync; **owner**: Firebase)
- [ ] A plan that ends keeps the profile; nothing syncs or publishes, and the cloud copy waits (HomeActivity.cloudChanged, publishScore; **owner**: Firebase)
- [ ] App Check on every Firestore call (Cloud.firestore; iPhone: App Attest; **owner** registers it)

## 22. Notifications and sounds

- [x] End of a rest: a beep when Rest timer sound is on (HomeActivity.onRestFinished → Training.tick, MainViewController's AudioToolbox system sound; a local notification with the same beep for a rest that ends with the app in the background, shown once alerts are allowed: Android asks only from the Timer's Enable alerts, so the iPhone asks there too, round 8)
- [x] End of a timed set: a beep (Rest timer sound) and a vibration (FocusWorkoutView host.holdFinished, TimingAlerts.tone, vibrate → Training.tick, MainViewController: AudioToolbox and kSystemSoundID_Vibrate; the vibration is checked on a real iPhone, as the Simulator doesn't vibrate)
- [ ] Timer's end and the alarm in the background: an alert "Time's up" or "Alarm" with sound, vibration and Stop (timer/TimingAlerts.kt notify; **iPhone API**: UserNotifications, or AlarmKit on iOS 26 to ring like an alarm)
- [ ] In the app: a "Time's up" or "Alarm" dialog with Stop and a beep (HomeActivity.timingTick)
- [ ] A tap on the alert opens the Timer tab (TimingAlerts.open, HomeActivity.onNewIntent)
- [ ] Alerts off: "Background alerts are off. Enable alerts" under the Timer and Alarm, and "Enable alerts" on the alarm's button, ask for permission or open Settings (TimingPanel alertsHint, HomeActivity.enableTimingNotifications; iPhone has no exact-alarm switch, so "Enable alarms" goes)

## 23. Home-screen widget

- [ ] "Next class" widget, four cells by one, resizable sideways; a compact card on short cells (res/xml/widget_next_class_info.xml, widget/WidgetRenderer.kt; **iPhone API**: WidgetKit)
- [ ] Card: NEXT on tape, "LEVEL 4 · CLASS 20", the class's role as the headline in the theme's display face, the lead movement (a recovery day's theme, an exam's "N targets") and "44 min" (WidgetRenderer, widget/NextClassCard.kt; **owner**: an App Group, for every line that reads progress)
- [ ] The lead movement's figure on a tile: a flip-book on Android; WidgetKit can't animate, so a still pose (WidgetRenderer.frames)
- [ ] The streak's flame closes the card: lit, unlit or out (WidgetRenderer; **owner**: App Group)
- [ ] A class beyond the tier keeps its lock: "locked for your tier" (NextClassCard locked, strings_widget.xml; **owner**: App Group)
- [ ] Every class done: "Sequence complete", "Every class done" (WidgetRenderer.WidgetText; **owner**: App Group)
- [ ] A tap opens the class's page; with every class done, or the strength test unanswered, it opens Today (widget/NextClassWidget.kt programToOpen; iPhone: widgetURL; **owner**: App Group)
- [ ] It follows progress, the tier and the theme, and redraws as a new day starts and when the clock or time zone changes (NextClassWidget.watch, scheduleNewDay; iPhone: WidgetCenter reloads and a midnight timeline; **owner**: App Group in the Apple account)
- [ ] Spoken as "Next class: Level 4, class 20, … Opens the class." (WidgetRenderer, widget_open_class; **owner**: App Group)
- [ ] Gallery description and preview (widget_description, res/layout/widget_preview.xml)

## 24. Sharing and progress analytics

Android has no share sheet; its one sharing feature is Share my progress.

- [ ] "Share my progress" switch, on by default, at the end of the strength test and in Settings › Your data (ui/ShareProgressView.kt; AppSettings.shareProgress)
- [ ] The install's record goes up when the app opens (at most every 10 minutes, once the test is answered or skipped) and after the test (analytics/Analytics.kt sync, placed; **owner**: Firebase iPhone app)
- [ ] Each finished class goes up with its sets, kept on the phone until the server confirms it (Analytics.classFinished, analytics/AnalyticsPayload.kt; **owner**: Firebase)
- [ ] Switching it off stops the uploads and deletes this install's records (Analytics.shareChanged; **owner**: Firebase)
- [ ] A deleted account's id is taken off the install's record (Analytics.accountDeleted; **owner**: Firebase)
- [ ] The install's chain: a random install id and Google's App Set ID; on iPhone identifierForVendor in its place (analytics/FirestoreAnalytics.kt; **owner**: Firebase)

## 25. Accessibility and languages

- [x] English only, the Android app's texts (HomeActivity.attachBaseContext → composeApp reads res/values/strings*.xml)
- [x] Spoken labels on the tabs, Today's head and streak, and the focus view's buttons (contentDescription → Shell.kt, TodayHeader.kt, StreakViews.kt, FocusScreen.kt)
- [x] Focus view spoken cues: rest started with what's next, every 15 s of rest left, rest over, time's up, switch sides (FocusWorkoutView.update, done → Training's Cues, FocusScreen.cueText; **iPhone API**: UIAccessibilityPostNotification with UIAccessibilityAnnouncementNotification)
- [x] Spoken labels in the list view: "Mark set 2 of Pull-Up complete", "Complete all Warm-up sets" with "3 of 8 sets" (renderActive → WorkoutList)
- [x] Spoken calendar days: "November 5, today, 2 workouts, 1 repeat"; the month title read when it changes; the arrows named (HistoryCalendarView.describe → HistoryScreen.describe, a polite live region)
- [x] Spoken medals ("Strict Pull-Up, Moderate skill, Rank 2"), points and ranks with "Double-tap for how…" (SkillsScreen.medalDescription → SkillCell, SkillsSection)
- [ ] Spoken park scene with its three movements and a Replay action (ParkSceneView, park_scene_description)
- [x] Spoken movement guide: "Line-figure movement guide for X…" (ExerciseAnimationView.bind → ExerciseFigure)
- [ ] Spoken strength-test counters and their − and + ("One rep more, Strict pull-ups") (PlacementView.countTile)
- [x] Spoken theme rows with "selected", level cards with progress and tier, class rows with their state (ThemeDecor.themePicker, HomeActivity.levelCard, ProgramListAdapter → ThemePicker, TrainingScreen, LevelScreen)
- [ ] Reduced motion also stills the park scene and the heart's pop (ParkSceneView, heartButton; **iPhone API**: UIAccessibility.isReduceMotionEnabled)
- [ ] Large text keeps every control on screen: the focus view cuts its info first (FocusWorkoutView.screen)

## 26. Anything else

- [ ] Back after the phone closed the app in the background: the same screen and tabs, the unsaved builder, the strength test's counts and the running rest (HomeActivity.onSaveInstanceState; **iPhone API**: state restoration)
- [ ] Landscape: the focus view and the Timer lay out side by side (FocusWorkoutView.screen, TimingPanel.build; the iPhone app is portrait-only in project.yml)

## Build order

Thirteen rounds of about 20 to 28 lines, each one sitting's build and test, in this order. A screen's spoken labels (§25) come with it.

1. **The line man and the list view** (27 lines): §5; §7; the list view's and the movement guide's spoken labels. Done 18 Nov 2026, with repeating a completed class and Complete day's question.
2. **The workout loop** (23): §4 but the locked level's page; §8; During your workout (§17); the rest and timed-set sounds (§22); the focus view's spoken cues. Done 18 Nov 2026, but "After finishing, Progress opens", which waits for Progress (round 4). The locked level's page came with round 3's Unlock.
3. **Today and Training › Levels** (25): §9; from §3 the label over the title, the pick kept for the day, Complete day's question, the midnight redraw, the region's week and large text. Done 18 Nov 2026. Tier tags and Unlock show; until round 10 they open the offer's heading and line in Android's words, with Cancel.
4. **Progress: History, the streak and Skills** (28): §12; §13; the streak tap on Today; spoken calendar days and medals. Done 18 Nov 2026, with "After finishing, Progress opens" from round 2. HistoryCalendar and StreakDays' local days are common Kotlin on kotlinx-datetime (`data/HistoryCalendar.kt`), checked against java.util.Calendar for eight zones over three years. The Leaderboard tab shows "Not on iPhone yet." until round 13.
5. **Settings and themes** (23): §17 but During your workout and Simulate tier; §18; the back swipe (§1); spoken theme rows. Done 18 Nov 2026. The switches act where Android's do (the sequence, class rows and pages, both workout views, exams, Skills and so the points), kept under Android's keys; the theme, Start over, the developer settings and Inspect all days too. Settings leaves out what needs an account or a purchase (Profile, Plus and Pro, Your data, Ad privacy) until their rounds.
6. **The strength test and the park scene** (24): §2; the park scene and its replay (§3); the loading screen and load error (§1); spoken park scene and counters; reduced motion for the park and the heart. Needs: round 1's figure rig; ParkStory off java TimeZone.
7. **My workouts** (23): §10; a Library or own workout in progress taking Today's card. Needs: round 1 (picker tiles), round 2 (the class page); a library store on the phone (LibraryCodec is in the engine).
8. **Library and Timer** (23): §11; §14; the timer and alarm alerts (§22). Needs: round 7 (the heart on a ready workout); UserNotifications, which the Simulator runs.
9. **Parks** (18): §15; large text (§25); coming back after the phone closed the app, and landscape (§26). Needs: MapLibre iOS through Swift Package Manager in a UIKitView; CoreLocation; `spots.tsv` in the bundle (5 MB). The weekly refresh waits for the owner's Firebase setup.
10. **Plans and purchases** (25): §19, with the plans in the offer that Unlock and the tier tags open (now its heading, line and Cancel); Simulate tier. Needs: StoreKit 2 in Swift behind a Kotlin `Store` interface, tested with a `.storekit` file; the owner's products for the sandbox check.
11. **Widget and ads** (21): §23; §20. Needs: a WidgetKit extension and an App Group (owner); the Google Mobile Ads SDK and UMP with demo units, then the owner's AdMob app, units and consent message.
12. **Account and cloud backups** (18): §21; the park's "Well done, <name>!". Needs: the owner's Firebase iPhone app; Firebase iOS SDK, GoogleSignIn and Sign in with Apple, bridged from Swift.
13. **Leaderboard and progress analytics** (20): §16; §24. Needs: round 12; the owner's decision on filtering, reporting and blocking usernames (Apple 1.2).

## Waiting on the owner

1. **Apple Developer Program**, as an Organization (a D-U-N-S number first). Unblocks App Store Connect, TestFlight, signing on a real iPhone, the widget's App Group and Sign in with Apple.
2. **App Store Connect**: the Paid Apps agreement, tax and bank details; products `plus`, `pro_life` and the yearly Pro subscription (Claude can fill the forms in Chrome once the account exists). Unblocks the sandbox checks of §19 and offer codes.
3. **App Store Connect API key** and its three GitHub secrets. Unblocks TestFlight uploads from CI.
4. **A real iPhone** with TestFlight. Unblocks the vibration, background sounds and alerts, the widget and location on a device.
5. **AdMob**: the iPhone app, a native and a full-screen unit, and the iPhone consent message. Unblocks §20.
6. **Firebase** (`streetgym-8dc6d`): the iPhone app and its `GoogleService-Info.plist`, an iPhone OAuth client for Google, Sign in with Apple as a provider, App Attest for App Check, and `parks_url`. Unblocks §21, §16, §24, the park's greeting and the weekly parks refresh.
7. **Decisions**: how usernames are filtered, reported and blocked (Apple 1.2); landscape or portrait only; whether Pro bought on Android opens on iPhone; iPhone only or iPad too.
8. **App Privacy** answers and a privacy page that names the iPhone app, then **Submit for review**. Unblocks the App Store release.
