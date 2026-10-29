# Deployment Guide

## Android (Play Store)
- Build release AAB: `cd android && ./gradlew bundleRelease`
- Signing: set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD via env/gradle properties.
- R8 enabled for release (proguard-rules.pro).
- Foreground services declared: microphone, camera, mediaProjection (manifest).
- App Links: verify `https://<host>/.well-known/assetlinks.json` matches package + SHA-256 fingerprint.
- Data Safety: declare camera/mic, meeting content, recordings; no data sold.
- Target SDK 35; min SDK 26.

## Backend
- Host Next.js as a Node service behind HTTPS reverse proxy.
- Set env vars from .env.example (Firebase service account, LiveKit, DB path, recordings dir).
- Run DB migrations on startup (auto via lib/db.ts).
- Persistent volume for DATABASE_PATH and RECORDINGS_DIR.

## AI service
- Run uvicorn behind reverse proxy; install faster-whisper, transformers, torch, tesseract + language data.
- Set TESSDATA_PREFIX to tessdata dir.
- GPU optional for faster inference; CPU works with int8.

## Infra
- Docker Compose: LiveKit, Egress, Redis, optional MinIO.
- Migrate to LiveKit Cloud: change LIVEKIT_URL/API_KEY/API_SECRET env vars only.

## Security
- HTTPS/WSS only in production; network security config permits cleartext only in debug.
- Tokens in encrypted storage; no secrets in APK.
- Rate limiting and input validation on backend (to be added).