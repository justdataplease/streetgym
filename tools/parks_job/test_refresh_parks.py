"""The parks refresh without the network or Cloud Storage: python -m pytest streetgymnastics/tools/parks_job"""
import osm_parks
import refresh_parks as job


def test_a_run_far_below_the_published_file_is_not_published():
    assert job.check(0, None) == "no parks found"
    assert job.check(100, None) is None
    assert job.check(79, {"count": 100})
    assert job.check(80, {"count": 100}) is None


def test_a_failed_overpass_run_publishes_nothing(monkeypatch, capsys):
    def broken(*_):
        raise RuntimeError("HTTP 504 after 6 attempts")
    monkeypatch.setattr(osm_parks, "run", broken)
    assert job.main(["--dry-run"]) == 1
    assert "PARKS FAIL" in capsys.readouterr().out


def test_a_dry_run_builds_and_publishes_nothing(monkeypatch, capsys):
    counts = {"stations": 3, "left_out": 0}
    monkeypatch.setattr(osm_parks, "run", lambda *_: ([("Athens - Workout park - Greece", 37.9, 23.7)], counts))
    assert job.main(["--dry-run"]) == 0
    assert "PARKS PASS (dry run): 1 parks" in capsys.readouterr().out
