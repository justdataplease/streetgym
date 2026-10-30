"""Street Gym's parks from OpenStreetMap: the app's parks file (app/src/main/assets/spots.tsv) and the job's.

Park data © OpenStreetMap contributors, under the Open Database License (ODbL 1.0,
https://www.openstreetmap.org/copyright). The app credits it on the Parks screen, and the file's first line says so.

What counts as a park: an outdoor place to train with your body weight, as mapped in OSM:
  - leisure=fitness_station: one station or a whole fitness area (fitness_station=* names the equipment);
  - sport=calisthenics or sport=street_workout (often on a leisure=pitch; the exact value, not in a list);
  - leisure=pitch with sport=fitness (an outdoor fitness area).
Left out: indoor places, buildings, gyms (leisure=fitness_centre), access=private or no, a lone sign, and places whose
tagged equipment is only machines or trail exercises without a bar (elliptical trainers, sit-up benches, balance
beams …). Untagged equipment counts: most stations don't say what they are.

Stations within LINK metres of each other, or inside a small fitness area, are one place. Each place is named like the
list the app had before, "City - Name - Park or neighbourhood - Country", in English where OSM has an English name:
the place's OSM name (else "Workout park"), the park it lies in (else the nearest neighbourhood), the town or city it
is in or nearest to, and the country.

Source: Postpass (https://postpass.geofabrik.de), Geofabrik's public PostGIS copy of the whole OSM planet, kept up to
date from OSM's minutely diffs. One query reads every station in the world (seconds); then, for the places in
boxes of at most BATCH, one query each finds their country (the most specific boundary with an ISO 3166-1 code, so
Puerto Rico and Hong Kong are their own), their park and the place nodes around them. About 160 requests and an hour,
one at a time, with a pause in between and a growing back-off when the server is busy. The same OSM data gives the
same file: lines sorted, coordinates rounded to 5 decimals.

    python osm_parks.py --out PATH                 every park in the world
    python osm_parks.py --box S,W,N,E --out PATH   only the parks in this box (for a try)
    python osm_parks.py --cache DIR ...            keep each answer in DIR and reuse it on the next run
"""
import argparse
import gzip
import hashlib
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

POSTPASS = os.environ.get("POSTPASS_URL", "https://postpass.geofabrik.de/api/interpreter")
USER_AGENT = "StreetGym-parks/2.0 (+https://justdataplease.com/apps/streetgymnastics/)"
PAUSE = 2.0              # seconds after every request
ATTEMPTS = 6
BATCH = 1500             # places per where-query (a box with more is asked in quarters)
LINK = 50.0              # metres: stations closer than this are one place
AREA_MAX = 300.0         # metres (diagonal): a fitness area that holds the stations inside it
GENERIC = "Workout park"
NOTICE = ("# Street Gym parks from OpenStreetMap. Data © OpenStreetMap contributors, available under the Open Database "
          "License (ODbL 1.0): https://www.openstreetmap.org/copyright")

STATIONS_SQL = """SELECT osm_type, osm_id, tags,
  round(ST_Y(ST_PointOnSurface(geom))::numeric, 7) AS lat, round(ST_X(ST_PointOnSurface(geom))::numeric, 7) AS lng,
  round(ST_YMin(geom)::numeric, 7) AS s, round(ST_XMin(geom)::numeric, 7) AS w,
  round(ST_YMax(geom)::numeric, 7) AS n, round(ST_XMax(geom)::numeric, 7) AS e
FROM postpass_pointpolygon
WHERE tags @> '{"leisure":"fitness_station"}' OR tags @> '{"sport":"calisthenics"}'
   OR tags @> '{"sport":"street_workout"}' OR tags @> '{"leisure":"pitch","sport":"fitness"}'
ORDER BY osm_type, osm_id"""

