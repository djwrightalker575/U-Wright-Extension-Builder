from __future__ import annotations
from dataclasses import dataclass
from typing import Any
from .models import ReleaseDraft

@dataclass(frozen=True)
class BrowserAction:
    action: str
    url: str | None = None
    selector: str | None = None
    value: str | None = None
    wait_ms: int = 500
    note: str | None = None

    def as_wire(self, request_id: str) -> dict[str, Any]:
        args = {k: v for k, v in {
            "url": self.url, "selector": self.selector,
            "value": self.value, "wait_ms": self.wait_ms
        }.items() if v is not None}
        return {
            "protocol": "WIRE_ANDROID_REQUEST_V1",
            "request_id": request_id,
            "action": "browser." + self.action,
            "args": args,
            "note": self.note,
        }

def build_freecords_plan(draft: ReleaseDraft, request_id: str):
    actions = [
        BrowserAction("tabs", note="Inspect existing Freecords session before changing anything."),
        BrowserAction("page_snapshot", note="Capture the current page and discover controls."),
        BrowserAction("query", selector="input,button,[role=button]", note="Discover selectors from current DOM; do not assume stale selectors."),
    ]
    for track in draft.tracks:
        actions.append(BrowserAction("page_snapshot", note=f"Prepare upload for track {track.title!r}."))
    actions.append(BrowserAction("page_snapshot", note="Verify final upload state before declaring success."))
    return [a.as_wire(request_id) for a in actions]
