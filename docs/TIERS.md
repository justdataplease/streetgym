# Free, Plus and Pro

Street Gymnastics has three tiers. Plus is one Google Play payment. Pro is a yearly subscription that includes everything in Plus; it pays for the servers and database behind the account, and the Pro offer says so.

| | Free | Plus · 9.99 once | Pro · 19.99 a year |
|---|---|---|---|
| Levels | 1–3 | 1–5 | all (6 to 10 are Pro only) |
| Ads | yes | no | no |
| Saved and custom days (My workouts) | 3 | 30 | 300 |
| Own lists in My workouts (beside Favorites) | – | 3 of 10 days | 10 of 30 days |
| Rename levels | – | yes | yes |
| Parks near me (23,512 bundled parks) | – | yes | yes |
| Leaderboard | – | view | view and appear on it |
| Sign in with Google, cloud progress | – | – | yes |

The prices shown are the requested starting prices; the app only ever shows the formatted price Google Play returns.

## Play Console products (owner)

| Product | Type | ID | Details |
|---|---|---|---|
| Plus | One-time product, non-consumable | `plus` | Purchase option `buy`, 9.99 EUR starting price |
| Pro | Subscription | `pro` | Base plan `yearly`, auto-renewing, 19.99 EUR a year |

Earlier IDs `full_program` and `remove_ads` were never activated and are no longer recognized; don't activate them. Play product IDs can't be reused once created.

## Where each tier applies

- **Levels.** `LevelAccess.locked(level, Entitlement.tier(context))` locks Levels 4 and 5 on Free and Levels 6 to 10 below Pro (`LevelAccess.tierFor`, owner's call on 2026-10-28). Today, Training, Overview, Skills, repeats and an active class all show the paywall instead: Plus and Pro for Levels 4 and 5, Pro alone for 6 to 10 (Levels 8–10 joined Pro on 2026-10-29). The level cards and skill cells carry the tier's badge.
- **Ads.** `Entitlement.adFree` (Plus and Pro) removes every placement.
- **My workouts.** Saved days (the heart on an overview) and custom days share `Tier.savedDays`. At the limit, adding shows the paywall (or, on Pro, a note). Nothing is ever removed when a tier ends: everything stays usable, and only adding stops until the athlete is under the limit.
- **Lists.** Favorites is the default list: every saved and custom day is in it, on every tier. Plus and Pro also make their own lists (`Tier.workoutLists` and `Tier.daysPerList`: 3 lists of 10 days on Plus, 10 lists of 30 on Pro, so the lists can hold every saved day); on those tiers the heart opens a picker with Favorites always ticked, the athlete's lists, New list and, for a saved day, Remove. Free's heart saves straight to Favorites, and its New list chip shows the Plus paywall; Plus at 3 lists sees Pro; a full list can't be ticked in the picker. Lists don't count toward the saved days, and a day leaves its lists when it leaves My workouts. Lists live in the library JSON (`LibraryState.lists`), so Pro's cloud copy carries them with no rules change; each list merges by its newest change, and a deleted list stays as a tombstone. When a tier ends the lists are kept but hidden, and come back with Plus.
- **Rename levels** and **Parks** (Training tab) show the Plus paywall on Free.
- **Leaderboard** (Progress tab): Free sees the Plus paywall; Plus sees the boards and an offer to join with Pro; Pro signs in and appears.
- **Account** (Settings): only Pro. A lapsed Pro stays signed in; nothing syncs and its cloud copy and last score are kept until it renews or deletes the account.

`TierBilling` follows the Toolbox entitlement model: only PURCHASED grants, pending grants nothing, purchases are acknowledged, ownership is cached offline, and only a successful complete query of a product's own type revokes it (a refund, or a lapsed subscription). There's no server-side purchase verification: Firestore trusts the app's tier. Restore purchases checks both.

**Debug and GitHub builds** can't buy (the `.debug` package isn't the Play app), so they get Pro. Settings > Simulate tier (debug only) picks Free, Plus or Pro to test limits, paywalls and ads; cached real ownership still counts on top. Release builds have no override.

## Pro account setup (owner, Firebase console)

Without these steps the app runs exactly as before: every account feature stays hidden, and the leaderboard tab says it isn't available in this build.

