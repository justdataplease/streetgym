# Sending the daily class to a Garmin watch: research notes

Short research from 28 October 2026. The owner asked what it would take to send Street Gymnastics' daily exercises to Garmin, then parked the idea. Nothing here is built.

## What a class needs to carry

A class is a list of sections: warm-up, skill practice, main work and cooldown. Each section holds exercises, and each exercise holds sets. A set has:
- reps or seconds;
- "per side";
- a tempo or note;
- rest between sets;
- an easier option.

A Garmin strength workout is a list of steps:
- each step has an exercise (a category plus a name from Garmin's list), a duration (reps, time, or until the lap button is pressed) and notes;
- rest is a separate step after each set;
- repeat blocks group steps.

So every set maps to one step plus one rest step. "Per side" becomes two steps or a note, and tempo and the easier option go in the notes.

## Three ways to do it

| | Garmin Training API | Connect IQ watch app | FIT workout file |
|---|---|---|---|
| What the athlete gets | The class appears in the Garmin Connect calendar and syncs to the watch as a native strength workout, with the watch's own timers, rep entry and heart rate. | Our own app on the watch: today's class, timers and the easier option, recorded as a strength activity. | A workout file to copy to the watch. |
| What we build | Garmin sign-in (OAuth) in the app; a Firebase function that turns the day's class into a Garmin workout and publishes it; a table mapping our movements to Garmin exercises. | A Monkey C watch app; the Connect IQ Mobile SDK in the Android app to send today's class to the watch; per-device layouts; a Connect IQ Store listing. | A FIT encoder in the app. |
| Access | The Garmin Connect Developer Program. Its business Starter tier includes the Training API (push only, limited user capacity). Enterprise has negotiated terms. The Personal tier only reads your own data. | Free SDK; apps go through Connect IQ Store review. | None. |
| Rough effort | About 1–2 weeks once approved. Approval time isn't published. | About 3–6 weeks for a solid first version, plus ongoing device testing. | Days, but no good way to get the file onto the watch from Android. |
| Main risks | Approval; the strength-step fields are only documented to approved members; custom exercise names. | Forum reports of phone↔watch messages not arriving; device fragmentation; a second codebase. | Poor user experience. |

**Leaning, if it's picked up:** the Training API. It gives the most native result (the class just appears on the watch) with no watch code, and it fits Pro, which already has accounts and Firebase functions. Apply for Starter access first. Its answers on strength-step fields and custom names decide the mapping work.

## Open questions

- **Exercise coverage.** Garmin's list is built around gym exercises. Calisthenics moves such as tuck front lever, planche lean, German hang and skin the cat may be missing. FIT workout files can carry custom names (the category set to "unknown" plus an exercise title record), but forum reports say step names can show as "Go" if this is done wrong. It's unclear whether the Training API accepts custom names.
- **Starter's user cap.** How many athletes Starter allows, and when Enterprise would be needed.
- **Watch coverage.** Which watches show strength workout notes, and how the easier option and tempo would read on a small screen.
- **Credit.** Whether a class done on the watch counts as finished in the app. It shouldn't count automatically: skill points need ticked sets.

## Sources

- [Garmin Connect Developer Program overview (tiers and APIs)](https://developer.garmin.com/gc-developer-program/overview)
- [Garmin Training API page](https://developer.garmin.com/gc-developer-program/training-api)
- [Tredict: Garmin Training API integration](https://tredict.com/blog/garmin_training_api_integration)
- [Connect IQ core topics (Mobile SDK, communicating with mobile apps)](https://developer.garmin.com/connect-iq/core-topics/)
- [Forum: watch does not receive messages from the phone](https://forums.garmin.com/developer/connect-iq/f/discussion/294579/watch-does-not-receive-the-messages-from-phone)
- [Forum: sending watch app data to an Android app](https://forums.garmin.com/developer/connect-iq/f/discussion/248486/send-garmin-watch-app-data-to-my-android-app)
- [Forum: strength workout exercise names](https://forums.garmin.com/developer/fit-sdk/f/discussion/327568/strength-workout-exercise-names)
- [Forum: custom workout FIT file step names not showing](https://forums.garmin.com/developer/fit-sdk/f/discussion/357803/custom-workout-fit-file---step-names-not-showing)
- [Terra: exercise reference by category](https://docs.tryterra.co/planned-workouts-api/exercise-reference)
