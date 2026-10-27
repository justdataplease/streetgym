"""The leaderboard boards from the Pro scores: pure, so it can be tested without Firebase.

Each score document (scores/{uid}, version 2) holds username, country, overall (the overall score, 0 to 1000),
families ({family id: ten times the family's points}) and verified (the rungs the athlete passed a test for). A
hidden, older or inconsistent one is skipped: the overall must match the families it is made of. Every board is one
document: the top TOP entries, the number of players and 100 percentiles (the score at each 1% step from the top), so
the app shows anyone's place with that single read.

    boards/overall_world   boards/overall_GR   boards/family_front_lever_world   boards/family_front_lever_GR
"""
import hashlib
import json
import math
import re

TOP = 100
COUNTRY = re.compile(r"[A-Z]{2}")
VERSION = 2
SCORE_MAX = 1000
# Each family's top value, as the app's OverallScore.SKILL_VALUES gives it (SkillBookTest checks they agree): a
# family's points never exceed it, and the overall is 1000 times the families' points over their sum.
FAMILY_CEILINGS = {
    "pullup": 110, "muscleup": 32, "front_lever": 100, "back_lever": 50, "dip": 12, "pushup": 24, "hspu": 55,
    "planche": 200, "handstand": 30, "pistol": 12, "nordic": 24, "hanging_core": 10, "lsit": 30,
    "dragon_flag": 18, "human_flag": 60,
}
# Each family is rounded on the phone before the server adds them up.
OVERALL_TOLERANCE = 2


def expected_overall(families: dict) -> int:
    """The overall score the families add up to."""
    return round(SCORE_MAX * sum(families.values()) / 10 / sum(FAMILY_CEILINGS.values()))


def _valid(score: dict) -> bool:
    families = score.get("families")
    if (score.get("hidden") or score.get("v") != VERSION
            or not (isinstance(score.get("username"), str) and score["username"])
            # The country names boards (boards/overall_GR), so anything but two capitals, like "a/", is refused.
            or not (isinstance(score.get("country"), str) and COUNTRY.fullmatch(score["country"]))
            or not (isinstance(score.get("overall"), int) and 0 <= score["overall"] <= SCORE_MAX)
            or not isinstance(families, dict)):
        return False
    for family, points in families.items():
        if family not in FAMILY_CEILINGS or not isinstance(points, int) or not 0 <= points <= 10 * FAMILY_CEILINGS[family]:
            return False
    return abs(expected_overall(families) - score["overall"]) <= OVERALL_TOLERANCE


def build_boards(scores: list) -> dict:
    """{board id: {"entries": [...], "players": n, "percentiles": [100 ints]}} from the score documents."""
    lists = {}

    def add(board, username, country, points):
        lists.setdefault(board, []).append({"username": username, "country": country, "score": points})

    for score in scores:
        if not _valid(score):
            continue
        name, country = score["username"], score["country"]
        add("overall_world", name, country, score["overall"])
        add(f"overall_{country}", name, country, score["overall"])
        for family, points in score["families"].items():
            if points > 0:
                add(f"family_{family}_world", name, country, points)
                add(f"family_{family}_{country}", name, country, points)

    boards = {}
    for board, entries in lists.items():
        entries.sort(key=lambda e: (-e["score"], e["username"].lower()))
        n = len(entries)
        percentiles = [entries[min(n - 1, math.ceil((step + 1) * n / 100) - 1)]["score"] for step in range(100)]
        boards[board] = {"entries": entries[:TOP], "players": n, "percentiles": percentiles}
    return boards


def board_hash(board: dict) -> str:
    """Unchanged boards aren't written again: each write costs money, a hash comparison doesn't."""
    return hashlib.sha256(json.dumps(board, sort_keys=True).encode()).hexdigest()[:16]