1. **Firebase project.** Use the original StreetGym project, `streetgym-8dc6d` (`firebase/.firebaserc` already points there). That app used only email sign-in from it, so nothing else depends on its Firestore. Switch it to the **Blaze** plan: scheduled functions need it. Set a budget alert (for example 5 EUR a month).
2. **Android apps.** Both packages are registered there (since 2026-10-30; the original app's two apps were removed): `com.justdataplease.streetgym` (Play) and `com.justdataplease.streetgym.debug` (GitHub builds). Give the suite only the release configuration. Debug builds are Pro by default, so a debug `firebase.xml` in a GitHub build would give everyone who installs it free cloud progress and a place on the real leaderboard; keep it on your own test phone, if anywhere.
3. **Fingerprints** (Project settings > your app > Add fingerprint), SHA-1 and SHA-256:
   - Play app signing key and upload key (Play Console > App integrity);
   - for a test project with the `.debug` app: the suite GitHub key `artifacts/suite/signing/debug.keystore`, SHA-1 `9C:21:22:2A:74:88:AC:10:5A:22:DF:7E:21:C4:EC:B6:FF:B3:21:4E`, SHA-256 `89:2D:89:77:6E:E0:D6:69:3A:19:B7:CD:FB:DA:EC:CB:88:1B:85:C9:2D:DF:3B:14:76:C4:0D:90:99:0F:56:B3`; and a local debug key, SHA-1 `BF:32:0F:B0:00:DA:F0:14:A1:16:44:E1:8C:1A:65:16:F9:AD:96:18`.
4. **Authentication > Sign-in method > Google** (enabled 2026-10-30; the debug app has the GitHub and local debug fingerprints, the Play app still needs its Play fingerprints): enable it, then copy the **Web client ID** (OAuth 2.0 client of type Web). Email/password, from the original app, can be turned off: the suite app signs in with Google only.
5. **Firestore:** create the database in production mode, region `europe-west1` (or `eur3`). If the project already has one, keep it and its location.
6. **Deploy rules and the function** from `streetgymnastics/firebase`: `firebase login`, then `firebase deploy --only firestore,functions`. The function is Python 3.12 (`functions/main.py`, tests in `functions/test_boards.py`).
7. **App Check:** register the Play app with Play Integrity. Leave enforcement off at first and turn it on for Firestore once the metrics show the genuine app's traffic (debug builds print a debug token to register for testing).
8. **Parks refresh:** from `streetgymnastics/tools/parks_job`, run `.\deploy_parks_scheduler.ps1 -Project streetgym-8dc6d` (the Cloud Run Job, its quarterly Cloud Scheduler trigger and the public parks bucket), then run the job once. It prints the `parks_url`.
9. **App configuration:** copy the original app's git-ignored `streetgymnastics/app/src/release/res/values/firebase.xml` (it has `google_app_id`, `google_api_key`, `project_id` and `gcm_defaultSenderId` for `streetgym-8dc6d`) to the same path in the suite, and add `default_web_client_id` (the Web client ID from step 4) and `parks_url` from step 8. Do this after steps 4–8: with only the original keys the leaderboard tab appears, but there are no boards to read yet.
10. **Play Console:** update Data safety: account info (email, user ID), the chosen username and country, and fitness activity (workout history) are collected for app functionality and the leaderboard, encrypted in transit, deletable in the app. Approximate location is used on the device only for Parks near me and isn't collected; the Parks map loads its tiles from OpenFreeMap (tiles.openfreemap.org), which sees the requested map area and the phone's IP address like any tile server, so the privacy policy should name it. Add an account-deletion web link (the privacy page can explain in-app deletion: Settings > Account > Delete account) and update the privacy policy.

## How Pro keeps costs down

Firestore bills per document read and write. At the list prices for standard regions (check the current pricing page), about 0.06 USD per 100,000 reads and 0.18 USD per 100,000 writes, with a free 50,000 reads and 20,000 writes a day.

- **Parks** cost almost nothing: every park ships in the app (`assets/spots.tsv`, 2 MB), distances are computed on the phone, the map's tiles come free from OpenFreeMap (no key, no per-view charge, cached on the phone by MapLibre), and directions open the athlete's own maps app. The quarterly job runs four short times a year, and a phone asks "changed?" at most weekly (a few bytes) and downloads the 0.7 MB file only after a refresh.
- **Boards** are precomputed: each is one document with the top 100, the player count and 100 percentiles. Viewing a board is one read, then it stays on the phone for six hours. The athlete's place comes from the same document (exact rank in the top 100, else "top N%"), so no extra queries.
- **The board rebuild** (every six hours) costs two reads and stops when no score was added, changed or removed. Otherwise it reads each score once and writes only the boards whose content changed (a hash comparison).
- **Scores** are written only after a finished workout changes them, at most every ten minutes, and include only real workouts (not Complete day or Complete level, repeats or custom days).
- **Cloud progress** syncs a few seconds after a change on the phone (one sync for a burst of changes), and otherwise at most every six hours when the app opens: two reads, and writes only for the documents that changed.

A rough worst case per Pro athlete is about 10 reads and 4 writes a day. A thousand Pro athletes stay inside the free daily quota; ten thousand are of the order of a few dollars a month in total.

## Parks data

The parks come from howtocalisthenics.com (names and coordinates only). The bundled `spots.tsv` (built with `tools/build_spots_asset.py` from a `scrape-properties/howtocalisthenics` snapshot) is the offline baseline. The quarterly refresh runs online, like JustMaps' event import: the Cloud Run Job `streetgym-parks-refresh`, started by Cloud Scheduler on 1 January, April, July and October, publishes a new file to a public bucket, and the app checks it at most weekly. See [tools/parks_job/README.md](../tools/parks_job/README.md).

## Acceptance before selling

With a Play license tester and the registered package, verify for each product: purchase and acknowledgement, cancellation, pending payment, reinstall and Restore purchases, offline ownership, refund (levels lock again, ads return), localized prices; for Pro also a subscription lapse (Plus, if owned, remains) and Manage subscription. With the Firebase project set up: sign in, choose a username (a taken one is refused), sync between two phones, the board after the next rebuild, hiding from the leaderboard, and account deletion.

JVM tests cover tiers and entitlement reconciliation, the saved-days limit, the custom-day model and merge, the exercise search, parks search and distance, scores, board caching and standing, usernames, countries and the progress merge; `firebase/functions/test_boards.py` covers the board builder. None of these is a real Google Play transaction or a live Firestore call.

References: [Google Play Billing](https://developer.android.com/google/play/billing/integrate), [subscriptions](https://developer.android.com/google/play/billing/subscriptions), [Credential Manager with Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg), [Firestore pricing](https://firebase.google.com/docs/firestore/pricing).
