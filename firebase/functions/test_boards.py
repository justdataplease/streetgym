"""The board builder without Firebase: python -m pytest streetgymnastics/firebase/functions"""
from boards import TOP, board_hash, build_boards


def score(name, country="GR", overall=0, hidden=False, **skills):
    return {"username": name, "country": country, "overall": overall, "skills": skills, "hidden": hidden}


def test_world_and_country_boards_rank_by_score_then_name():
    boards = build_boards([score("b", overall=50), score("a", overall=50), score("c", "DE", overall=90)])
    assert [e["username"] for e in boards["overall_world"]["entries"]] == ["c", "a", "b"]
    assert [e["username"] for e in boards["overall_GR"]["entries"]] == ["a", "b"]
    assert boards["overall_DE"]["players"] == 1


def test_each_skill_gets_a_world_and_a_country_board_for_points_above_zero():
    boards = build_boards([score("a", overall=30, pull_up=20, dip=0), score("b", "DE", overall=10, pull_up=5)])
    assert [e["score"] for e in boards["skill_pull_up_world"]["entries"]] == [20, 5]
    assert "skill_pull_up_GR" in boards and "skill_pull_up_DE" in boards
    assert not any(board.startswith("skill_dip") for board in boards)


def test_hidden_and_malformed_scores_are_left_out():
    boards = build_boards([score("a", overall=5), score("h", overall=99, hidden=True),
                           {"username": "x", "country": "GRC", "overall": 1}, {"country": "GR", "overall": 3},
                           score("y", overall=-1), score("z", **{"bad/id": 4})])
    assert [e["username"] for e in boards["overall_world"]["entries"]] == ["a", "z"]
    assert not any("bad" in board for board in boards)


def test_only_the_top_entries_are_kept_but_every_player_counts():
    boards = build_boards([score(f"p{i:03}", overall=i) for i in range(250)])
    world = boards["overall_world"]
    assert len(world["entries"]) == TOP and world["players"] == 250
    assert world["entries"][0]["score"] == 249


def test_percentiles_are_the_score_at_each_one_percent_step():
    world = build_boards([score(f"p{i:03}", overall=i) for i in range(1, 201)])["overall_world"]
    p = world["percentiles"]
    assert len(p) == 100
    assert p[0] == 199           # the top 1% of 200 players is 2 players: 200 and 199
    assert p[49] == 101          # the top half ends at the 100th player
    assert p[99] == 1
    assert p == sorted(p, reverse=True)


def test_one_player_fills_every_percentile():
    assert build_boards([score("a", overall=7)])["overall_world"]["percentiles"] == [7] * 100


def test_an_unchanged_board_has_the_same_hash():
    a = build_boards([score("a", overall=7)])["overall_world"]
    b = build_boards([score("a", overall=7)])["overall_world"]
    c = build_boards([score("a", overall=8)])["overall_world"]
    assert board_hash(a) == board_hash(b) != board_hash(c)