# For each point: its country and park; then the cities and towns around the batch, and the villages and
# neighbourhoods within about 2 km of a point (grid cells of 0.02 degrees and their neighbours).
WHERE_SQL = """WITH p(i, lat, lng) AS (VALUES {values}),
pt AS MATERIALIZED (SELECT i, ST_SetSRID(ST_MakePoint(lng, lat), 4326) AS g FROM p),
cells AS MATERIALIZED (SELECT DISTINCT floor(lat * 50)::int + dy AS cy, floor(lng * 50)::int + dx AS cx
  FROM p, generate_series(-1, 1) dy, generate_series(-1, 1) dx)
SELECT 'w' AS k, pt.i AS id, c.tags->>'ISO3166-1:alpha2' AS iso, c.tags->>'name' AS name, c.tags->>'name:en' AS en,
  c.tags->>'int_name' AS int, k.tags->>'name' AS park, k.tags->>'name:en' AS park_en, k.tags->>'int_name' AS park_int,
  NULL AS place, NULL AS pop, NULL::float8 AS lat, NULL::float8 AS lng
FROM pt
LEFT JOIN LATERAL (SELECT tags FROM postpass_polygon
  WHERE geom && pt.g AND tags ? 'ISO3166-1:alpha2' AND tags @> '{{"boundary":"administrative"}}'
    AND ST_Contains(geom, pt.g)
  ORDER BY CASE WHEN tags->>'admin_level' ~ '^[0-9]+$' THEN (tags->>'admin_level')::int ELSE 0 END DESC,
    tags->>'ISO3166-1:alpha2' DESC
  LIMIT 1) c ON true
LEFT JOIN LATERAL (SELECT tags FROM postpass_polygon
  WHERE geom && pt.g AND tags @> '{{"leisure":"park"}}' AND tags ? 'name' AND ST_Contains(geom, pt.g)
  ORDER BY area_m2, osm_id LIMIT 1) k ON true
UNION ALL
SELECT 'p', osm_id, NULL, tags->>'name', tags->>'name:en', tags->>'int_name', NULL, NULL, NULL,
  tags->>'place', tags->>'population', ST_Y(geom), ST_X(geom)
FROM postpass_point
WHERE (tags @> '{{"place":"city"}}' OR tags @> '{{"place":"town"}}') AND tags ? 'name'
  AND geom && ST_MakeEnvelope({west_far}, {south_far}, {east_far}, {north_far}, 4326)
UNION ALL
SELECT 'p', q.osm_id, NULL, q.tags->>'name', q.tags->>'name:en', q.tags->>'int_name', NULL, NULL, NULL,
  q.tags->>'place', NULL, ST_Y(q.geom), ST_X(q.geom)
FROM postpass_point q JOIN cells ON cells.cy = floor(ST_Y(q.geom) * 50)::int AND cells.cx = floor(ST_X(q.geom) * 50)::int
WHERE (q.tags @> '{{"place":"village"}}' OR q.tags @> '{{"place":"suburb"}}' OR q.tags @> '{{"place":"quarter"}}'
    OR q.tags @> '{{"place":"neighbourhood"}}') AND q.tags ? 'name'
  AND q.geom && ST_MakeEnvelope({west_near}, {south_near}, {east_near}, {north_near}, 4326)"""

# fitness_station=* values: bars and the like count; machines and trail exercises alone don't; a sign says nothing.
BODYWEIGHT = re.compile(r"bar|ladder|ring|push|pull|dip|chin|captain|calisthenic|workout|structure|rope|climb|monkey")
SIGNS = {"sign", "information", "board", "map", "start", "finish"}
NOT_BODYWEIGHT = {
    "sit-up", "situp", "balance_beam", "balance", "stairs", "elliptical_trainer", "air_walker", "exercise_bike",
    "bike", "rower", "chest_press", "ab_twist", "leg_press", "stepping_stone", "beam_jump", "steering_wheel", "slalom",
    "stretch_bars", "stretching", "leg_stretching", "slackline", "pull_down", "ski_swing", "stepper", "hurdling",
    "log_lifting", "leapfrog", "fly", "outdoor_exercise_machines", "hyperextension", "box", "back_massager",
    "hand_bike", "shoulder_wheel", "waist_twister", "twister", "surfboard", "pendulum", "massage", "cross_trainer",
    "walker", "jump", "hop",
}
EXCLUDED_LEISURE = {"fitness_centre", "sports_hall", "dance", "sauna", "bowling_alley", "amusement_arcade"}

# Place nodes: how far a place's name reaches (km); a city's grows with its population.
REACH_KM = {"city": 8.0, "town": 4.0, "village": 1.5}
NEIGHBOURHOODS = {"suburb", "quarter", "neighbourhood"}
NEIGHBOURHOOD_KM = 1.5
CITY_MAX_KM = 30.0
FAR = 0.3    # degrees around a batch for its cities and towns
NEAR = 0.03  # degrees around a batch for its villages and neighbourhoods

LATIN = re.compile(r"^[\x00-ɏḀ-ỿ -⁯\s]*$")
NUMBERED = re.compile(r"^\W*(station|stazione|estaci[oó]n|posten|nr|no|n°|#)?\.?\s*\d+[a-z]?\W*$", re.IGNORECASE)


