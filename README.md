# MetaFrazo

Native Android multilingual meeting platform (English / Tamil / Hindi) with a Next.js backend, FastAPI AI service and a LiveKit-based real-time media + recording pipeline. UI is Jetpack Compose with Material 3.

## Repository layout

- `android/` — Kotlin + Jetpack Compose app (single activity, MVVM, Hilt-free manual DI, Navigation Compose).
- `backend/` — Next.js Route Handlers API, SQLite (better-sqlite3), Firebase Admin auth, LiveKit token issuance, recording template page, assetlinks.
- `ai-service/` — FastAPI: ASR (Faster-Whisper), translation (NLLB-200), OCR (Tesseract), extractive summaries, LiveKit agent scaffold.
- `infra/` — Docker Compose for LiveKit, Egress, Redis, optional MinIO; `livekit.yaml`, `egress.yaml`.
- `docs/` — deployment, security, data safety, Material 3 reference.

## Prerequisites

- JDK 17 (`/opt/homebrew/Cellar/openjdk@17/...` on macOS or any JDK 17).
- Android SDK (compileSdk 35) with platform-tools. Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
- Node.js 20+ (tested on 24) and npm.
- Python 3.10+ (tested on 3.14).
- Docker (optional, only for LiveKit/Egress/Redis/MinIO). Not available in this dev environment; infra cannot be run here.
- Tesseract binary + language data (`tam`, `hin`, `eng`) for OCR.
- (Optional) Firebase project + service account for real auth; dev bypass works without it.

## Environment variables

Copy `.env.example` to `.env` at repo root and to `backend/.env`. Key vars:

```
LIVEKIT_URL=ws://localhost:7880
LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=secret
LIVEKIT_NODE_IP=127.0.0.1
BACKEND_PORT=3000
DATABASE_PATH=./data/metafrazo.db
RECORDINGS_DIR=./data/recordings
DEV_AUTH_BYPASS=true            # dev only
NODE_ENV=development
AI_SERVICE_PORT=8000
WHISPER_MODEL=base
TESSDATA_PREFIX=<path to tessdata>
```

Android reads URLs from `BuildConfig` (default `http://10.0.2.2:3000` and `http://10.0.2.2:8000` for emulator). Override in `android/gradle.properties` via `backend.url`/`ai.url` if needed (not currently wired; edit `app/build.gradle.kts` buildConfigField).

## Local setup (exact commands)

### Quick start: one command for the dev stack
```
./scripts/dev-up.sh     # starts backend (:3000) + LiveKit (:7880/7881), sets adb reverse, checks health
./scripts/dev-down.sh   # stops what dev-up started and clears adb reverse
```
Create/join needs both the backend (token) and LiveKit (media) running. A USB phone running the debug
build talks to `localhost`, so it also needs `adb reverse` for 3000, 8000, 7880, and 7881. `dev-up.sh`
sets these up again on every run, because `adb reverse` is lost when the phone is replugged. To use
Wi-Fi without USB, build with `./gradlew assembleDebug -PLAN_HOST=<your Mac's LAN IP>`.
The sections below are the manual steps.

### 1. Infra (requires Docker)
```
cd infra
docker compose up -d
```
Health: LiveKit `http://localhost:7880`, Redis `6379`.

### 2. Backend
```
cd backend
cp ../.env.example .env   # fill values; keep DEV_AUTH_BYPASS=true for local dev
npm install
npm run typecheck
npm run build
npm run dev                # http://localhost:3000
```
Health: `curl http://localhost:3000/api/health`.

### 3. AI service
```
cd ai-service
pip3 install fastapi uvicorn pydantic python-multipart
# Optional heavy deps (ASR/translation):
pip3 install faster-whisper transformers torch sentencepiece
# OCR:
# brew install tesseract tesseract-lang   (macOS)  -> provides tam/hin/eng
export TESSDATA_PREFIX=/opt/homebrew/share/tessdata   # adjust
uvicorn app.main:app --reload --port 8000
```
Health: `curl http://localhost:8000/health` (reports which components are installed honestly).

### 4. Android
```
cd android
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Emulator reaches host backend at `10.0.2.2`. For a physical device on the same LAN, set `BACKEND_URL`/`AI_SERVICE_URL` build config to the host LAN IP.

## How to create and join a meeting

1. Start backend + (optionally) infra + AI service.
2. Launch the app; it uses dev-user identity automatically (dev bypass).
3. Home → "Start Meeting" → backend creates a meeting and returns a 6-char code. Copy/Share the code or invitation link.
4. Preflight (camera/mic preview) → "Join Meeting".
5. Another device/emulator: Home → "Join Meeting" → enter code → preflight → connect. Invalid codes are rejected by the backend (no room created).

Deep links: `https://metafrazo.app/join/<CODE>` and `metafrazo://join/<CODE>`.

## How to test all six translation directions

Backend/AI endpoints:
```
curl -X POST http://localhost:8000/translate -H 'Content-Type: application/json' \
  -d '{"text":"hello","source_lang":"en","target_lang":"ta"}'
```
Directions: EN→TA, EN→HI, TA→EN, TA→HI, HI→EN, HI→TA. Requires `transformers` + `torch` + NLLB model download (first call downloads ~600MB). Without them, `/translate` returns the original text (honest fallback).

