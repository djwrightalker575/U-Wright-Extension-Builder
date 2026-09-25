from __future__ import annotations
import os
from .freecords import build_freecords_plan
from .models import Evidence, Mission, MissionState, WireReleaseRequest, WireReleaseResult, utcnow
from .state import transition
from .store import MissionStore
from .validation import validate_freecords_constraints, validate_release

class ReleaseService:
    def __init__(self, store=None):
        self.store = store or MissionStore(os.getenv("WIRE_RELEASE_DB", "./wire-release.db"))

    async def execute(self, req: WireReleaseRequest):
        if req.idempotency_key:
            old = self.store.get_idempotent(req.idempotency_key)
            if old:
                return WireReleaseResult.model_validate(old)

        if req.action == "create":
            if req.draft is None:
                raise ValueError("create requires draft")
            mission = Mission(request_id=req.request_id, draft=req.draft, distributor=req.distributor)
            self.store.put_mission(mission)
        else:
            if not req.mission_id:
                raise ValueError("mission_id is required")
            mission = self.store.get_mission(req.mission_id)
            if not mission:
                raise ValueError("mission not found")

        if req.action in {"create", "validate"}:
            if mission.state == MissionState.DRAFT:
                transition(mission, MissionState.ASSET_VALIDATION)
            errors = validate_release(mission.draft)
            if mission.distributor == "freecords":
                errors.extend(validate_freecords_constraints(mission.draft))
            if errors:
                mission.errors = errors
                transition(mission, MissionState.BLOCKED)
                return self._finish(req, mission, "BLOCKED", {"errors": errors})
            transition(mission, MissionState.METADATA_VALIDATION)
            transition(mission, MissionState.READY)
            return self._finish(req, mission, "VERIFIED", {"validation": "passed"})

        if req.action == "package":
            if mission.state != MissionState.READY:
                raise ValueError(f"package requires READY, got {mission.state.value}")
            transition(mission, MissionState.PACKAGING)
            transition(mission, MissionState.SUBMISSION_PENDING)
            return self._finish(req, mission, "VERIFIED", {"package": "logical_package_ready"})

        if req.action == "submit":
            if mission.state != MissionState.SUBMISSION_PENDING:
                raise ValueError(f"submit requires SUBMISSION_PENDING, got {mission.state.value}")
            if mission.distributor == "freecords":
                plan = build_freecords_plan(mission.draft, req.request_id)
                mission.evidence.append(Evidence(
                    request_id=req.request_id,
                    source="freecords.browser_plan",
                    status="PLAN_READY",
                    data={"actions": plan},
                ))
                return self._finish(req, mission, "ACTION_REQUIRED", {"browser_actions": plan})
            raise ValueError(f"no distributor adapter installed for {mission.distributor!r}")

        if req.action == "apply_evidence":
            if not req.result:
                raise ValueError("apply_evidence requires result")
            mission.evidence.append(Evidence(
                request_id=req.request_id,
                source="wire.android",
                status=str(req.result.get("status", "UNKNOWN")),
                data=req.result,
            ))
            if req.result.get("status") == "VERIFIED" and req.result.get("data", {}).get("success") is True:
                if mission.state == MissionState.SUBMISSION_PENDING:
                    transition(mission, MissionState.SUBMITTED)
                elif mission.state == MissionState.SUBMITTED:
                    transition(mission, MissionState.MODERATION)
                elif mission.state == MissionState.MODERATION:
                    transition(mission, MissionState.LIVE)
                else:
                    raise ValueError(f"verified evidence cannot advance state {mission.state.value}")
                return self._finish(req, mission, "VERIFIED", {"evidence_recorded": True, "state_advanced": True})
            return self._finish(req, mission, "OBSERVED", {"evidence_recorded": True, "state_advanced": False})

        if req.action == "status":
            return self._finish(req, mission, "VERIFIED", {"mission": mission.model_dump(mode="json")})

        raise ValueError(f"unsupported action: {req.action}")

    def _finish(self, req, mission, status, data):
        mission.updated_at = utcnow()
        self.store.put_mission(mission)
        self.store.record_event(mission.mission_id, req.request_id, req.action, data)
        result = WireReleaseResult(
            request_id=req.request_id, status=status, mission_id=mission.mission_id,
            state=mission.state, action=req.action, data=data, evidence=mission.evidence
        )
        if req.idempotency_key:
            self.store.put_idempotent(req.idempotency_key, req.request_id, result.model_dump(mode="json"))
        return result
