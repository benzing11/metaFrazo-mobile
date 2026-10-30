import io
import os
import shutil
from typing import Any, Optional

from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel

from app.engines import get_engine

app = FastAPI(title="MetaFrazo AI Service", version="0.1.0")

LANG_MAP = {"en": "eng_Latn", "ta": "tam_Taml", "hi": "hin_Deva"}
NLLB_MODEL = os.environ.get("NLLB_MODEL", "facebook/nllb-200-distilled-600M")

# Lazy-loaded models
_whisper = None
_nllb_tok = None
_nllb_model = None


def _check_module(name: str) -> bool:
    try:
        __import__(name)
        return True
    except Exception:
        return False


class HealthResponse(BaseModel):
    status: str
    service: str
    components: dict[str, Any]


class TranslateRequest(BaseModel):
    text: str
    source_lang: str = "en"
    target_lang: str = "ta"


class TranslateResponse(BaseModel):
    translated_text: str
    source_lang: str
    target_lang: str


class OcrRegion(BaseModel):
    text: str
    confidence: float
    x: float
    y: float
    w: float
    h: float


class OcrResponse(BaseModel):
    language: str
    regions: list[OcrRegion]
    full_text: str


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    tesseract_path = shutil.which("tesseract")
    components = {
        "fastapi": True,
        "faster_whisper": _check_module("faster_whisper"),
        "transformers": _check_module("transformers"),
        "torch": _check_module("torch"),
        "sentencepiece": _check_module("sentencepiece"),
        "pytesseract": _check_module("pytesseract"),
        "cv2": _check_module("cv2"),
        "PIL": _check_module("PIL"),
        "tesseract_binary": bool(tesseract_path),
        "tesseract_path": tesseract_path,
    }
    return HealthResponse(status="ok", service="metafrazo-ai", components=components)


@app.get("/")
def root() -> dict:
    return {"service": "metafrazo-ai", "docs": "/docs"}


class SummaryRequest(BaseModel):
    text: str
    max_sentences: int = 5


class SummaryResponse(BaseModel):
    summary: str
    abstract: str = ""
    key_points: list[str] = []
    decisions: list[str] = []
    action_items: list[str] = []


class QaSegment(BaseModel):
    id: str = ""
    speaker: str = ""
    text: str


class QaRequest(BaseModel):
    question: str
    context: list[QaSegment]


class QaResponse(BaseModel):
    answer: str
    cited_segments: list[str]


@app.post("/summarize", response_model=SummaryResponse)
async def summarize(req: SummaryRequest):
    r = get_engine().summarize(req.text, req.max_sentences)
    return SummaryResponse(summary=r["abstract"], **r)


@app.post("/qa", response_model=QaResponse)
async def qa(req: QaRequest):
    r = get_engine().answer(req.question, [s.model_dump() for s in req.context])
    return QaResponse(**r)


@app.post("/ocr", response_model=OcrResponse)
async def ocr(file: UploadFile = File(...), language: str = Form("eng")):
    if not _check_module("pytesseract"):
        return OcrResponse(language=language, regions=[], full_text="")
    try:
        import io as _io
        from PIL import Image
        import pytesseract
        import os
        tess = os.environ.get("TESSERACT_CMD") or r"C:\Program Files\Tesseract-OCR\tesseract.exe"
        if os.path.exists(tess):
            pytesseract.pytesseract.tesseract_cmd = tess
        os.environ.setdefault("TESSDATA_PREFIX", os.environ.get("TESSDATA_PREFIX", r"C:\Users\bsbp0\tessdata"))

        data = await file.read()
        img = Image.open(_io.BytesIO(data))
        w, h = img.size
        tess_lang = "tam+eng" if language in ("ta", "tam") else ("hin" if language in ("hi", "hin") else "eng")
        data_dict = pytesseract.image_to_data(img, lang=tess_lang, output_type=pytesseract.Output.DICT)
        regions = []
        full_parts = []
        n = len(data_dict["text"])
        for i in range(n):
            txt = (data_dict["text"][i] or "").strip()
            conf = float(data_dict["conf"][i]) if data_dict["conf"][i] not in ("-1", None) else 0.0
            if not txt:
                continue
            x = data_dict["left"][i] / w
            y = data_dict["top"][i] / h
            ww = data_dict["width"][i] / w
            hh = data_dict["height"][i] / h
            regions.append(OcrRegion(text=txt, confidence=conf, x=x, y=y, w=ww, h=hh))
            full_parts.append(txt)
        return OcrResponse(language=tess_lang, regions=regions, full_text=" ".join(full_parts))
    except Exception as e:
        return OcrResponse(language=language, regions=[], full_text=f"error: {e}")


