# MetaFrazo AI Service

FastAPI service providing ASR (Faster-Whisper), translation (NLLB-200), OCR (Tesseract), summaries, and a LiveKit agent.

## Setup
```
pip install -r requirements.txt
```

### Tesseract (screen OCR)
The `/ocr` endpoint powers server-side shared-screen translation for scripts the Android
app can't read on-device (Tamil, Telugu, Kannada, Bengali, Gujarati, Urdu, Arabic, Chinese).
Install the Tesseract binary plus the language packs you expect to OCR:

- macOS: `brew install tesseract tesseract-lang` (installs all languages)
- Debian/Ubuntu: `sudo apt-get install tesseract-ocr tesseract-ocr-hin tesseract-ocr-tam tesseract-ocr-tel tesseract-ocr-kan tesseract-ocr-ben tesseract-ocr-guj tesseract-ocr-urd tesseract-ocr-ara tesseract-ocr-chi-sim`
- Windows: install the UB-Mannheim build and add the chosen language data.

The service auto-detects the binary on `PATH`. Overrides (all optional):
- `TESSERACT_CMD` — explicit path to the `tesseract` binary (needed on Windows if not on PATH).
- `TESSDATA_PREFIX` — path to the `tessdata` directory, only if the system default is wrong.

Language codes map as: en→eng, hi→hin, mr→mar, ta→tam, te→tel, kn→kan, bn→ben,
gu→guj, ur→urd, es→spa, fr→fra, de→deu, ar→ara, zh→chi_sim (each paired with eng).
`GET /health` reports `tesseract_binary` and `tesseract_path` so you can confirm it's found.

## Run
```
uvicorn app.main:app --reload
```