"""The OSM parks generator without the network: python -m pytest streetgymnastics/tools/parks_job"""
import random

import osm_parks as osm


def node(i, lat, lng, **tags):
    return {"osm_type": "N", "osm_id": i, "tags": tags, "lat": lat, "lng": lng, "s": lat, "w": lng, "n": lat, "e": lng}


def area(i, south, west, north, east, **tags):
    return {"osm_type": "W", "osm_id": i, "tags": tags, "lat": (south + north) / 2, "lng": (west + east) / 2,
            "s": south, "w": west, "n": north, "e": east}


def place(i, lat, lng, kind, name, en=None, pop=None):
    return {"k": "p", "id": i, "name": name, "en": en, "int": None, "place": kind, "pop": pop, "lat": lat, "lng": lng}


FS = {"leisure": "fitness_station"}
M = 1 / 111_320   # about a metre of latitude, in degrees


def test_outdoor_public_workout_places_count():
    assert osm.wanted(FS)
    assert osm.wanted({"leisure": "pitch", "sport": "fitness"})
    assert osm.wanted({"leisure": "pitch", "sport": "soccer;calisthenics"})
    assert osm.wanted({"sport": "street_workout"})
    assert not osm.wanted({"leisure": "pitch", "sport": "soccer"})
    assert not osm.wanted({"leisure": "fitness_centre", "sport": "calisthenics"})
    assert not osm.wanted({**FS, "access": "private"})
    assert not osm.wanted({**FS, "indoor": "yes"})
    assert not osm.wanted({"sport": "calisthenics", "building": "yes"})


def test_equipment_says_bars_machines_signs_or_nothing():
    assert osm.equipment({**FS, "fitness_station": "horizontal_bar"}) == "bodyweight"
    assert osm.equipment({**FS, "fitness_station": "sit-up;parallel_bars"}) == "bodyweight"
    assert osm.equipment({**FS, "sport": "calisthenics"}) == "bodyweight"
    assert osm.equipment({**FS, "fitness_station": "elliptical_trainer;air_walker"}) == "other"
    assert osm.equipment({**FS, "fitness_station": "stretch_bars"}) == "other"
    assert osm.equipment({**FS, "fitness_station": "sign"}) == "sign"
    assert osm.equipment(FS) == "unknown"
    assert osm.equipment({**FS, "fitness_station": "yes"}) == "unknown"


def test_stations_within_50_m_are_one_place_and_an_area_holds_its_stations():
    found = osm.stations([
        node(1, 50.0, 8.0, **FS), node(2, 50.0 + 40 * M, 8.0, **FS), node(3, 50.0 + 80 * M, 8.0, **FS),   # a chain
        node(4, 50.01, 8.0, **FS),                                                                       # 1.1 km
        area(5, 50.02, 8.0, 50.02 + 150 * M, 8.0015, **FS), node(6, 50.02 + 140 * M, 8.0012, **FS),         # inside
        node(7, 50.0, 8.0, leisure="playground"),                                                        # not one
    ])
    groups = osm.cluster(found)
    assert sorted(sorted(s.key[1] for s in g) for g in groups) == [[1, 2, 3], [4], [5, 6]]


def test_a_box_keeps_only_its_stations():
    rows = [node(1, 37.9, 23.7, **FS), node(2, 52.5, 13.4, **FS)]
    assert [s.key for s in osm.stations(rows, box=(37.8, 23.5, 38.2, 24.0))] == [("N", 1)]


def test_a_place_with_only_machines_or_a_sign_is_left_out():
    def group(*values):
        return osm.stations([node(i, 50.0, 8.0, **FS, **({"fitness_station": v} if v else {}))
                             for i, v in enumerate(values)])
    assert osm.keep(group(None))
    assert osm.keep(group(None, "sign"))
    assert osm.keep(group("rower", "horizontal_bar"))
    assert not osm.keep(group("sign"))
    assert not osm.keep(group("rower", "air_walker"))
    assert not osm.keep(group(None, "elliptical_trainer"))


def test_the_name_is_the_areas_or_the_one_name_its_stations_share():
    named_area = osm.stations([area(1, 50.0, 8.0, 50.001, 8.001, **FS, name="Fitnesspark Nord"),
                               node(2, 50.0005, 8.0005, **FS, name="Barren")])
    assert osm.label(named_area) == "Fitnesspark Nord"
    equipment = osm.stations([node(1, 50.0, 8.0, **FS, name="Barren"), node(2, 50.0, 8.0, **FS, name="Reck")])
    assert osm.label(equipment) == ""
    shared = osm.stations([node(1, 50.0, 8.0, **FS, name="Calisthenics"), node(2, 50.0, 8.0, **FS)])
    assert osm.label(shared) == "Calisthenics"
    assert osm.label(osm.stations([node(1, 50.0, 8.0, **FS, name="Station 3")])) == ""
    assert osm.label(osm.stations([node(1, 37.9, 23.7, **FS, name="Γυμναστήριο", **{"name:en": "Outdoor gym"})])) \
        == "Outdoor gym"


