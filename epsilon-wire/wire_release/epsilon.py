from __future__ import annotations
import httpx

class EpsilonClient:
    def __init__(self, base_url: str, token: str | None = None, timeout: float = 30):
        self.base_url = base_url.rstrip("/")
        self.headers = {"Authorization": f"Bearer {token}"} if token else {}
        self.timeout = timeout

    async def _request(self, method: str, path: str, **kwargs):
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            r = await client.request(method, f"{self.base_url}{path}", headers=self.headers, **kwargs)
            r.raise_for_status()
            return r.json() if r.content else {}

    async def health(self):
        return await self._request("GET", "/")

    async def create_release(self, release_name: str):
        return await self._request("POST", "/v1/releases", json={"release_name": release_name})

    async def get_release(self, release_id: str):
        return await self._request("GET", f"/v1/releases/{release_id}")

    async def update_release(self, release_id: str, payload: dict):
        return await self._request("PATCH", f"/v1/releases/{release_id}", json=payload)

    async def create_track(self, payload: dict):
        return await self._request("POST", "/v1/tracks", json=payload)

    async def get_track(self, track_id: str):
        return await self._request("GET", f"/v1/tracks/{track_id}")

    async def update_track(self, track_id: str, payload: dict):
        return await self._request("PATCH", f"/v1/tracks/{track_id}", json=payload)
