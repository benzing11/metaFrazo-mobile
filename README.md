# MetaFrazo

Native Android multilingual meeting platform with backend, AI services and recording pipeline. Material 3 Expressive.

## Repository layout
- `android/` — Kotlin + Jetpack Compose app
- `backend/` — Next.js API + SQLite + auth + recording template + assetlinks
- `ai-service/` — FastAPI (ASR, translation, OCR, summaries)
- `infra/` — docker-compose (LiveKit, Egress, Redis, MinIO)
- `docs/` — notes and reference

## Local setup

### 1. Infra (requires Docker — not installed in this environment)
```
cd infra
docker compose up -d
```

### 2. Backend
```
cd backend
cp ../.env.example .env   # fill values
npm install
npm run typecheck
npm run build
npm run dev   # http://localhost:3000
```

### 3. AI service
```
cd ai-service
pip install -r requirements.txt
# Optional heavy deps: faster-whisper, transformers, torch, pytesseract, opencv-python, Pillow
# Install tesseract binary and language data (tam, hin) separately
uvicorn app.main:app --reload --port 8000
```
Health: http://localhost:8000/health

### 4. Android
Open `android/` in Android Studio, or:
```
cd android
./gradlew assembleDebug
```
Emulator reaches host backend at 10.0.2.2. Install:
```
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Honest status
Phase 1 foundation only. Auth, meetings, translation, screen translation, chat, notes, recordings not yet implemented. AI optional ML deps and tesseract binary are not installed locally; health endpoint reports this. Docker is not available in this environment, so infra cannot be run here.