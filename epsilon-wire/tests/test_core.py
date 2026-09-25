from pathlib import Path
import asyncio
from wire_release.freecords import build_freecords_plan
from wire_release.models import Asset, ReleaseDraft, ReleaseKind, TrackDraft, WireReleaseRequest
from wire_release.service import ReleaseService
from wire_release.store import MissionStore
from wire_release.validation import validate_release

def make_draft(tmp_path: Path):
    audio = tmp_path / "song.wav"
    art = tmp_path / "cover.jpg"
    audio.write_bytes(b"audio")
    art.write_bytes(b"art")
    return ReleaseDraft(
        title="Test Release", kind=ReleaseKind.SINGLE, primary_artist="DJwrighTalker",
        artwork=Asset(kind="artwork", path_or_url=str(art)),
        tracks=[TrackDraft(title="Test Song", position=1, audio=Asset(kind="audio", path_or_url=str(audio)))]
    )

def test_validation(tmp_path):
    assert validate_release(make_draft(tmp_path)) == []

def test_freecords_plan(tmp_path):
    plan = build_freecords_plan(make_draft(tmp_path), "wire-test-1")
    assert plan[0]["action"] == "browser.tabs"
    assert any(x["action"] == "browser.query" for x in plan)
    assert all(x["protocol"] == "WIRE_ANDROID_REQUEST_V1" for x in plan)

def test_replay_protection(tmp_path):
    service = ReleaseService(MissionStore(str(tmp_path / "db.sqlite")))
    req = WireReleaseRequest(
        request_id="wire-test-2", action="create", draft=make_draft(tmp_path),
        idempotency_key="same-operation", distributor="freecords"
    )
    first = asyncio.run(service.execute(req))
    second = asyncio.run(service.execute(req))
    assert first.mission_id == second.mission_id
