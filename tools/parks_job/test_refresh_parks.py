"""The parks refresh without the network or Cloud Storage: python -m pytest streetgymnastics/tools/parks_job"""
import random

import refresh_parks as job


def feature(slug, lng, lat, name=None):
    return {"geometry": {"coordinates": [lng, lat]},
            "properties": {"id": slug, "name": name or f"{slug} - Spot", "url": f"/en/spots/{slug}/"}}


def fake_api(points, cap=50):
    def fetch(params):
        box = (params["west"], params["south"], params["east"], params["north"])
        inside = sorted(s for s, (x, y) in points.items() if box[0] <= x <= box[2] and box[1] <= y <= box[3])
        return {"features": [feature(s, *points[s]) for s in inside[:cap]], "truncated": len(inside) > cap}
    return fetch


def test_the_crawl_finds_every_park_of_the_world():
    rng = random.Random(3)
    points = {f"p{i}": (rng.uniform(-180, 180), rng.uniform(-60, 70)) for i in range(1500)}
    found, boxes = job.crawl(fake_api(points))
    assert set(found) == set(points) and boxes > 1


def test_stacked_parks_stop_the_splitting():
    found, _ = job.crawl(fake_api({f"s{i}": (23.7, 37.9) for i in range(60)}))
    assert len(found) == 50


def test_names_are_cleaned():
    assert job.clean("Berlin - Outdoor pull up bars - Moabit - Germany - Spot") == "Berlin - Outdoor pull up bars - Moabit - Germany"
    assert job.clean("- Italy - Spot") == "Italy"
    assert job.clean("  A\tB  -  - C ") == "A B - C"


def test_a_feature_without_a_slug_a_name_or_coordinates_is_skipped():
    assert job.park(feature("x", 10.0, 50.0)) == ("x", "x", 50.0, 10.0)
    no_slug = feature("x", 10.0, 50.0); no_slug["properties"].update(id=None, url=None)
    assert job.park(no_slug) is None
    assert job.park(feature("x", 0, 0)) is None
    assert job.park(feature("x", 200, 10)) is None
    assert job.park(feature("x", 10, 50, name=" - Spot")) is None


def test_the_file_is_sorted_with_duplicates_once():
    content = job.tsv({"a": ("Zeta", 1.0, 2.0), "b": ("Alpha", 3.0, 4.0), "c": ("Zeta", 1.0, 2.0)})
    assert content == b"Alpha\t3.00000\t4.00000\nZeta\t1.00000\t2.00000\n"


def test_a_run_far_below_the_published_file_is_not_published():
    assert job.check(0, None) == "no parks found"
    assert job.check(100, None) is None
    assert job.check(79, {"count": 100})
    assert job.check(80, {"count": 100}) is None
