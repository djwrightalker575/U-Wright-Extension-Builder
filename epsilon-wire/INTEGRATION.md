# Integration contract

ChatGPT sends WIRE_RELEASE_REQUEST_V1 to the WIRE Release API. The API returns WIRE_RELEASE_RESULT_V1.

For browser work, the result contains WIRE Android commands. W.A.R. executes them through its existing browser capability owner and returns device evidence through the verified WIRE_ANDROID_RESULT_V1 auto-return path.

Lifecycle:
DRAFT -> ASSET_VALIDATION -> METADATA_VALIDATION -> READY -> PACKAGING -> SUBMISSION_PENDING -> SUBMITTED -> MODERATION -> LIVE

BLOCKED and FAILED require an explicit repair/restart operation.

No false success:
VERIFIED means the orchestration layer has evidence for the operation it reports. A browser plan is ACTION_REQUIRED, not VERIFIED. A Freecords upload is not SUBMITTED until W.A.R. returns observable evidence that the upload succeeded.

Freecords public documentation currently describes web/app upload flows and constraints including MP3/WAV/FLAC audio, 100 MB maximum music-file size, JPG/PNG artwork, 10 MB artwork limit, and minimum artwork dimensions of 600x600. These are validation constraints, not guarantees of moderation acceptance.

The current public Freecords material used here does not expose a developer API contract. Therefore the adapter intentionally generates browser actions and performs selector discovery instead of inventing API endpoints.

Epsilon currently exposes FastAPI CRUD routes for releases and tracks, while its upstream README states that the project is under active development. The WIRE mission database remains the source of truth for orchestration state.

Required end-to-end verification:
1. Start Epsilon.
2. Start epsilon-wire.
3. Create a real release mission.
4. Validate it.
5. Package it.
6. Request submission.
7. Execute the returned browser actions through W.A.R.
8. Return actual WIRE_ANDROID_RESULT_V1 evidence.
9. Record the evidence.
10. Only then advance the mission based on the observed result.
