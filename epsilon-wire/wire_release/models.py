from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum
from typing import Any
from uuid import UUID, uuid4
from pydantic import BaseModel, Field

def utcnow() -> datetime:
    return datetime.now(timezone.utc)

class ReleaseKind(str, Enum):
    SINGLE = "single"
    EP = "ep"
    ALBUM = "album"

class MissionState(str, Enum):
    DRAFT = "draft"
    ASSET_VALIDATION = "asset_validation"
    METADATA_VALIDATION = "metadata_validation"
    READY = "ready"
    PACKAGING = "packaging"
    SUBMISSION_PENDING = "submission_pending"
    SUBMITTED = "submitted"
    MODERATION = "moderation"
    LIVE = "live"
    BLOCKED = "blocked"
    FAILED = "failed"
    CANCELLED = "cancelled"

class Asset(BaseModel):
    asset_id: UUID = Field(default_factory=uuid4)
    kind: str
    path_or_url: str
    sha256: str | None = None
    bytes: int | None = None
    duration_seconds: float | None = None
    mime_type: str | None = None
    verified: bool = False
    evidence: dict[str, Any] = Field(default_factory=dict)

class TrackDraft(BaseModel):
    track_id: UUID = Field(default_factory=uuid4)
    title: str
    version: str = ""
    artists: list[str] = Field(default_factory=list)
    featured_artists: list[str] = Field(default_factory=list)
    isrc: str | None = None
    iswc: str | None = None
    composer: str | None = None
    audio: Asset | None = None
    explicit: bool = False
    position: int = 1

class ReleaseDraft(BaseModel):
    release_id: UUID = Field(default_factory=uuid4)
    title: str
    kind: ReleaseKind
    primary_artist: str
    label: str | None = None
    release_date: str | None = None
    artwork: Asset | None = None
    tracks: list[TrackDraft] = Field(default_factory=list)
    genre: str | None = None
    subgenre: str | None = None
    language: str | None = None
    copyright_text: str | None = None
    publisher: str | None = None

class Evidence(BaseModel):
    evidence_id: UUID = Field(default_factory=uuid4)
    request_id: str
    source: str
    status: str
    observed_at: datetime = Field(default_factory=utcnow)
    data: dict[str, Any] = Field(default_factory=dict)

class Mission(BaseModel):
    mission_id: UUID = Field(default_factory=uuid4)
    request_id: str
    draft: ReleaseDraft
    state: MissionState = MissionState.DRAFT
    distributor: str | None = None
    created_at: datetime = Field(default_factory=utcnow)
    updated_at: datetime = Field(default_factory=utcnow)
    evidence: list[Evidence] = Field(default_factory=list)
    errors: list[str] = Field(default_factory=list)
    external_refs: dict[str, str] = Field(default_factory=dict)

class WireReleaseRequest(BaseModel):
    protocol: str = "WIRE_RELEASE_REQUEST_V1"
    request_id: str
    action: str
    mission_id: UUID | None = None
    draft: ReleaseDraft | None = None
    distributor: str | None = None
    dry_run: bool = False
    idempotency_key: str | None = None

class WireReleaseResult(BaseModel):
    protocol: str = "WIRE_RELEASE_RESULT_V1"
    request_id: str
    status: str
    mission_id: UUID | None = None
    state: MissionState | None = None
    action: str
    observed_at: datetime = Field(default_factory=utcnow)
    data: dict[str, Any] = Field(default_factory=dict)
    evidence: list[Evidence] = Field(default_factory=list)
    error: str | None = None
