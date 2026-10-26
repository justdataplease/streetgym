"""Street Gymnastics parks refresh: the Cloud Run Job behind Parks near me (README.md in this folder).

Every three months Cloud Scheduler starts it. It reads every howtocalisthenics.com spot (name, latitude, longitude)
from the site's map API, splitting any box the API truncates into quarters (the same method as
scrape-properties/howtocalisthenics), checks the result against the published file, and publishes

    gs://$PARKS_BUCKET/parks/spots.tsv.gz    one park per line: name<TAB>lat<TAB>lng, sorted, gzip
    gs://$PARKS_BUCKET/parks/manifest.json   {"count", "sha256", "built_at"}

The app keeps its bundled copy and downloads the new file only when it changed. Nothing is published when the run
finds fewer than MIN_SHARE of the published parks, or when the content is unchanged.

    python refresh_parks.py              the job
    python refresh_parks.py --dry-run    scrape and check, print the summary, publish nothing
"""
import argparse
import gzip
import hashlib
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

MAP_URL = "https://howtocalisthenics.com/en/spots/api/map/"
WORLD = (-180.0, -90.0, 180.0, 90.0)
MIN_BOX = 0.0005      # degrees (~50 m): a box this small is not split again
WORKERS = 4
DELAY = 0.25          # seconds each worker waits after a request
ATTEMPTS = 5
MIN_SHARE = 0.8       # a run needs this share of the published parks
HEADERS = {"User-Agent": "Mozilla/5.0 (StreetGym parks refresh)", "Accept": "application/json"}


def log(text: str) -> None:
    print(text, flush=True)


def get(params: dict) -> dict:
    url = f"{MAP_URL}?{urllib.parse.urlencode(params)}"
    for attempt in range(1, ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as response:
                body = json.load(response)
            time.sleep(DELAY)
            return body
        except Exception as exc:  # HTTP errors, timeouts, a page that is not JSON
            why = f"{type(exc).__name__}: {exc}"
        if attempt < ATTEMPTS:
            time.sleep(2 ** attempt)
    raise RuntimeError(f"{why} after {ATTEMPTS} attempts: {url}")


def clean(name: str) -> str:
    """One line; without the " - Spot" every name ends with and without empty " - " parts."""
    parts = [part.strip("- ").strip() for part in " ".join((name or "").split()).split(" - ")]
    if parts and parts[-1] == "Spot":
        parts.pop()
    return " - ".join(part for part in parts if part)


def park(feature: dict):
    """(slug, name, lat, lng), or None without a slug, a name or valid coordinates."""
    props = feature.get("properties") or {}
    slug = props.get("id") or (props.get("url") or "").strip("/").rpartition("/")[2]
    name = clean(props.get("name") or "")
    try:
        lng, lat = (float(v) for v in feature["geometry"]["coordinates"][:2])
    except (KeyError, TypeError, ValueError):
        return None
    if not (slug and name and math.isfinite(lat) and math.isfinite(lng)
            and -90 <= lat <= 90 and -180 <= lng <= 180 and (lat, lng) != (0, 0)):
        return None
    return str(slug), name, round(lat, 5), round(lng, 5)


def quarters(box: tuple) -> list:
    west, south, east, north = box
    x, y = round((west + east) / 2, 6), round((south + north) / 2, 6)
    return [(west, south, x, y), (x, south, east, y), (west, y, x, north), (x, y, east, north)]


def crawl(fetch=get) -> tuple:
    """({slug: (name, lat, lng)}, boxes asked): from the whole world down, a truncated box asked again in quarters."""
    found, level, asked = {}, [WORLD], 0

    def ask(box):
        west, south, east, north = box
        return fetch({"north": north, "south": south, "east": east, "west": west})

    with ThreadPoolExecutor(WORKERS) as pool:
        while level:
            results = list(pool.map(ask, level))
            asked += len(level)
            deeper = []
            for box, body in zip(level, results):
                for feature in body.get("features") or []:
                    row = park(feature)
                    if row:
                        found[row[0]] = row[1:]
                if body.get("truncated") and max(box[2] - box[0], box[3] - box[1]) > MIN_BOX:
                    deeper += quarters(box)
            log(f"level: {len(level)} boxes, {len(deeper)} quarters next, {len(found):,} parks so far")
            level = deeper
    return found, asked


def tsv(parks: dict) -> bytes:
    """One park per line, sorted, duplicates (same name and place) once."""
    lines = sorted({f"{name}\t{lat:.5f}\t{lng:.5f}" for name, lat, lng in parks.values()})
    return ("\n".join(lines) + "\n").encode("utf-8")


def check(count: int, published: dict | None) -> str | None:
    """The reason not to publish, or None."""
    if count == 0:
        return "no parks found"
    if published and count < MIN_SHARE * published.get("count", 0):
        return f"{count:,} parks, below {MIN_SHARE} x the {published['count']:,} published"
    return None


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--dry-run", action="store_true", help="scrape and check; publish nothing")
    args = parser.parse_args(argv)
    bucket_name = os.environ.get("PARKS_BUCKET")
    if not bucket_name and not args.dry_run:
        log("REFUSED: set PARKS_BUCKET")
        return 2

    parks, boxes = crawl()
    content = tsv(parks)
    digest = hashlib.sha256(content).hexdigest()
    count = content.count(b"\n")

    published = None
    bucket = None
    if bucket_name:
        from google.cloud import storage
        bucket = storage.Client().bucket(bucket_name)
        manifest = bucket.blob("parks/manifest.json")
        published = json.loads(manifest.download_as_text()) if manifest.exists() else None

    problem = check(count, published)
    if problem:
        log(f"PARKS FAIL: {problem}; nothing was published ({boxes} boxes)")
        return 1
    if published and published.get("sha256") == digest:
        log(f"PARKS PASS: {count:,} parks, unchanged; nothing to publish ({boxes} boxes)")
        return 0
    if args.dry_run:
        log(f"PARKS PASS (dry run): {count:,} parks, {len(content):,} bytes, {boxes} boxes; nothing published")
        return 0

    data = bucket.blob("parks/spots.tsv.gz")
    data.cache_control = "public, max-age=3600"
    data.upload_from_string(gzip.compress(content, mtime=0), content_type="application/gzip")
    manifest = bucket.blob("parks/manifest.json")
    manifest.cache_control = "public, max-age=3600"
    manifest.upload_from_string(json.dumps({"count": count, "sha256": digest,
                                            "built_at": datetime.now(timezone.utc).isoformat()}),
                                content_type="application/json")
    log(f"PARKS PASS: {count:,} parks published (previously {published['count'] if published else 'none'}), "
        f"{boxes} boxes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
