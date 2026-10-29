"""The board builder without Firebase: python -m pytest streetgymnastics/firebase/functions"""
from boards import FAMILY_CEILINGS, TOP, VERSION, board_hash, build_boards, expected_overall


def score(name, country="GR", hidden=False, overall=None, v=VERSION, **families):
    """A current (version 3) score document; the overall adds up from the families unless given."""
    return {"v": v, "username": name, "country": country, "families": families, "verified": [], "hidden": hidden,
            "overall": expected_overall(families) if overall is None else overall}


def test_world_and_country_boards_rank_by_score_then_name():
    boards = build_boards([score("b", pullup=500), score("a", pullup=500), score("c", "DE", planche=900)])
    assert [e["username"] for e in boards["overall_world"]["entries"]] == ["c", "a", "b"]
    assert [e["username"] for e in boards["overall_GR"]["entries"]] == ["a", "b"]
    assert boards["overall_DE"]["players"] == 1


def test_each_family_gets_a_world_and_a_country_board_for_points_above_zero():
    boards = build_boards([score("a", pullup=200, dip=0), score("b", "DE", pullup=50)])
    assert [e["score"] for e in boards["family_pullup_world"]["entries"]] == [200, 50]
    assert "family_pullup_GR" in boards and "family_pullup_DE" in boards
    assert not any(board.startswith("family_dip") for board in boards)


def test_hidden_old_malformed_and_inconsistent_scores_are_left_out():
    boards = build_boards([
        score("a", pullup=100),
        score("h", hidden=True, pullup=100),
        score("old", v=1, pullup=100),
        score("v2", v=2, pullup=100),
        {**score("x", pullup=100), "country": "GRC"},
        {**score("slash", pullup=100), "country": "a/"},
        {**score("lower", pullup=100), "country": "gr"},
        {**score("n", pullup=100), "username": ""},
        score("inflated", overall=900, pullup=100),
        score("unknown", lever=100),
        score("retired", nordic=100),
        score("over", dip=10 * FAMILY_CEILINGS["dip"] + 1),
        score("negative", pullup=-5),
        score("max", overall=1001, **{family: 10 * top for family, top in FAMILY_CEILINGS.items()}),
    ])
    assert [e["username"] for e in boards["overall_world"]["entries"]] == ["a"]


def test_version_3_knows_round_11s_families():
    assert VERSION == 3
    assert len(FAMILY_CEILINGS) == 16 and "nordic" not in FAMILY_CEILINGS
    assert (FAMILY_CEILINGS["handstand"], FAMILY_CEILINGS["shrimp"], FAMILY_CEILINGS["hefesto"]) == (110, 14, 50)
    assert sum(FAMILY_CEILINGS.values()) == 887
    boards = build_boards([score("s", shrimp=140, hefesto=500, handstand=1100)])
    assert {"family_shrimp_world", "family_hefesto_world", "family_handstand_world"} <= boards.keys()
    assert "overall_world" not in build_boards([score("over", handstand=1101)])


def test_every_family_at_its_top_is_the_whole_thousand():
    top = {family: 10 * value for family, value in FAMILY_CEILINGS.items()}
    assert expected_overall(top) == 1000
    assert build_boards([score("w", **top)])["overall_world"]["entries"][0]["score"] == 1000


def test_rounding_on_the_phone_is_tolerated():
    families = {"pullup": 333, "planche": 251}
    assert build_boards([score("r", overall=expected_overall(families) + 2, **families)])["overall_world"]["players"] == 1
    assert "overall_world" not in build_boards([score("r", overall=expected_overall(families) + 3, **families)])


def test_only_the_top_entries_are_kept_but_every_player_counts():
    boards = build_boards([score(f"p{i:03}", pullup=4 * i) for i in range(250)])
    world = boards["family_pullup_world"]
    assert len(world["entries"]) == TOP and world["players"] == 249   # p000 has no pull-up points
    assert world["entries"][0]["score"] == 996
    assert boards["overall_world"]["players"] == 250


def test_percentiles_are_the_score_at_each_one_percent_step():
    world = build_boards([score(f"p{i:03}", pullup=i) for i in range(1, 201)])["family_pullup_world"]
    p = world["percentiles"]
    assert len(p) == 100
    assert p[0] == 199           # the top 1% of 200 players is 2 players: 200 and 199
    assert p[49] == 101          # the top half ends at the 100th player
    assert p[99] == 1
    assert p == sorted(p, reverse=True)


def test_one_player_fills_every_percentile():
    assert build_boards([score("a", pullup=100)])["family_pullup_world"]["percentiles"] == [100] * 100


def test_an_unchanged_board_has_the_same_hash():
    a = build_boards([score("a", pullup=100)])["family_pullup_world"]
    b = build_boards([score("a", pullup=100)])["family_pullup_world"]
    c = build_boards([score("a", pullup=110)])["family_pullup_world"]
    assert board_hash(a) == board_hash(b) != board_hash(c)