def log(text: str) -> None:
    print(text, flush=True)


# ---------------------------------------------------------------- Postpass

def postpass(sql: str, opener=urllib.request.urlopen) -> list:
    """One query's rows, retried with a growing pause when the server is busy or the connection fails."""
    data = urllib.parse.urlencode({"data": sql, "options[geojson]": "false"}).encode()
    why = ""
    for attempt in range(1, ATTEMPTS + 1):
        try:
            request = urllib.request.Request(POSTPASS, data=data, headers={"User-Agent": USER_AGENT})
            with opener(request, timeout=1800) as response:
                body = json.load(response)
            time.sleep(PAUSE)
            return body["result"]
        except urllib.error.HTTPError as exc:   # 400 is a bad query; 429 and 5xx: wait and ask again
            why = f"HTTP {exc.code}: {exc.read()[:300].decode('utf-8', 'replace').strip()}"
            if exc.code == 400:
                raise RuntimeError(f"bad query: {why}") from exc
        except Exception as exc:   # timeouts, a cut connection, a page that is not JSON
            why = f"{type(exc).__name__}: {exc}"
        if attempt < ATTEMPTS:
            wait = min(600, 15 * 2 ** (attempt - 1))
            log(f"  {why}; again in {wait} s")
            time.sleep(wait)
    raise RuntimeError(f"{why} after {ATTEMPTS} attempts")


def cached(cache, key: str, ask):
    """ask() once, its answer kept as cache/key.json.gz when cache is a directory."""
    if cache is None:
        return ask()
    path = Path(cache) / f"{key}.json.gz"
    if path.exists():
        return json.loads(gzip.decompress(path.read_bytes()))
    body = ask()
    path.parent.mkdir(parents=True, exist_ok=True)
    part = path.with_suffix(".part")
    part.write_bytes(gzip.compress(json.dumps(body, ensure_ascii=False).encode("utf-8"), mtime=0))
    part.replace(path)
    return body


def quarters(box: tuple) -> list:
    south, west, north, east = box
    lat, lng = (south + north) / 2, (west + east) / 2
    return [(south, west, lat, lng), (south, lng, lat, east), (lat, west, north, lng), (lat, lng, north, east)]


def in_box(point: tuple, box: tuple) -> bool:
    """(i, lat, lng) in (S, W, N, E): the south and west edges inside, north and east only at the world's edge."""
    south, west, north, east = box
    return ((south <= point[1] < north or point[1] == north == 90.0)
            and (west <= point[2] < east or point[2] == east == 180.0))


def batches(points: list, size: int = BATCH) -> list:
    """The (i, lat, lng) points in boxes of at most size (dense boxes quartered), each box's points in a list."""
    out, todo = [], [((-90.0, -180.0, 90.0, 180.0), points)]
    while todo:
        box, inside = todo.pop()
        if len(inside) <= size or box[2] - box[0] < 0.01:
            if inside:
                out.append(inside)
            continue
        todo += [(part, [p for p in inside if in_box(p, part)]) for part in quarters(box)]
    return sorted(out, key=lambda b: (b[0][1], b[0][2]))


def where_sql(batch: list) -> str:
    south = min(p[1] for p in batch)
    north = max(p[1] for p in batch)
    west = min(p[2] for p in batch)
    east = max(p[2] for p in batch)
    return WHERE_SQL.format(
        values=",".join(f"({i},{lat:.6f},{lng:.6f})" for i, lat, lng in batch),
        south_far=south - FAR, west_far=west - FAR, north_far=north + FAR, east_far=east + FAR,
        south_near=south - NEAR, west_near=west - NEAR, north_near=north + NEAR, east_near=east + NEAR)


def fetch_where(points: list, cache=None, ask=postpass) -> tuple:
    """({i: row}, place rows) for the (i, lat, lng) points, a batch at a time."""
    where, place_rows = {}, {}
    groups = batches(points)
    for n, batch in enumerate(groups, 1):
        key = "where_" + hashlib.sha1(repr(batch).encode()).hexdigest()[:16]
        for row in cached(cache, key, lambda: ask(where_sql(batch))):
            if row.get("k") == "w":
                where[int(row["id"])] = row
            elif row.get("k") == "p":
                place_rows[int(row["id"])] = row
        if n % 10 == 0 or n == len(groups):
            log(f"where: {n} of {len(groups)} batches, {len(where):,} places, {len(place_rows):,} place names")
    return where, [place_rows[k] for k in sorted(place_rows)]


