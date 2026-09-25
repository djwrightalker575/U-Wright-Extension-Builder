from .models import Mission, MissionState

ALLOWED = {
    MissionState.DRAFT: {MissionState.ASSET_VALIDATION, MissionState.CANCELLED},
    MissionState.ASSET_VALIDATION: {MissionState.METADATA_VALIDATION, MissionState.BLOCKED, MissionState.FAILED},
    MissionState.METADATA_VALIDATION: {MissionState.READY, MissionState.BLOCKED, MissionState.FAILED},
    MissionState.READY: {MissionState.PACKAGING, MissionState.CANCELLED},
    MissionState.PACKAGING: {MissionState.SUBMISSION_PENDING, MissionState.FAILED},
    MissionState.SUBMISSION_PENDING: {MissionState.SUBMITTED, MissionState.BLOCKED, MissionState.FAILED},
    MissionState.SUBMITTED: {MissionState.MODERATION, MissionState.LIVE, MissionState.BLOCKED, MissionState.FAILED},
    MissionState.MODERATION: {MissionState.LIVE, MissionState.BLOCKED, MissionState.FAILED},
    MissionState.LIVE: set(),
    MissionState.BLOCKED: {MissionState.ASSET_VALIDATION, MissionState.METADATA_VALIDATION, MissionState.CANCELLED},
    MissionState.FAILED: {MissionState.ASSET_VALIDATION, MissionState.METADATA_VALIDATION, MissionState.CANCELLED},
    MissionState.CANCELLED: set(),
}

def transition(mission: Mission, target: MissionState):
    if target not in ALLOWED[mission.state]:
        raise ValueError(f"illegal transition {mission.state.value} -> {target.value}")
    mission.state = target
    return mission