def _get_whisper():
    global _whisper
    if _whisper is None:
        from faster_whisper import WhisperModel

        _whisper = WhisperModel(os.environ.get("WHISPER_MODEL", "base"), device="cpu", compute_type="int8")
    return _whisper


WHISPER_RATE = 16_000
# Caps one upload at ~30 s of 16 kHz mono 16-bit audio so a bad client can't exhaust memory.
MAX_PCM_BYTES = WHISPER_RATE * 2 * 30


@app.post("/asr")
async def asr(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    encoding: Optional[str] = Form(None),
    sample_rate: Optional[int] = Form(None),
    initial_prompt: Optional[str] = Form(None),
):
    """Transcribes one utterance.

    `encoding=pcm_s16le` with `sample_rate` sends raw mono PCM (what the Android app streams);
    otherwise the upload is decoded as a container file. `initial_prompt` biases decoding toward
    names and domain terms the caller expects.
    """
    if not _check_module("faster_whisper"):
        return {"error": "faster-whisper not installed"}
    try:
        import numpy as np

        data = await file.read()
        if encoding == "pcm_s16le":
            if not sample_rate or sample_rate <= 0:
                return {"error": "sample_rate is required for pcm_s16le"}
            if len(data) > MAX_PCM_BYTES * max(1, sample_rate // WHISPER_RATE):
                return {"error": "audio too long"}
            audio = np.frombuffer(data[: len(data) - len(data) % 2], dtype="<i2").astype("float32") / 32768.0
            if sample_rate != WHISPER_RATE:
                # Linear resample; the app already sends 16 kHz so this is a fallback only.
                n = int(len(audio) * WHISPER_RATE / sample_rate)
                audio = np.interp(np.linspace(0, len(audio) - 1, n), np.arange(len(audio)), audio).astype("float32")
        else:
            from faster_whisper.audio import decode_audio

            audio = decode_audio(io.BytesIO(data), sampling_rate=WHISPER_RATE)
        if audio.size == 0:
            return {"error": "no audio"}

        prompt = (initial_prompt or "").strip()[:500] or None
        model = _get_whisper()
        segments, info = model.transcribe(
            audio,
            language=language or None,
            task="transcribe",
            beam_size=5,
            best_of=5,
            temperature=0.0,
            vad_filter=True,
            condition_on_previous_text=False,
            # Whisper absorbs names better when the initial prompt is phrased like
            # prior speech; keep the user's words verbatim after a short lead-in.
            initial_prompt=(f"Transcription of a meeting. {prompt}." if prompt else None),
        )
        out = [{"text": s.text.strip(), "start": s.start, "end": s.end} for s in segments]
        return {
            "language": info.language,
            "language_probability": info.language_probability,
            "text": " ".join(s["text"] for s in out if s["text"]).strip(),
            "segments": out,
        }
    except Exception as e:
        return {"error": str(e)}


def _get_nllb():
    global _nllb_tok, _nllb_model
    if _nllb_tok is None:
        from transformers import AutoTokenizer, AutoModelForSeq2SeqLM

        _nllb_tok = AutoTokenizer.from_pretrained(NLLB_MODEL)
        _nllb_model = AutoModelForSeq2SeqLM.from_pretrained(NLLB_MODEL)
    return _nllb_tok, _nllb_model


@app.post("/translate", response_model=TranslateResponse)
def translate(req: TranslateRequest):
    if not _check_module("transformers") or not _check_module("torch"):
        return TranslateResponse(translated_text=req.text, source_lang=req.source_lang, target_lang=req.target_lang)
    src = LANG_MAP.get(req.source_lang, "eng_Latn")
    tgt = LANG_MAP.get(req.target_lang, "eng_Latn")
    if src == tgt:
        return TranslateResponse(translated_text=req.text, source_lang=req.source_lang, target_lang=req.target_lang)
    try:
        import torch

        tok, model = _get_nllb()
        inputs = tok(req.text, return_tensors="pt")
        forced = tok.convert_tokens_to_ids(tgt)
        out = model.generate(**inputs, forced_bos_token_id=forced, max_length=256)
        translated = tok.batch_decode(out, skip_special_tokens=True)[0]
        return TranslateResponse(translated_text=translated, source_lang=req.source_lang, target_lang=req.target_lang)
    except Exception as e:
        return TranslateResponse(translated_text=req.text, source_lang=req.source_lang, target_lang=req.target_lang)