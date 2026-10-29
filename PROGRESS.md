# MetaFrazo Progress

## Phase 1 — Foundation
- [x] Monorepo skeleton (android/, backend/, ai-service/, infra/, docs/)
- [~] Android: M3 Expressive theme + component library + previews (theme in place; previews not yet added)
- [ ] Android: WindowSizeClass adaptive layouts
- [x] Android: env handling (BuildConfig URLs), Diagnostics screen
- [ ] Android: auth groundwork (Credential Manager + Firebase) — not started
- [x] Backend: health endpoint, SQLite migrations, Firebase Admin verify (typecheck+build pass)
- [x] Backend: assetlinks.json, recording template page
- [x] AI service: health, model/Tesseract availability checks (boots; reports missing deps honestly)
- [x] Infra: docker-compose, livekit.yaml, egress.yaml (Docker not installed locally)
- [x] Verification: assembleDebug OK, backend build OK, AI boot OK, health checks OK

## Phase 2 — Core meeting
- [x] Backend: create/join/token endpoints (verified via curl: create, join, token issued)
- [x] Android: StartMeeting, JoinMeeting, MeetingRoom screens with LiveKit connect + mic/camera toggles
- [x] Deep links: manifest intent filters (https metafrazo.app/join, metafrazo://join) + MainActivity route parsing + join/{code} route
- [x] Screen share: MediaProjection permission + LiveKit setScreenShareEnabled + notification
- [x] Foreground service (microphone/camera type) for meeting keep-alive
- [x] Picture-in-Picture on background when in meeting
- [x] Reconnection state tracking (Reconnecting/Reconnected/Disconnected)
- [ ] Active speaker highlighting, participant grid polish
- [x] Permissions rationale screen (mic/camera request + status)

## Phase 3 — Translation
- [x] AI service: /asr (Faster-Whisper) verified; /translate (NLLB-200) verified for en->ta
- [x] LiveKit Python agent scaffold (agent.py): joins room, transcribes audio, translates, publishes captions via data channel
- [x] Android captions UI + language selectors (EN/TA/HI) + original toggle
- [x] Android TTS playback (Speak last, availability check)
- [ ] Per-recipient delivery (currently broadcasts to all)
- [x] Continuity engine module added (CaptionContinuityEngine)

## Phase 4 — Screen translation
- [x] Tesseract installed with eng/hin/tam language data
- [x] AI service /ocr endpoint with bounding boxes (verified)
- [x] Backend /api/screen/analyze proxy (OCR + translate per region)
- [x] Android ScreenTranslateOverlay composable (normalized regions)
- [ ] Real screen-share frame capture + per-recipient overlay delivery
- [ ] Stale-result rejection, dedupe, region tracking
- [ ] Coordinate mapping to letterboxed video rect

## Phase 5 — Meeting content
- [x] Backend chat endpoints (send/list) with membership checks
- [x] Backend notes endpoints (get/upsert) per meeting/user
- [x] Android ChatScreen (list + send)
- [x] Transcripts backend endpoints + Android TranscriptScreen
- [x] Summaries backend endpoint + AI /summarize
- [x] Android NotesScreen (get/upsert)
- [x] Focus assistant (heuristic action/decision extraction in TranscriptScreen)
- [x] Release signing config (env/gradle props) + bundleRelease verified
- [ ] Notes UI polish

## Phase 6 — Recording
- [x] Backend recording start/stop (LiveKit Egress room composite)
- [x] Backend recordings list + metadata + range streaming + delete
- [x] Android Recordings library screen
- [x] Android player (ExoPlayer + SurfaceView)
- [x] Recording template page present (app/recording-template) served for Egress composition
- [ ] Recording indicator in meeting UI, consent flow polish

## Phase 7 — Verification & deployment
- [x] Android assembleDebug builds (verified)
- [x] Backend typecheck + build (verified)
- [x] AI service /health, /asr, /translate, /ocr verified
- [x] Deployment docs added (docs/deployment.md, docs/security.md)
- [x] Android network security config (HTTPS-only release, cleartext dev hosts)
- [ ] E2E tests, Play Store data safety submission, release signing config