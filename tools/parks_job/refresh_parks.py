"""Street Gymnastics parks refresh: the Cloud Run Job behind Parks near me (README.md in this folder).

Every three months Cloud Scheduler starts it. It builds the parks file from OpenStreetMap with osm_parks.py (outdoor
workout places read from the Overpass API; © OpenStreetMap contributors, ODbL), checks the result against the
published file, and publishes

    gs://$PARKS_BUCKET/parks/spots.tsv.gz    the OSM notice, then one park per line: name<TAB>lat<TAB>lng, sorted, gzip
    gs://$PARKS_BUCKET/parks/manifest.json   {"count", "sha256", "built_at", "source"}

The app keeps its bundled copy and downloads the new file only when it changed. Nothing is published when a request
keeps failing, when the run finds fewer than MIN_SHARE of the published parks, or when the content is unchanged.

    python refresh_parks.py              the job
    python refresh_parks.py --dry-run    build and check, print the summary, publish nothing
"""
import argparse
import gzip
import hashlib
import json
import os
import sys
from datetime import datetime, timezone

import osm_parks

MIN_SHARE = 0.8       # a run needs this share of the published parks
SOURCE = "OpenStreetMap via the Overpass API; © OpenStreetMap contributors, ODbL 1.0"


def log(text: str) -> None:
    print(text, flush=True)


def check(count: int, published: dict | None) -> str | None:
    """The reason not to publish, or None."""
    if count == 0:
        return "no parks found"
    if published and count < MIN_SHARE * published.get("count", 0):
        return f"{count:,} parks, below {MIN_SHARE} x the {published['count']:,} published"
    return None


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--dry-run", action="store_true", help="build and check; publish nothing")
    args = parser.parse_args(argv)
    bucket_name = os.environ.get("PARKS_BUCKET")
    if not bucket_name and not args.dry_run:
        log("REFUSED: set PARKS_BUCKET")
        return 2

    try:
        parks, counts = osm_parks.run()
    except Exception as exc:   # a request that kept failing: publish nothing rather than part of the world
        log(f"PARKS FAIL: {type(exc).__name__}: {exc}; nothing was published")
        return 1
    content = osm_parks.tsv(parks)
    digest = hashlib.sha256(content).hexdigest()
    count = osm_parks.park_lines(content)
    summary = f"{counts['stations']:,} stations, {counts['left_out']:,} places left out"

    published = None
    bucket = None
    if bucket_name:
        from google.cloud import storage
        bucket = storage.Client().bucket(bucket_name)
        manifest = bucket.blob("parks/manifest.json")
        published = json.loads(manifest.download_as_text()) if manifest.exists() else None

    problem = check(count, published)
    if problem:
        log(f"PARKS FAIL: {problem}; nothing was published ({summary})")
        return 1
    if published and published.get("sha256") == digest:
        log(f"PARKS PASS: {count:,} parks, unchanged; nothing to publish ({summary})")
        return 0
    if args.dry_run:
        log(f"PARKS PASS (dry run): {count:,} parks, {len(content):,} bytes, {summary}; nothing published")
        return 0

    data = bucket.blob("parks/spots.tsv.gz")
    data.cache_control = "public, max-age=3600"
    data.upload_from_string(gzip.compress(content, mtime=0), content_type="application/gzip")
    manifest = bucket.blob("parks/manifest.json")
    manifest.cache_control = "public, max-age=3600"
    manifest.upload_from_string(json.dumps({"count": count, "sha256": digest, "source": SOURCE,
                                            "built_at": datetime.now(timezone.utc).isoformat()}),
                                content_type="application/json")
    log(f"PARKS PASS: {count:,} parks published (previously {published['count'] if published else 'none'}), "
        f"{summary}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
