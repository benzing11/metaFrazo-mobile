# MetaFrazo — Agent Guide

## Monorepo layout
- `android/` — Kotlin + Jetpack Compose app (single-activity, MVVM/MVI).
- `backend/` — Next.js Route Handlers API, SQLite, auth, recording template page, assetlinks.
- `ai-service/` — FastAPI: ASR (Faster-Whisper), translation (IndicTrans2), OCR (Tesseract), summaries, LiveKit agent.
- `infra/` — docker-compose (LiveKit, Egress, Redis, optional MinIO), livekit.yaml, egress.yaml.
- `docs/` — setup, deployment, security, recording architecture, reference notes.
- Root: `AGENTS.md`, `PROGRESS.md`, `README.md`, `.env.example`.

## Architecture
Android client talks to backend (REST) and LiveKit (media). Backend verifies Firebase ID tokens, issues LiveKit tokens, manages meetings/notes/transcripts/recordings, serves recording composition template and assetlinks. AI service does ASR/translation/OCR/summaries and runs a LiveKit agent for transcription/screen translation. SQLite persists data (migrations). Recording media on disk/S3-compatible storage, metadata in SQLite.

## Conventions
- Kotlin official code style; TypeScript strict; Python type hints.
- No secrets in code; env vars only; `.env.example` with placeholders.
- Versions centralized (Gradle version catalog in android/; package.json in backend; requirements.txt/pyproject in ai-service).
- Keep UI/domain/data separated in Android.

## Build & test
- Android: `cd android && ./gradlew assembleDebug`
- Backend: `cd backend && npm install && npm run build && npm run typecheck`
- AI service: `cd ai-service && pip install -r requirements.txt && uvicorn app.main:app --reload`
- Infra: `cd infra && docker compose up -d` (Docker required)

## Current status
See PROGRESS.md. Phase 1 foundation in progress.