# ---------------------------------------------------------------- names

def tidy(text) -> str:
    return " ".join(str(text or "").replace(" - ", " – ").split())[:80].strip(" -–")


def english(tags: dict, name_key: str = "name", en_key: str = "name:en", int_key: str = "int_name") -> str:
    """The English name where OSM has one, else the name (a Latin int_name before a name in another script)."""
    name, en = tidy(tags.get(name_key)), tidy(tags.get(en_key))
    if en:
        return en
    if name and not LATIN.match(name) and tidy(tags.get(int_key)):
        return tidy(tags.get(int_key))
    return name


def population(text) -> int:
    digits = re.sub(r"[^\d]", "", str(text or "").split(";")[0].split(".")[0])
    return int(digits) if digits and len(digits) < 11 else 0


def joined(*parts: str) -> str:
    """The parts with " - " between them, without empty ones or one the name already says."""
    out = []
    for part in parts:
        if part and not any(part.casefold() == seen.casefold() for seen in out):
            out.append(part)
    return " - ".join(out)


# ---------------------------------------------------------------- stations and places

def wanted(tags: dict) -> bool:
    """An outdoor, public workout station or area (see the module's description)."""
    sport = {s.strip() for s in (tags.get("sport") or "").split(";")}
    leisure = tags.get("leisure")
    if not (leisure == "fitness_station" or sport & {"calisthenics", "street_workout"}
            or (leisure == "pitch" and "fitness" in sport)):
        return False
    if leisure in EXCLUDED_LEISURE or "building" in tags or "shop" in tags or tags.get("amenity") == "gym":
        return False
    if tags.get("indoor") in {"yes", "room", "area"} or tags.get("location") in {"indoor", "underground"}:
        return False
    return tags.get("access") not in {"private", "no", "customers", "members"}


def equipment(tags: dict) -> str:
    """'bodyweight' (bars and the like), 'other' (only machines or trail exercises), 'sign' (only a sign), or
    'unknown' (not tagged, or unclear)."""
    if {s.strip() for s in (tags.get("sport") or "").split(";")} & {"calisthenics", "street_workout"}:
        return "bodyweight"
    values = [v.strip().lower() for v in (tags.get("fitness_station") or "").split(";") if v.strip()]
    if any(BODYWEIGHT.search(v) and v not in NOT_BODYWEIGHT for v in values):
        return "bodyweight"
    if values and all(v in SIGNS for v in values):
        return "sign"
    if values and all(v in NOT_BODYWEIGHT or v in SIGNS for v in values):
        return "other"
    return "unknown"


class Station:
    __slots__ = ("key", "lat", "lng", "box", "tags", "area")

    def __init__(self, key, lat, lng, box, tags, area):
        self.key, self.lat, self.lng, self.box, self.tags, self.area = key, lat, lng, box, tags, area


def stations(rows: list, box=None) -> list:
    """The wanted stations (only those in box = (S, W, N, E) when given), each once, in a fixed order."""
    found = {}
    for row in rows:
        tags = row.get("tags") or {}
        try:
            lat, lng = float(row["lat"]), float(row["lng"])
        except (KeyError, TypeError, ValueError):
            continue
        if not wanted(tags) or not (-90 <= lat <= 90 and -180 <= lng <= 180):
            continue
        if box and not (box[0] <= lat <= box[2] and box[1] <= lng <= box[3]):
            continue
        area = row.get("osm_type") != "N"
        bounds = tuple(float(row[k]) for k in ("s", "w", "n", "e")) if area and row.get("s") is not None else None
        key = (row.get("osm_type") or "?", int(row["osm_id"]))
        found[key] = Station(key, lat, lng, bounds, tags, area and bounds is not None)
    return [found[key] for key in sorted(found)]


def places(rows: list) -> list:
    """(kind, name, lat, lng, population) for every named place node."""
    out = []
    for row in rows:
        name = english({"name": row.get("name"), "name:en": row.get("en"), "int_name": row.get("int")})
        if name and row.get("place") in set(REACH_KM) | NEIGHBOURHOODS and row.get("lat") is not None:
            out.append((row["place"], name, float(row["lat"]), float(row["lng"]), population(row.get("pop"))))
    return out


def metres(lat1, lng1, lat2, lng2) -> float:
    """Equirectangular distance, plenty for the short ones here."""
    x = math.radians((lng2 - lng1 + 540) % 360 - 180) * math.cos(math.radians((lat1 + lat2) / 2))
    y = math.radians(lat2 - lat1)
    return 6371000.0 * math.hypot(x, y)


