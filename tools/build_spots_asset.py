"""Turn a howtocalisthenics.com spots snapshot into the app's parks asset.

The snapshot comes from scrape-properties (python -m howtocalisthenics.howtocalisthenics): a JSON list of
{id, name, lat, lng}. The asset is one spot per line, "name<TAB>lat<TAB>lng", sorted by name, so the APK carries
names and coordinates only.

    python streetgymnastics/tools/build_spots_asset.py SNAPSHOT.json
"""
import json
import sys
from pathlib import Path

ASSET = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "spots.tsv"


def clean(name: str) -> str:
    """The name's " - " parts without the empty ones ("- Italy" -> "Italy"), on one line."""
    parts = [" ".join(part.split()) for part in " ".join(name.split()).split(" - ")]
    return " - ".join(part.strip("- ") for part in parts if part.strip("- "))


def main(snapshot: str) -> None:
    spots = json.loads(Path(snapshot).read_text(encoding="utf-8"))
    lines = sorted({
        f"{clean(spot['name'])}\t{spot['lat']:.5f}\t{spot['lng']:.5f}"
        for spot in spots
        if clean(spot.get("name") or "") and -90 <= spot["lat"] <= 90 and -180 <= spot["lng"] <= 180
    })
    ASSET.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(lines):,} spots, {ASSET.stat().st_size:,} bytes -> {ASSET}")


if __name__ == "__main__":
    main(sys.argv[1])
