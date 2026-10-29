# MetaFrazo AI Service

FastAPI service providing ASR (Faster-Whisper), translation (NLLB-200), OCR (Tesseract), summaries, and a LiveKit agent.

## Setup
```
pip install -r requirements.txt
```
Install Tesseract binary + language data (eng, hin, tam). Set `TESSDATA_PREFIX` to tessdata dir. On Windows set `TESSERACT_CMD` if not on PATH.

## Run
```
uvicorn app.main:app --reload
```