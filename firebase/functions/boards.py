"""The leaderboard boards from the Plus and Pro scores: pure, so it can be tested without Firebase.

Each score document (scores/{uid}, version 4) holds username, country, points (every point the athlete has earned,
from the sets they trained and the chevrons they reached), month (the UTC month, "2026-10") and month_points (the
points earned in that month). A hidden, older or malformed one is skipped. Every board is one document: the top TOP
entries, the number of players and 100 percentiles (the score at each 1% step from the top), so the app shows anyone's
place with that single read.

    boards/all_world   boards/all_GR   boards/month_world   boards/month_GR

A month board only takes the scores of the month it is built in, so it starts empty on the 1st.
"""
import hashlib
import json
import math
import re

TOP = 100
COUNTRY = re.compile(r"[A-Z]{2}")
MONTH = re.compile(r"[0-9]{4}-(0[1-9]|1[0-2])")
# Version 4 (the owner, 2026-10-31): points for training and chevrons, all time and this month, instead of the overall
# score out of 1000 and its family boards. Older scores are dropped until the app republishes them.
VERSION = 4
POINTS_MAX = 100_000_000


def _count(value, most) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and 0 <= value <= most


def _valid(score: dict) -> bool:
    return (not score.get("hidden") and score.get("v") == VERSION
            and isinstance(score.get("username"), str) and bool(score["username"])
            # The country names boards (boards/all_GR), so anything but two capitals, like "a/", is refused.
            and isinstance(score.get("country"), str) and bool(COUNTRY.fullmatch(score["country"]))
            and _count(score.get("points"), POINTS_MAX)
            and isinstance(score.get("month"), str) and bool(MONTH.fullmatch(score["month"]))
            and _count(score.get("month_points"), score["points"]))


def build_boards(scores: list, month: str) -> dict:
    """{board id: {"entries": [...], "players": n, "percentiles": [100 ints]}} from the score documents, with the month
    boards for [month] ("2026-10")."""
    lists = {}

    def add(board, username, country, points):
        lists.setdefault(board, []).append({"username": username, "country": country, "score": points})

    for score in scores:
        if not _valid(score):
            continue
        name, country = score["username"], score["country"]
        if score["points"] > 0:
            add("all_world", name, country, score["points"])
            add(f"all_{country}", name, country, score["points"])
        if score["month"] == month and score["month_points"] > 0:
            add("month_world", name, country, score["month_points"])
            add(f"month_{country}", name, country, score["month_points"])

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