def box_of(station) -> tuple:
    return station.box or (station.lat, station.lng, station.lat, station.lng)


def box_gap(a: tuple, b: tuple) -> float:
    """Metres between two boxes (0 when they touch or overlap)."""
    lat = max(0.0, a[0] - b[2], b[0] - a[2])
    lng = max(0.0, a[1] - b[3], b[1] - a[3])
    mid = math.radians((a[0] + a[2] + b[0] + b[2]) / 4)
    return 6371000.0 * math.hypot(math.radians(lat), math.radians(lng) * math.cos(mid))


def diagonal(box: tuple) -> float:
    return metres(box[0], box[1], box[2], box[3])


def cluster(found: list) -> list:
    """Groups of stations: within LINK metres of each other, or inside the same small fitness area."""
    parent = list(range(len(found)))

    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    cell = LINK / 111_320.0
    grid = defaultdict(list)
    boxes = []
    for index, station in enumerate(found):
        box = box_of(station)
        if diagonal(box) > AREA_MAX:   # a big area is linked by its point only
            box = (station.lat, station.lng, station.lat, station.lng)
        boxes.append(box)
        scale = max(0.05, math.cos(math.radians(station.lat)))
        for y in range(math.floor(box[0] / cell), math.floor(box[2] / cell) + 1):
            for x in range(math.floor(box[1] * scale / cell), math.floor(box[3] * scale / cell) + 1):
                grid[(y, x)].append(index)
    for (y, x), members in grid.items():
        near = set()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                near.update(grid.get((y + dy, x + dx), ()))
        for i in members:
            for j in near:
                if i < j and root(i) != root(j) and box_gap(boxes[i], boxes[j]) <= LINK:
                    parent[root(i)] = root(j)
    groups = defaultdict(list)
    for index in range(len(found)):
        groups[root(index)].append(found[index])
    return sorted(groups.values(), key=lambda g: min(s.key for s in g))


def position(group: list) -> tuple:
    """The largest small area's point, else the station nearest the group's centre."""
    areas = [s for s in group if s.area and diagonal(s.box) <= AREA_MAX * 3]
    if areas:
        best = max(areas, key=lambda s: (diagonal(s.box), s.key))
        return best.lat, best.lng
    lat = sum(s.lat for s in group) / len(group)
    lng = sum(s.lng for s in group) / len(group)
    best = min(group, key=lambda s: (metres(lat, lng, s.lat, s.lng), s.key))
    return best.lat, best.lng


def keep(group: list) -> bool:
    """False for a lone sign, or when every station that says what it is has only machines or trail exercises."""
    kinds = {equipment(s.tags) for s in group} - {"sign"}
    return bool(kinds) and ("bodyweight" in kinds or "other" not in kinds)


def label(group: list) -> str:
    """The place's own OSM name: a named area's, a calisthenics station's, or the one name its stations share."""
    def usable(station):
        name = english(station.tags)
        return name if name and not NUMBERED.match(name) else ""

    areas = sorted((s for s in group if s.area and usable(s)), key=lambda s: (-diagonal(s.box), s.key))
    if areas:
        return usable(areas[0])
    calisthenics = sorted((s for s in group if equipment(s.tags) == "bodyweight" and s.tags.get("sport")
                           and usable(s)), key=lambda s: s.key)
    if calisthenics:
        return usable(calisthenics[0])
    names = {usable(s) for s in group} - {""}
    # Several names on one group are mostly the equipment's ("Barren", "Klimmzug"): not the place's name.
    return names.pop() if len(names) == 1 else ""


