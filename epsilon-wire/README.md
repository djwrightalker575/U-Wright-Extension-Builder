# Epsilon + WIRE Release Platform

This is the WIRE orchestration layer for the Epsilon.fm music-distribution foundation.

Architecture:
ChatGPT conversation -> W.A.R. auto-return transport -> WIRE Release API -> durable release mission -> Epsilon adapter -> distributor adapter.

The orchestration layer does not claim distributor success unless an adapter returns observable evidence.

Implemented:
- durable single and album missions
- deterministic request IDs and replay protection
- explicit release state machine
- metadata, artwork, and audio validation
- Epsilon HTTP adapter
- Freecords browser-action plan generation
- WIRE_ANDROID_V1 command envelopes
- evidence records and audit events
- dry-run-compatible request model
- pytest coverage for core invariants

The upstream Epsilon repository is under active development. Its current backend exposes CRUD routes for releases and tracks, while the release and track models are still minimal. WIRE therefore keeps durable orchestration state in its own database and isolates Epsilon behind an adapter boundary.

Run:
cd epsilon-wire
python -m venv .venv
pip install -e ".[dev]"
uvicorn wire_release.api:app --reload --port 8787

Environment:
EPSILON_BASE_URL=https://api.epsilon.fm
WIRE_RELEASE_DB=./wire-release.db
