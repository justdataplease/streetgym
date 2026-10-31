# Free, Plus and Pro

Street Gym has three tiers (the owner, 2026-10-30, for 2.8.0). Plus is one Google Play payment. Pro includes everything in Plus and is either a yearly subscription, which pays for the servers and database behind cloud progress, or one larger payment, "Pro for life".

| | Free | Plus · 14.99 once | Pro · 29.99 a year or 89.99 once |
|---|---|---|---|
| Levels | 1–3 | 1–6 | all (7 to 10 are Pro only) |
| Ads | yes | no | no |
| Leaderboard (sign in with Google, appear on it) | – | yes | yes |
| Ready workouts (Training › Library, 36) | see them | see them | yes |
| Cloud progress (save and restore My workouts and progress) | – | – | yes |
| Saved and custom days (My workouts) | 3 | 30 | 300 |
| Own lists in My workouts (beside Favorites) | – | 3 of 10 days | 10 of 30 days |
| Rename levels | – | yes | yes |
| Parks near me (73,303 OpenStreetMap parks) | – | yes | yes |

The prices are the owner's Play prices (decided 2026-10-30, before the first upload; earlier drafts said 9.99 and 19.99; Pro for life was added the same evening). In 2.7.0 Plus was Levels 1–5 and only Pro signed in. The app only ever shows the formatted price Google Play returns. Play adds VAT and rounds per country, so enter each VAT country's price where a clean consumer price matters.

## Play Console products (owner)

| Product | Type | ID | Details |
|---|---|---|---|
| Plus | One-time product, non-consumable | `plus` | Purchase option `buy`, 14.99 EUR |
| Pro | Subscription | `pro` | Base plan `yearly`, auto-renewing, 29.99 EUR a year |
| Pro for life | One-time product, non-consumable | `pro_life` | Purchase option `buy`, 89.99 EUR |

Earlier IDs `full_program` and `remove_ads` were never activated and are no longer recognized; don't activate them. Play product IDs can't be reused once created.

## Where each tier applies

- **Levels.** `LevelAccess.locked(level, Entitlement.tier(context))` locks Levels 4 to 6 on Free and Levels 7 to 10 below Pro (`LevelAccess.tierFor`; Plus gained Level 6 on 2026-10-30). Today, Training, Overview, Skills, repeats and an active class all show the paywall instead: Plus and Pro for Levels 4 to 6, Pro alone for 7 to 10. The level cards and skill cells carry the tier's badge.
- **Ads.** `Entitlement.adFree` (Plus and Pro) removes every placement.
- **My workouts.** Saved days (the heart on an overview) and custom days share `Tier.savedDays`. At the limit, adding shows the paywall (or, on Pro, a note). Nothing is ever removed when a tier ends: everything stays usable, and only adding stops until the athlete is under the limit.
- **Lists.** Favorites is the default list: every saved and custom day is in it, on every tier. Plus and Pro also make their own lists (`Tier.workoutLists` and `Tier.daysPerList`: 3 lists of 10 days on Plus, 10 lists of 30 on Pro, so the lists can hold every saved day); on those tiers the heart opens a picker with Favorites always ticked, the athlete's lists, New list and, for a saved day, Remove. Free's heart saves straight to Favorites, and its New list chip shows the Plus paywall; Plus at 3 lists sees Pro; a full list can't be ticked in the picker. Lists don't count toward the saved days, and a day leaves its lists when it leaves My workouts. Lists live in the library JSON (`LibraryState.lists`), so Pro's cloud copy carries them with no rules change; each list merges by its newest change, and a deleted list stays as a tombstone. When a tier ends the lists are kept but hidden, and come back with Plus.
- **Rename levels** and **Parks** (Training tab) show the Plus paywall on Free.
- **Leaderboard** (Progress tab): Free sees the Plus paywall; Plus and Pro see the boards, sign in from Settings and appear on them (`Tier.account`).
- **Account** (Settings): Plus and Pro sign in. Only Pro syncs (`Tier.cloud`, `CloudSync`): Plus sees "Your progress stays on this phone. Cloud saves are part of Pro." instead of the sync status. An athlete whose plan ends stays signed in; nothing syncs or publishes, and the cloud copy and last score are kept until they renew or delete the account.
- **Ready workouts** (Training › Library, `Tier.readyWorkouts`): 200 workouts in eight groups (full body, pull, push, legs, core, skills, short on time, mobility), filtered at the top by how hard each is for the athlete, grade and focus (a group or a skill family), each a whole class with warm-up, cool-down and easier options, written in `tools/build_ready_workouts.py` from the program's own movements (assets/workouts.json, ids `ready:<name>`). Every tier sees the list and each overview; below Pro the Pro offer replaces Start. They run like a custom day: any time, at the normal intensity, every skill on, never completing a class; their sets count toward Skills and the leaderboard, and a heart saves one to My workouts.

