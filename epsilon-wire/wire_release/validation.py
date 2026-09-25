from pathlib import Path
from .models import ReleaseDraft

AUDIO_EXTS = {".mp3", ".wav", ".flac"}
ART_EXTS = {".jpg", ".jpeg", ".png"}

def validate_audio(path_or_url: str, position: int = 1) -> list[str]:
    p = Path(path_or_url)
    if "://" not in path_or_url and p.suffix.lower() not in AUDIO_EXTS:
        return [f"track {position}: unsupported audio format {p.suffix or '<none>'}"]
    return []

def validate_artwork(path_or_url: str) -> list[str]:
    p = Path(path_or_url)
    if "://" not in path_or_url and p.suffix.lower() not in ART_EXTS:
        return [f"artwork: unsupported format {p.suffix or '<none>'}"]
    return []

def validate_release(draft: ReleaseDraft) -> list[str]:
    errors: list[str] = []
    if not draft.title.strip(): errors.append("release title is required")
    if not draft.primary_artist.strip(): errors.append("primary artist is required")
    if not draft.tracks: errors.append("at least one track is required")
    if draft.kind.value == "single" and len(draft.tracks) != 1:
        errors.append("single releases must contain exactly one track")
    if not draft.artwork: errors.append("artwork is required")
    else: errors.extend(validate_artwork(draft.artwork.path_or_url))
    for i, track in enumerate(draft.tracks, 1):
        if not track.title.strip(): errors.append(f"track {i}: title is required")
        if not track.audio: errors.append(f"track {i}: audio is required")
        else: errors.extend(validate_audio(track.audio.path_or_url, i))
        if track.position != i: errors.append(f"track {i}: position must be {i}")
    return errors

def validate_freecords_constraints(draft: ReleaseDraft) -> list[str]:
    errors = []
    for i, track in enumerate(draft.tracks, 1):
        if track.audio and "://" not in track.audio.path_or_url:
            p = Path(track.audio.path_or_url)
            if p.exists() and p.stat().st_size > 100 * 1024 * 1024:
                errors.append(f"track {i}: exceeds Freecords 100MB file limit")
    if draft.artwork and "://" not in draft.artwork.path_or_url:
        p = Path(draft.artwork.path_or_url)
        if p.exists() and p.stat().st_size > 10 * 1024 * 1024:
            errors.append("artwork: exceeds Freecords 10MB file limit")
    return errors