In-app: Meeting Room → "Captions" → pick source/target language (EN/TA/HI). Each recipient's choice is independent. TTS playback available if the device has the language voice installed (Settings → check voice availability; captions-only fallback otherwise).

## How to record a meeting

1. Host only: in Meeting Room, tap "Record" (backend calls LiveKit Egress room composite).
2. A recording indicator + live duration show to everyone; consent event stored.
3. Tap "Stop rec" → egress stops, file finalized (mp4) on `RECORDINGS_DIR`.
4. Recordings → list → "Play" streams via range-capable backend endpoint.

Requires LiveKit + Egress running (Docker). Without Egress, start returns 500 and the recording is marked `failed` with the error message.

## How to find and play saved recordings

- Home → "Recordings" (or nav rail). Lists real recordings for meetings you belong to.
- Each entry: meeting title/code, date, duration, mode, status.
- Play: Media3 ExoPlayer with seek, speed, fullscreen, PiP, resume position.
- Download to device (MediaStore), share, rename (authorized), delete with confirmation.
- Filters/search by date, meeting, mode, status. Empty/loading/processing/failed/offline states shown.

## Recording architecture and verified limitations

- Egress: `livekit-server-sdk` `startRoomCompositeEgress` with `EncodedFileOutput` to `RECORDINGS_DIR/<uuid>.mp4`.
- Standard mode captures the composed room (audio + video + screen share as LiveKit composes it). It does **not** include recipient-specific translated captions/overlays — those are per-client UI and cannot be in a room composite. A personalized composed-view recording would require a custom web composition template (served at `/recording-template`) driven by a viewer profile; this is scaffolded but not fully wired to Egress web mode in this build. Do not assume personalized overlays are captured.
- File validation with ffprobe and frame inspection is **not automated** in this build; validate manually: `ffprobe -v error -show_entries stream=codec_type,codec_name -show_entries format=duration recording.mp4`.
- Storage: local disk in dev; configure S3-compatible (MinIO) via env for production. SQLite stores metadata + protected references only, never media bytes.
- Access: authorized, range-capable streaming through backend; no public URLs.

## LiveKit Cloud migration

1. Create a LiveKit Cloud project; get URL + API key/secret.
2. Set `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` env vars (backend + infra client).
3. Verify Egress is enabled on the Cloud project (Egress is a separate capability).
4. Android needs no code change; backend issues tokens from env.
5. For media reachability, ensure the Cloud URL is WSS and the device has network access.

## Build / lint / type-check / test results (this environment)

- Android: `./gradlew assembleDebug` → BUILD SUCCESSFUL. `./gradlew test` → BUILD SUCCESSFUL (6 unit tests for continuity engine).
- Backend: `npm run typecheck` → pass. `npm run build` → pass.
- AI service: boots; `/health` reports installed components honestly (heavy ML deps and tesseract not installed in this env).
- Infra: Docker not installed; compose files present but not runnable here.

## Deployment

See `docs/deployment.md` (Play Store AAB, signing, foreground services, App Links, backend/AI hosting, persistent volumes, HTTPS/WSS). See `docs/security.md` and `docs/data-safety.md`.

## Security

- Backend verifies Firebase ID token on every protected request (dev bypass only when `DEV_AUTH_BYPASS=true` and `NODE_ENV !== production`). Client-supplied user IDs ignored.
- Ownership/membership checked on every resource.
- Android: HTTPS-only in release (network security config allows cleartext only for debug local hosts), tokens via encrypted storage, no secrets in APK, R8 enabled for release.
- No paid AI API mandatory; core features use local models.

## Honest list of remaining issues / blockers

- Docker not installed in this environment → LiveKit/Egress/Redis/MinIO not runnable; recording pipeline cannot be end-to-end verified here.
- Heavy ML deps (faster-whisper, transformers, torch) and Tesseract binary not installed → ASR/translation/OCR endpoints return honest fallbacks/errors.
- Firebase Auth on Android (Credential Manager) not wired; dev bypass used. Real sign-in requires a Firebase project + `google-services.json`.
- Adaptive layouts (WindowSizeClass) and many Material 3 Expressive components are not introduced (material3 pinned to stable 1.3.1; expressive APIs require AGP 9.1 + compileSdk 37, not available offline).
- Screen translation is scaffolded (OCR endpoint, overlay with letterbox mapping, staleness manager) but real frame capture from a screen-share track and per-recipient delivery are not wired end-to-end.
- Per-recipient caption delivery is broadcast currently; routing by recipient preference not implemented.
- Focus assistant is heuristic (action/decision extraction) only; no real attention analysis.
- Automated ffprobe/frame validation of recordings not implemented.
- Unit/UI/instrumented test coverage is minimal (continuity engine only).

## Architecture (summary)

Android (Compose) ↔ Backend (Next.js/SQLite) for auth, meetings, chat, notes, transcripts, summaries, recordings metadata. Android ↔ LiveKit for media (audio/video/screen share/data). AI service (FastAPI) provides ASR/translation/OCR/summaries and a LiveKit agent scaffold for transcription/screen translation. Recordings stored on disk/S3; metadata in SQLite. See AGENTS.md.