`TierBilling` (Plus and Pro for life as one-time products, Pro as a subscription) follows the Toolbox entitlement model: only PURCHASED grants, pending grants nothing, purchases are acknowledged, ownership is cached offline, and only a successful complete query of a product's own type revokes it (a refund, or a lapsed subscription). There's no server-side purchase verification: Firestore trusts the app's tier. Restore purchases checks all three.

**Debug and GitHub builds** can't buy (the `.debug` package isn't the Play app), so they get Pro. Settings > Simulate tier (debug only) picks Free, Plus or Pro to test limits, paywalls and ads; cached real ownership still counts on top. Release builds have no override, only the review code below.

## Play review access

Play's reviewers can't buy, use their own accounts or start a trial, so a hidden code gives them Pro. Hold the title in Settings › About for two seconds, type the code, tap OK: the right code makes the app Pro on that phone for 30 days (`ReviewAccess`, stored as `Entitlement.REVIEW_UNTIL`), release builds included; a wrong one says "Code not recognised" and changes nothing. The app holds only the SHA-256 of the code (trimmed, in capitals, hyphens kept); to change the code, replace `ReviewAccess.CODE_SHA256`. The code itself is kept outside this public repository: in the owner's Play Console › App content › App access ("Sign in details") and in `C:\Users\jason\keys\streetgymnastics\`. Progress analytics only sees `tier` `pro`.

## Pro account setup (owner, Firebase console)

Without these steps the app runs exactly as before: every account feature stays hidden, and the leaderboard tab says it isn't available in this build.