def test_the_city_is_the_one_whose_reach_covers_the_park_best():
    index = osm.Places(osm.places([
        place(1, 37.9755, 23.7348, "city", "Αθήνα", "Athens", "3090508"),
        place(2, 38.02, 23.80, "village", "Village"),
        place(3, 37.98, 23.74, "suburb", "Κολωνάκι", "Kolonaki"),
        place(4, 38.0, 23.0, "hamlet", "Not a place we use"),
    ]))
    assert index.city(37.99, 23.76) == "Athens"          # 3 km from Athens, 5 km from the village
    assert index.city(38.021, 23.80) == "Village"        # 100 m from the village, 9 km from Athens
    assert index.city(0.0, 0.0) == ""
    assert index.neighbourhood(37.981, 23.741) == "Kolonaki"
    assert index.neighbourhood(37.99, 23.80) == ""


def test_country_and_park_in_english_where_osm_has_it():
    row = {"name": "Ελλάς", "en": "Greece", "park": "Πεδίον του Άρεως", "park_en": None, "park_int": "Pedion tou Areos"}
    assert osm.country_and_park(row) == ("Greece", "Pedion tou Areos")
    assert osm.country_and_park({}) == ("", "")


def test_build_names_each_place_city_name_park_neighbourhood_country():
    rows = [
        node(1, 37.9889, 23.7319, **FS, fitness_station="horizontal_bar"),
        node(2, 37.9890, 23.7320, **FS),   # the middle one stands for the place
        node(5, 37.9891, 23.7321, **FS),
        node(3, 37.9600, 23.7000, **FS, name="Street Workout Kallithea", sport="calisthenics"),
        node(4, 37.9300, 23.7400, **FS, fitness_station="air_walker"),
    ]
    greece = {"name": "Ελλάς", "en": "Greece"}

    def where(points):
        assert len(points) == 2   # the machines-only station is left out before asking
        return ({points[0][0]: {"k": "w", **greece, "park": "Πεδίον του Άρεως", "park_en": "Pedion tou Areos"},
                 points[1][0]: {"k": "w", **greece, "park": "Πάρκο", "park_en": "Park"}},
                [place(10, 37.9755, 23.7348, "city", "Αθήνα", "Athens", "3090508"),
                 place(11, 37.9900, 23.7300, "neighbourhood", "Κυψέλη", "Kypseli"),
                 place(12, 37.9610, 23.7010, "neighbourhood", "Καλλιθέα", "Kallithea")])

    parks, counts = osm.build(rows, where)
    assert sorted(parks) == [
        ("Athens - Street Workout Kallithea - Park - Greece", 37.96, 23.7),
        ("Athens - Workout park - Pedion tou Areos - Kypseli - Greece", 37.989, 23.732),
    ]
    assert counts["stations"] == 5 and counts["places"] == 3 and counts["left_out"] == 1
    assert counts["named"] == 1 and counts["in_a_park"] == 2 and counts["bodyweight"] == 2


def test_the_file_has_the_notice_then_sorted_parks_once():
    content = osm.tsv([("Zeta", 1.0, 2.0), ("Alpha", 3.0, 4.0), ("Zeta", 1.0, 2.0), ("", 0.5, 0.5)])
    assert content == (osm.NOTICE + "\nAlpha\t3.00000\t4.00000\nZeta\t1.00000\t2.00000\n").encode()
    assert "\t" not in osm.NOTICE and "OpenStreetMap contributors" in osm.NOTICE
    assert osm.park_lines(content) == 2


def test_names_are_tidy_and_their_parts_once():
    assert osm.tidy("  Park\t-  Nord ") == "Park - Nord"
    assert osm.tidy("A - B") == "A – B"
    assert osm.joined("Berlin", "Workout park", "", "berlin", "Germany") == "Berlin - Workout park - Germany"
    assert osm.population("3,090,508") == 3090508 and osm.population("about 5000") == 5000
    assert osm.population("") == 0


def test_batches_hold_every_point_once_and_dense_ones_are_split():
    rng = random.Random(5)
    points = [(i, rng.uniform(-60, 70), rng.uniform(-180, 180)) for i in range(3000)]
    points += [(3000 + i, 52.5 + rng.uniform(0, 0.1), 13.4 + rng.uniform(0, 0.1)) for i in range(2500)]
    points += [(9000, 90.0, 180.0), (9001, -90.0, -180.0)]
    groups = osm.batches(points, size=1000)
    assert sorted(p for g in groups for p in g) == sorted(points)
    assert max(len(g) for g in groups) <= 1000


def test_the_where_query_lists_the_batch_and_its_surroundings():
    sql = osm.where_sql([(7, 37.9, 23.7), (9, 38.0, 23.8)])
    assert "(7,37.900000,23.700000),(9,38.000000,23.800000)" in sql
    assert "ST_MakeEnvelope(23.4, 37.6" in sql and "{" not in sql.replace('{"', "").replace("{}", "")


def test_where_keeps_each_points_row_and_the_place_names_once():
    def ask(sql):
        assert sql.count("),(") == 1
        return [{"k": "w", "id": 7, "en": "Greece"}, {"k": "p", "id": 3, "name": "Athens"},
                {"k": "w", "id": 9, "en": None}, {"k": "p", "id": 3, "name": "Athens"}]
    where, rows = osm.fetch_where([(7, 37.9, 23.7), (9, 38.0, 23.8)], ask=ask)
    assert set(where) == {7, 9} and rows == [{"k": "p", "id": 3, "name": "Athens"}]
