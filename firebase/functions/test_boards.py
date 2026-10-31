"""The board builder without Firebase: python -m pytest streetgymnastics/firebase/functions"""
from boards import POINTS_MAX, TOP, VERSION, board_hash, build_boards

MONTH = "2026-11"


def score(name, points, month_points=0, country="GR", month=MONTH, hidden=False, v=VERSION):
    """A current (version 4) score document."""
    return {"v": v, "username": name, "country": country, "points": points, "month": month,
            "month_points": month_points, "hidden": hidden}


def test_world_and_country_boards_rank_by_points_then_name():
    boards = build_boards([score("b", 500), score("a", 500), score("c", 900, country="DE")], MONTH)
    assert [e["username"] for e in boards["all_world"]["entries"]] == ["c", "a", "b"]
    assert [e["username"] for e in boards["all_GR"]["entries"]] == ["a", "b"]
    assert boards["all_DE"]["players"] == 1


def test_the_month_boards_take_this_months_points_only():
    boards = build_boards([
        score("new", 300, 300),
        score("steady", 90_000, 1_200),
        score("away", 50_000, 4_000, month="2026-10"),   # last month's points are no longer this month's
        score("rest", 20_000, 0),
    ], MONTH)
    assert [(e["username"], e["score"]) for e in boards["month_world"]["entries"]] == [("steady", 1_200), ("new", 300)]
    assert boards["month_GR"]["players"] == 2
    assert [e["username"] for e in boards["all_world"]["entries"]] == ["steady", "away", "rest", "new"]


def test_a_new_month_starts_with_no_month_boards():
    boards = build_boards([score("a", 5_000, 800, month="2026-10")], MONTH)
    assert "all_world" in boards and not any(board.startswith("month_") for board in boards)


def test_no_points_no_entry():
    assert build_boards([score("zero", 0)], MONTH) == {}


def test_only_two_kinds_of_board():
    boards = build_boards([score("a", 100, 50), score("b", 80, 10, country="DE")], MONTH)
    assert set(boards) == {"all_world", "all_GR", "all_DE", "month_world", "month_GR", "month_DE"}


def test_hidden_old_and_malformed_scores_are_left_out():
    boards = build_boards([
        score("a", 100, 10),
        score("h", 100, 10, hidden=True),
        score("v3", 100, 10, v=3),
        {"v": 3, "username": "old", "country": "GR", "overall": 500, "families": {}, "verified": []},
        score("x", 100, 10, country="GRC"),
        score("slash", 100, 10, country="a/"),
        score("lower", 100, 10, country="gr"),
        score("", 100, 10),
        score("text", "100", 10),
        score("float", 100.5, 10),
        score("bool", True, 0),
        score("negative", -5, 0),
        score("huge", POINTS_MAX + 1, 0),
        score("more_this_month", 100, 101),
        score("bad_month", 100, 10, month="2026-13"),
        score("no_month", 100, 10, month=None),
    ], MONTH)
    assert [e["username"] for e in boards["all_world"]["entries"]] == ["a"]
    assert [e["username"] for e in boards["month_world"]["entries"]] == ["a"]


def test_version_4():
    assert VERSION == 4


def test_only_the_top_entries_are_kept_but_every_player_counts():
    boards = build_boards([score(f"p{i:03}", 4 * i, i) for i in range(250)], MONTH)
    world = boards["all_world"]
    assert len(world["entries"]) == TOP and world["players"] == 249   # p000 has no points
    assert world["entries"][0]["score"] == 996
    assert boards["month_world"]["players"] == 249


def test_percentiles_are_the_score_at_each_one_percent_step():
    world = build_boards([score(f"p{i:03}", i) for i in range(1, 201)], MONTH)["all_world"]
    p = world["percentiles"]
    assert len(p) == 100
    assert p[0] == 199           # the top 1% of 200 players is 2 players: 200 and 199
    assert p[49] == 101          # the top half ends at the 100th player
    assert p[99] == 1
    assert p == sorted(p, reverse=True)


def test_one_player_fills_every_percentile():
    boards = build_boards([score("a", 100, 40)], MONTH)
    assert boards["all_world"]["percentiles"] == [100] * 100
    assert boards["month_world"]["percentiles"] == [40] * 100


def test_an_unchanged_board_has_the_same_hash():
    a = build_boards([score("a", 100)], MONTH)["all_world"]
    b = build_boards([score("a", 100)], MONTH)["all_world"]
    c = build_boards([score("a", 110)], MONTH)["all_world"]
    assert board_hash(a) == board_hash(b) != board_hash(c)