1. **Firebase project.** Use the original StreetGym project, `streetgym-8dc6d` (`firebase/.firebaserc` already points there). That app used only email sign-in from it, so nothing else depends on its Firestore. Switch it to the **Blaze** plan: scheduled functions need it. Set a budget alert (for example 5 EUR a month).
2. **Android apps.** Both packages are registered there (since 2026-10-30; the original app's two apps were removed): `com.justdataplease.streetgym` (Play) and `com.justdataplease.streetgym.debug` (GitHub builds). Give the suite only the release configuration. Debug builds are Pro by default, so a debug `firebase.xml` in a GitHub build would give everyone who installs it free cloud progress and a place on the real leaderboard; keep it on your own test phone, if anywhere.
3. **Fingerprints** (Project settings > your app > Add fingerprint), SHA-1 and SHA-256:
   - Play app signing key and upload key (Play Console > App integrity);
   - for a test project with the `.debug` app: the suite GitHub key `artifacts/suite/signing/debug.keystore`, SHA-1 `9C:21:22:2A:74:88:AC:10:5A:22:DF:7E:21:C4:EC:B6:FF:B3:21:4E`, SHA-256 `89:2D:89:77:6E:E0:D6:69:3A:19:B7:CD:FB:DA:EC:CB:88:1B:85:C9:2D:DF:3B:14:76:C4:0D:90:99:0F:56:B3`; and a local debug key, SHA-1 `BF:32:0F:B0:00:DA:F0:14:A1:16:44:E1:8C:1A:65:16:F9:AD:96:18`.
4. **Authentication > Sign-in method > Google** (enabled 2026-10-30; the debug app has the GitHub and local debug fingerprints, the Play app still needs its Play fingerprints): enable it, then copy the **Web client ID** (OAuth 2.0 client of type Web). Email/password, from the original app, can be turned off: the suite app signs in with Google only. Enable **Anonymous** too: progress analytics signs in with it ("Progress analytics" below). Until it is on, nothing is uploaded for athletes without an account.
5. **Firestore:** create the database in production mode, region `europe-west1` (or `eur3`). If the project already has one, keep it and its location.
6. **Deploy rules and the function** from `streetgymnastics/firebase`: `firebase login`, then `firebase deploy --only firestore,functions`. The function is Python 3.12 (`functions/main.py`, tests in `functions/test_boards.py`).
7. **App Check:** register the Play app with Play Integrity. Leave enforcement off at first and turn it on for Firestore once the metrics show the genuine app's traffic (debug builds print a debug token to register for testing).
8. **Parks refresh:** from `streetgymnastics/tools/parks_job`, run `.\deploy_parks_scheduler.ps1 -Project streetgym-8dc6d` (the Cloud Run Job, its quarterly Cloud Scheduler trigger and the public parks bucket), then run the job once. It prints the `parks_url`.
9. **App configuration:** copy the original app's git-ignored `streetgymnastics/app/src/release/res/values/firebase.xml` (it has `google_app_id`, `google_api_key`, `project_id` and `gcm_defaultSenderId` for `streetgym-8dc6d`) to the same path in the suite, and add `default_web_client_id` (the Web client ID from step 4) and `parks_url` from step 8. Do this after steps 4–8: with only the original keys the leaderboard tab appears, but there are no boards to read yet.
10. **Play Console:** update Data safety: account info (email, user ID), the chosen username and country, and fitness activity (workout history) are collected for app functionality and the leaderboard, encrypted in transit, deletable in the app. Progress analytics adds fitness info and device or other IDs for Analytics, optional, from every tier ("Progress analytics" below). Approximate location is used on the device only for Parks near me and isn't collected; the Parks map loads its tiles from OpenFreeMap (tiles.openfreemap.org), which sees the requested map area and the phone's IP address like any tile server, so the privacy policy should name it. Add an account-deletion web link (the privacy page can explain in-app deletion: Settings > Account > Delete account) and update the privacy policy.

## Progress analytics

Every tier's training progress goes to Firestore (the owner, 2026-10-30), to see how athletes do and improve the program, while **Share my progress** is on: the note and switch at the bottom of the first-launch placement and in Settings › Your data, on by default (`AppSettings.shareProgress`). Code: `analytics/Analytics.kt` (when), `AnalyticsPayload.kt` (what), `FirestoreAnalytics.kt` (Firestore, App Set ID); rules: `firebase/firestore.rules`, `analytics/`.

- **Who writes.** A build without Firebase (GitHub) uploads nothing. Otherwise the app writes as the Google account when a Plus or Pro athlete is signed in, else as an anonymous Firebase user it signs in only for this. The anonymous user is no account: `Cloud.uid`, the Account section, cloud progress and the leaderboard ignore it, and the rules refuse it everywhere but `analytics/`. A Google sign-in simply replaces it.
- **The chain.** A random install ID (a UUID, kept until the app's data is cleared), Google's App Set ID (developer scope when Play services gives it; it survives a reinstall), and a signed-in account's Firebase uid. No Android ID, advertising ID or device details.
- **`analytics/{installId}`**: `v` 1, `appSetId`, `appSetScope` (`developer` or `app`), `accountUid` (only ever the writer's own Google account; an anonymous write leaves it as it was), `firstSeen`, `lastSeen` (server time), `versionName`, `versionCode`, `tier` (`free`, `plus` or `pro`, as the app believes it: Pro in debug), `debug`, `shareProgress` true, `placement` {`pullUps`, `pushUps`, `squats` (null when left empty), `skillTest`, `startLevel`, `startDay`}, `skills` {skill id: [level 0–5, sets, best set]} for started skills, `score` (overall out of 1000, the leaderboard's scale), `switches` (`SkillSwitches.format`), `completedClasses`, `trainedClasses`, `markedClasses`, `atLevel` and `atDay` (the first day not passed).
- **`analytics/{installId}/sessions/{startedAt millis}`**, one per finished class: `v` 1, `programId` (`sg:lNN:pNNNN`, `ready:<name>` for a ready workout, or `custom` for an own day), `level`, `day`, `dayType`, `exam`, `custom`, `repeat`, `intensity`, `effort` (1–10), `startedAt`, `finishedAt`, `durationSec`, `plannedSets`, `doneSets`, `leftOutSets` (skill switches), `uploadedAt` (server time), and `exercises`: each {`section`, `movementId`, `easier` (the easier option's movement when used), `test` (an exam target), `sets`: each {`planned` (reps or seconds, null for max), `max`, `timed`, `done` (logged, else null), `ticked`, `off`}}.
- **Never sent:** names (an own day's or its exercises'), email, username, country, location, locale, time zone, IP-derived data or anything typed. Ids that aren't plain ids are dropped (`AnalyticsPayload.ID`), and the rules allow only the fields above.
- **When.** The install document when the app opens, at most every 10 minutes and only once the placement was answered or skipped; after a placement; and with each finished class in one batch (two writes). Days marked done by Complete day, Complete level or the placement aren't sessions; they're counted in `markedClasses`. A class stays on the phone until the server confirms its write.
- **Off** stops every upload and deletes the install document and every class this install wrote (it keeps their ids, as nobody can read the collection), retried until confirmed. Deleting the Pro account takes its uid off this install's document at the next sync.
- **Kept 24 months.** The scheduled function `expire_analytics` (`firebase/functions/expiry.py`, daily at 03:30) deletes every session uploaded more than 730 days ago and every install document not updated for 730 days, by the server's `uploadedAt` and `lastSeen`, so a phone's clock plays no part. The sessions query needs the collection-group index on `uploadedAt` in `firestore.indexes.json`. The privacy page says "each upload is deleted 24 months after it was made".
- **Cost.** About one write per app open (at most every 10 minutes) and two per class; no reads. The daily expiry costs two reads while nothing is due.
- **Setup:** enable Authentication › Sign-in method › **Anonymous**, then deploy from `streetgymnastics/firebase`: `firebase deploy --only firestore,functions` (rules, the index and both functions).
- **Privacy page and Data safety:** fitness info (classes, sets, reps and holds, skill levels, score, placement counts) and device or other IDs (install ID, App Set ID, the anonymous user's id), collected for Analytics, not shared, encrypted in transit, optional (on by default, switch off in the app), deletable by switching it off. It is pseudonymous, not anonymous: for a signed-in Pro athlete the record carries the account's uid, which links it to the account.

## How Pro keeps costs down

Firestore bills per document read and write. At the list prices for standard regions (check the current pricing page), about 0.06 USD per 100,000 reads and 0.18 USD per 100,000 writes, with a free 50,000 reads and 20,000 writes a day.

- **Parks** cost almost nothing: every park ships in the app (`assets/spots.tsv`, 5 MB), distances are computed on the phone, the map's tiles come free from OpenFreeMap (no key, no per-view charge, cached on the phone by MapLibre), and directions open the athlete's own maps app. The quarterly job runs four times a year (about an hour each, mostly waiting on the public OSM database), and a phone asks "changed?" at most weekly (a few bytes) and downloads the 1.4 MB file only after a refresh.
- **Boards** are precomputed: each is one document with the top 100, the player count and 100 percentiles. Viewing a board is one read, then it stays on the phone for six hours. The athlete's place comes from the same document (exact rank in the top 100, else "top N%"), so no extra queries.
- **The board rebuild** (every six hours) costs two reads and stops when no score was added, changed or removed. Otherwise it reads each score once and writes only the boards whose content changed (a hash comparison).
- **Scores** are written only after a finished workout changes them, at most every ten minutes, and include only real workouts (not Complete day or Complete level, repeats or custom days).
- **Cloud progress** syncs a few seconds after a change on the phone (one sync for a burst of changes), and otherwise at most every six hours when the app opens: two reads, and writes only for the documents that changed.

A rough worst case per Pro athlete is about 10 reads and 4 writes a day. A thousand Pro athletes stay inside the free daily quota; ten thousand are of the order of a few dollars a month in total.

## Parks data

The parks come from OpenStreetMap (names and coordinates only; © OpenStreetMap contributors, ODbL), read from Geofabrik's Postpass copy of the OSM planet: outdoor fitness stations and calisthenics places, stations within 50 m as one park, named "City - Name - Park - Neighbourhood - Country". The app credits OpenStreetMap under the Parks map and list, and the file's first line carries the ODbL notice. The bundled `spots.tsv` (built with `tools/parks_job/osm_parks.py`) is the offline baseline. The quarterly refresh runs online, like JustMaps' event import: the Cloud Run Job `streetgym-parks-refresh`, started by Cloud Scheduler on 1 January, April, July and October, publishes a new file to a public bucket, and the app checks it at most weekly. See [tools/parks_job/README.md](../tools/parks_job/README.md).

## Acceptance before selling

With a Play license tester and the registered package, verify for each product (`plus`, `pro`, `pro_life`): purchase and acknowledgement, cancellation, pending payment, reinstall and Restore purchases, offline ownership, refund (levels lock again, ads return), localized prices; for Pro also a subscription lapse (Plus, if owned, remains) and Manage subscription. With the Firebase project set up: sign in, choose a username (a taken one is refused), sync between two phones, the board after the next rebuild, hiding from the leaderboard, and account deletion.

JVM tests cover tiers and entitlement reconciliation, the review code (with a test code, never the real one), the saved-days limit, the custom-day model and merge, the exercise search, parks search and distance, scores, board caching and standing, usernames, countries and the progress merge; `firebase/functions/test_boards.py` covers the board builder. None of these is a real Google Play transaction or a live Firestore call.

References: [Google Play Billing](https://developer.android.com/google/play/billing/integrate), [subscriptions](https://developer.android.com/google/play/billing/subscriptions), [Credential Manager with Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg), [Firestore pricing](https://firebase.google.com/docs/firestore/pricing).