class Places:
    """Place nodes in a grid, for the town or city a park is in and the neighbourhood around it."""

    CELL = 0.1   # degrees

    def __init__(self, rows: list):
        self.grid = defaultdict(list)
        for row in rows:
            self.grid[(math.floor(row[2] / self.CELL), math.floor(row[3] / self.CELL))].append(row)

    def near(self, lat: float, lng: float, km: float):
        dy = math.ceil(km / (111.32 * self.CELL))
        dx = math.ceil(km / (111.32 * self.CELL * max(0.05, math.cos(math.radians(lat)))))
        y, x = math.floor(lat / self.CELL), math.floor(lng / self.CELL)
        for yy in range(y - dy, y + dy + 1):
            for xx in range(x - dx, x + dx + 1):
                for row in self.grid.get((yy, (xx + 1800) % 3600 - 1800), ()):   # across the antimeridian too
                    d = metres(lat, lng, row[2], row[3]) / 1000
                    if d <= km:
                        yield d, row

    def city(self, lat: float, lng: float) -> str:
        """The village, town or city whose reach covers the park best (distance / reach). A big city's reach takes in
        the towns around it, so London parks are London's (and, without boundaries, Hoboken's are New York's)."""
        best = None
        for d, (kind, name, _, _, pop) in self.near(lat, lng, CITY_MAX_KM):
            if kind not in REACH_KM:
                continue
            reach = REACH_KM[kind]
            if kind == "city" and pop:
                reach = min(30.0, max(6.0, 8.0 * (pop / 500_000) ** (1 / 3)))
            score = (d / reach, d, name)
            if best is None or score < best:
                best = score
        return best[2] if best else ""

    def neighbourhood(self, lat: float, lng: float) -> str:
        found = [(d, name) for d, (kind, name, *_) in self.near(lat, lng, NEIGHBOURHOOD_KM) if kind in NEIGHBOURHOODS]
        return min(found)[1] if found else ""


def country_and_park(row: dict) -> tuple:
    """The country and the park from a where-row (the query already picked the most specific country)."""
    country = english({"name": row.get("name"), "name:en": row.get("en"), "int_name": row.get("int")})
    park = english({"name": row.get("park"), "name:en": row.get("park_en"), "int_name": row.get("park_int")})
    return country, park


def build(rows: list, where=None, box=None) -> tuple:
    """(parks as [(name, lat, lng)], counts) from the station rows; where(points) gives ({i: row}, place rows)."""
    found = stations(rows, box)
    groups = cluster(found)
    kept = [g for g in groups if keep(g)]
    points = [(i, *position(g)) for i, g in enumerate(kept)]
    areas, place_rows = where(points) if where else ({}, [])
    index = Places(places(place_rows))
    parks, named, in_park = [], 0, 0
    for (i, lat, lng), group in zip(points, kept):
        country, park = country_and_park(areas.get(i, {}))
        name = label(group)
        named += bool(name)
        park = park if park and park.casefold() not in name.casefold() else ""
        in_park += bool(park)
        # An unnamed place also gets its neighbourhood, so a search for it finds the place.
        area = "" if name else index.neighbourhood(lat, lng)
        area = area if area and area.casefold() not in park.casefold() else ""
        parks.append((joined(index.city(lat, lng), name or GENERIC, park, area, country), round(lat, 5), round(lng, 5)))
    counts = {
        "stations": len(found),
        "places": len(groups),
        "kept": len(kept),
        "left_out": len(groups) - len(kept),
        "named": named,
        "in_a_park": in_park,
        "bodyweight": sum(any(equipment(s.tags) == "bodyweight" for s in g) for g in kept),
        "countries": dict(Counter(n.rsplit(" - ", 1)[-1] for n, *_ in parks).most_common()),
    }
    return parks, counts


def tsv(parks) -> bytes:
    """The ODbL notice, then one park per line, name<TAB>lat<TAB>lng, sorted, duplicates (same name and place) once.
    The app skips the notice like any line without two tabs."""
    lines = sorted({f"{name}\t{lat:.5f}\t{lng:.5f}" for name, lat, lng in parks if name})
    return ("\n".join([NOTICE] + lines) + "\n").encode("utf-8")


def park_lines(content: bytes) -> int:
    """The parks in a file tsv() wrote: its lines but the notice."""
    return sum(1 for line in content.decode("utf-8").splitlines() if line.count("\t") == 2)


def run(box=None, cache=None) -> tuple:
    """Fetch and build: (parks, counts)."""
    rows = cached(cache, "stations", lambda: postpass(STATIONS_SQL))
    log(f"stations: {len(rows):,} rows")
    return build(rows, lambda points: fetch_where(points, cache), box)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--out", required=True, help="where to write the parks file")
    parser.add_argument("--box", help="S,W,N,E: only the parks in this box")
    parser.add_argument("--cache", help="a directory for the Postpass answers")
    args = parser.parse_args(argv)
    box = tuple(float(v) for v in args.box.split(",")) if args.box else None
    started = time.time()
    parks, counts = run(box, args.cache)
    content = tsv(parks)
    Path(args.out).write_bytes(content)
    top = list(counts.pop("countries").items())[:15]
    log(json.dumps(counts))
    log("top countries: " + ", ".join(f"{c or '?'} {n:,}" for c, n in top))
    log(f"{park_lines(content):,} parks, {len(content):,} bytes -> {args.out} ({time.time() - started:,.0f} s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
