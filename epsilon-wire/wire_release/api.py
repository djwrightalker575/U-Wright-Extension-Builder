from __future__ import annotations
from uuid import UUID
from fastapi import FastAPI, HTTPException
from .models import WireReleaseRequest, WireReleaseResult
from .service import ReleaseService

app = FastAPI(title="WIRE Release Platform", version="0.1.0")
service = ReleaseService()

@app.get("/health")
async def health():
    return {"ok": True, "protocol": "WIRE_RELEASE_API_V1"}

@app.post("/v1/release", response_model=WireReleaseResult)
async def release(req: WireReleaseRequest):
    try:
        return await service.execute(req)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"execution_failed: {exc}") from exc

@app.get("/v1/release/{mission_id}")
async def release_status(mission_id: str):
    mission = service.store.get_mission(UUID(mission_id))
    if not mission:
        raise HTTPException(status_code=404, detail="mission not found")
    return mission
