# Parks from OpenStreetMap (Cloud Run Job)

Parks near me (Plus) lists outdoor workout parks from **OpenStreetMap**: names and coordinates only, © OpenStreetMap
contributors, under the [Open Database License](https://www.openstreetmap.org/copyright) (ODbL 1.0). The app ships a
copy (`app/src/main/assets/spots.tsv`), so Parks works offline from the first start, and credits OpenStreetMap under
the map and the list. This job keeps the copy current without an app release, the same way JustMaps refreshes its
events: a **Cloud Run Job started by Cloud Scheduler**, here on 1 January, April, July and October at 05:00
`Europe/Athens`. Nothing runs on a PC.

The file is a derivative of the OSM database, so it is under the ODbL too: its first line says so, and anyone may
reuse it on the same terms.

## What `osm_parks.py` makes

`osm_parks.py` builds the file; the job (`refresh_parks.py`) and the bundled copy use the same code.

- **Which places:** `leisure=fitness_station` (a station or a fitness area; `fitness_station=*` names the equipment),
  `sport=calisthenics` or `sport=street_workout` (often on a `leisure=pitch`), and `leisure=pitch` with
  `sport=fitness` (exact values; a `sport` list such as `soccer;calisthenics` isn't read). Left out: indoor places,
  buildings, gyms (`leisure=fitness_centre`), `access=private`/`no`, a lone sign, and places whose tagged equipment is
  only machines or trail exercises (elliptical trainers, air walkers, sit-up benches, balance beams …). Untagged
  equipment counts: most stations don't say what they are, so many outdoor gyms with machines only are in too.
- **One place per spot:** stations within 50 m of each other, or inside a small fitness area, are one place, at the
  area's point or the station in the middle.
- **Names** like the old list, `City - Name - Park - Neighbourhood - Country`, in English where OSM has an English
  name: the place's OSM name (else `Workout park`), the park it lies in, the nearest neighbourhood (for unnamed
  places), the town or city (place nodes: the one whose reach, growing with a city's population, covers the spot best)
  and the country (the most specific OSM boundary with an ISO 3166-1 code, so Puerto Rico and Hong Kong are their own).
  Empty or repeated parts are dropped. The app's search matches every word of these names.
- **The file:** a first line with the ODbL notice (the app skips it), then one park per line,
  `name<TAB>lat<TAB>lng`, sorted, coordinates to 5 decimals. The same OSM data gives the same file.

**Source: [Postpass](https://postpass.geofabrik.de)** ([docs](https://github.com/woodpeck/postpass-ops)), Geofabrik's
public PostGIS copy of the whole OSM planet, kept current from OSM's minutely diffs; set `POSTPASS_URL` for another
instance. One SQL query reads every station in the world (a few seconds, about 20 MB); then one query per batch of up
to 1,500 places (boxes of the world, quartered where dense) finds each place's country and park and the place nodes
around it. About 160 requests and an hour (October 2026), one at a time, with a pause in between and a growing
back-off when the server is busy; a request that keeps failing stops the run. The public Overpass API was the first choice, but its instances
answered "at capacity" for hours and its main one now asks automated users to run their own; a planet or Geofabrik
extract would mean downloading about 80 GB for a few hundred thousand objects.

## The job

`refresh_parks.py` runs `osm_parks.py`, checks the result against the published file and publishes:

- `gs://<project>-parks/parks/spots.tsv.gz`: the file above, gzip;
- `gs://<project>-parks/parks/manifest.json`: count, SHA-256, source and build time.

It publishes nothing when a request keeps failing, when the run finds fewer than 0.8 × the published parks, or when the
content is unchanged. The last line of each execution is `PARKS PASS …` or `PARKS FAIL …` in Cloud Logging.

## The app side

With `parks_url` set (in the git-ignored `app/src/release/res/values/firebase.xml`), opening Parks checks the file at
most once a week with a conditional request: unchanged costs a few bytes; a new file (about four times a year) is
downloaded once, checked the same way and kept on the phone. Without `parks_url`, or offline, the bundled copy is used.
A phone takes a new file only when it has at least 0.8 × the parks it has now.

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
`allUsers` binding; then the app keeps the bundled copy. Run it again after changing the job, then run the job once.

## Locally

From the suite root, with any Python 3.10+ (no packages):

```powershell
python streetgymnastics\tools\parks_job\osm_parks.py --out streetgymnastics\app\src\main\assets\spots.tsv --cache <dir>
python streetgymnastics\tools\parks_job\osm_parks.py --box 37.8,23.5,38.2,24.0 --out athens.tsv   # one box, to try
python streetgymnastics\tools\parks_job\refresh_parks.py --dry-run                                  # the job, publishing nothing
python -m pytest streetgymnastics\tools\parks_job                                                   # no network
```

`--cache` keeps every answer, so a second run (say, after changing the names) asks the server nothing.

Cost: four executions of about an hour a year (inside Cloud Run's and Cloud Scheduler's free tiers), a file of a few MB in
storage, and the downloads: each Plus phone fetches the file about four times a year.
