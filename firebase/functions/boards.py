"""The leaderboard boards from the Pro scores: pure, so it can be tested without Firebase.

Each score document (scores/{uid}) holds username, country, overall and skills ({skill id: points}); a hidden one
is skipped. Every board is one document: the top TOP entries, the number of players and 100 percentiles (the score
at each 1% step from the top), so the app shows anyone's place with that single read.

    boards/overall_world   boards/overall_GR   boards/skill_pull_up_world   boards/skill_pull_up_GR
"""
import hashlib
import json
import math

TOP = 100


def _valid(score: dict) -> bool:
    return (not score.get("hidden")
            and isinstance(score.get("username"), str) and score["username"]
            and isinstance(score.get("country"), str) and len(score["country"]) == 2
            and isinstance(score.get("overall"), int) and score["overall"] >= 0)


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
        for skill, points in (score.get("skills") or {}).items():
            if isinstance(points, int) and points > 0 and skill.replace("_", "").isalnum():
                add(f"skill_{skill}_world", name, country, points)
                add(f"skill_{skill}_{country}", name, country, points)

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
