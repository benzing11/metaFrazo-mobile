# MetaFrazo Progress

## Verification (this environment)
- Android: `./gradlew assembleDebug` BUILD SUCCESSFUL; `./gradlew test` BUILD SUCCESSFUL (6 unit tests).
- Backend: `npm run typecheck` pass; `npm run build` pass; health + create/join/token verified via curl.
- AI service: boots; `/health` reports installed components honestly (heavy ML deps + tesseract not installed).
- Infra: Docker not installed locally → not runnable.

## Phase 1 — Foundation
- [x] Monorepo skeleton
- [~] Android: M3 theme (standard MaterialTheme; expressive APIs downgraded to stable 1.3.1 due to AGP/compileSdk constraints) + component library; previews not yet added
- [ ] Android: WindowSizeClass adaptive layouts (not implemented)
- [x] Android: env handling (BuildConfig URLs), Diagnostics screen (refresh wired)
- [ ] Android: auth groundwork (Credential Manager + Firebase) — not started (dev bypass used)
- [x] Backend: health endpoint, SQLite migrations, Firebase Admin verify (typecheck+build pass)
- [x] Backend: assetlinks.json, recording template page
- [x] AI service: health, model/Tesseract availability checks (boots; reports missing deps honestly)
- [x] Infra: docker-compose, livekit.yaml, egress.yaml (Docker not installed locally)
- [x] Verification: assembleDebug OK, backend build OK, AI boot OK, health checks OK

## Phase 2 — Core meeting
- [x] Backend: create/join/token endpoints (verified via curl)
- [x] Android: StartMeeting, JoinMeeting, MeetingRoom screens with LiveKit connect + mic/camera toggles
- [x] Deep links: manifest intent filters + MainActivity route parsing + join/{code} route
- [x] Screen share: MediaProjection permission + LiveKit setScreenShareEnabled + notification
- [x] Foreground service (microphone/camera type) for meeting keep-alive
- [x] Picture-in-Picture on background when in meeting
- [x] Reconnection state tracking
- [x] Wireless low-latency phone-to-phone optimization: LiveKit RoomOptions (adaptiveStream + dynacast)
- [x] Proactive peer language handshake on ParticipantConnected and Reconnected
- [x] Low-latency progressive caption upserting (CaptionUpsert) with unit test suite
- [x] Wireless phone-to-phone connection guide (docs/wireless-phone-connection.md)
- [ ] Active speaker highlighting polish (events tracked; UI basic)
- [x] Permissions rationale screen
- [x] Recording indicator + controls in meeting UI (wired to backend)

## Phase 3 — Translation
- [x] AI service: /asr (Faster-Whisper) scaffold; /translate (NLLB-200) scaffold
- [x] LiveKit Python agent scaffold (agent.py)
- [x] Android captions UI + language selectors (EN/TA/HI) + original toggle
- [x] Android TTS playback (Speak last, availability check)
- [x] Per-recipient delivery (caption_pref announcements + targeted publishData/destination_identities; broadcast fallback when a recipient's preference is unknown; agent.py + CaptionsViewModel + CaptionRouter with unit tests)
- [x] Continuity engine module (enhanced + unit tested)

## Phase 4 — Screen translation
- [x] Tesseract installed? (not in this env; endpoint reports honest status)
- [x] AI service /ocr endpoint with bounding boxes (scaffold)
- [x] Backend /api/screen/analyze proxy (OCR + translate per region)
- [x] Android ScreenTranslateOverlay composable (normalized regions + letterbox mapping)
- [ ] Real screen-share frame capture + per-recipient overlay delivery
- [ ] Stale-result rejection wiring end-to-end (manager exists)
- [x] Coordinate mapping to letterboxed video rect (in overlay)

## Phase 5 — Meeting content
- [x] Backend chat endpoints (send/list) with membership checks
- [x] Backend notes endpoints (get/upsert) per meeting/user
- [x] Android ChatScreen (list + send)
- [x] Transcripts backend endpoints + Android TranscriptScreen
- [x] Summaries backend endpoint + AI /summarize
- [x] Android NotesScreen (get/upsert)
- [x] Focus assistant (heuristic action/decision extraction)
- [x] Release signing config (env/gradle props) + bundleRelease verified
- [ ] Notes UI polish

## Phase 6 — Recording
- [x] Backend recording start/stop (LiveKit Egress room composite)
- [x] Backend recordings list + metadata + range streaming + delete
- [x] Android Recordings library screen
- [x] Android player (ExoPlayer + SurfaceView)
- [x] Recording template page present (app/recording-template) served for Egress composition
- [x] Recording indicator in meeting UI, consent flow (basic)

## Phase 7 — Verification & deployment
- [x] Android assembleDebug builds (verified)
- [x] Backend typecheck + build (verified)
- [x] AI service /health, /asr, /translate, /ocr scaffolds
- [x] Deployment docs (docs/deployment.md, docs/security.md, docs/data-safety.md)
- [x] Android network security config (HTTPS-only release, cleartext dev hosts)
- [x] README expanded with full setup/test/deployment/limitations
- [ ] E2E tests on real devices, Play Store data safety submission