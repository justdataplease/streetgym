# Parks refresh (Cloud Run Job)

Parks near me (Plus) lists calisthenics parks from howtocalisthenics.com: names and coordinates only. The app ships a
copy (`app/src/main/assets/spots.tsv`), so Parks works offline from the first start. This job keeps it current
without an app release, the same way JustMaps refreshes its events: a **Cloud Run Job started by Cloud Scheduler**,
here on 1 January, April, July and October at 05:00 `Europe/Athens`. Nothing runs on a PC.

`refresh_parks.py` reads every spot from the site's map API (boxes the API truncates are asked again in quarters,
from the whole world down), cleans the names and checks the count against the published file. Then it publishes:

- `gs://<project>-parks/parks/spots.tsv.gz`: one park per line, `name<TAB>lat<TAB>lng`, sorted, gzip (about 0.7 MB);
- `gs://<project>-parks/parks/manifest.json`: count, SHA-256 and build time.

It publishes nothing when the run finds fewer than 0.8 × the published parks (the site changed or refused us), or
when the content is unchanged. The last line of each execution is `PARKS PASS …` or `PARKS FAIL …` in Cloud Logging.

## The app side

With `parks_url` set (in the git-ignored `app/src/release/res/values/firebase.xml`), opening Parks checks the file at
most once a week with a conditional request: unchanged costs a few bytes; a new file (about four times a year) is
downloaded once, checked the same way and kept on the phone. Without `parks_url`, or offline, the bundled copy is used.

## Deploy, run, inspect

Once, after the Firebase project exists (`docs/TIERS.md`, "Pro account setup"), from your own PowerShell with
`gcloud auth login` done:

```powershell
.\deploy_parks_scheduler.ps1 -Project <firebase project id>
gcloud run jobs execute streetgym-parks-refresh --region=europe-west1 --project=<project> --wait
gcloud scheduler jobs describe streetgym-parks-quarterly --location=europe-west1 --project=<project>
```

The script enables the APIs, creates the `streetgym-parks` service account, the `streetgym-jobs` image repository and
the public bucket `<project>-parks`, builds the image with Cloud Build, deploys the job and creates the schedule. It
prints the `parks_url` to put in `firebase.xml`. An organization policy that forbids public buckets blocks the
`allUsers` binding; then the app keeps the bundled copy.

Locally, without publishing: `python refresh_parks.py --dry-run`. Tests (no network, no Cloud Storage):
`python -m pytest streetgymnastics/tools/parks_job`.

Cost: four short executions a year (well inside Cloud Run's and Cloud Scheduler's free tiers), a 0.7 MB file in
storage, and the downloads: each Plus phone fetches the file about four times a year.
