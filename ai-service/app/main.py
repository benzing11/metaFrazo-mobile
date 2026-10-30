import io
import os
import shutil
from typing import Any, Optional

from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel

from app.engines import get_engine

app = FastAPI(title="MetaFrazo AI Service", version="0.1.0")

LANG_MAP = {
    "en": "eng_Latn",
    "ta": "tam_Taml",
    "hi": "hin_Deva"
}
# Using IndicTrans2 as the heavy-duty engine
INDIC_TRANS_MODEL = os.environ.get("INDIC_TRANS_MODEL", "ai4bharat/indictrans2-en-indic-1B")

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


# Maps the app's two-letter language codes to Tesseract traineddata names. Each target
# script is paired with English so mixed-language slides still read cleanly. Install the
# matching language packs for whatever you expect to OCR (see docs/setup).
OCR_TESS_LANG = {
    "en": "eng",
    "hi": "hin+eng",
    "mr": "mar+eng",
    "ta": "tam+eng",
    "te": "tel+eng",
    "kn": "kan+eng",
    "bn": "ben+eng",
    "gu": "guj+eng",
    "ur": "urd+eng",
    "es": "spa+eng",
    "fr": "fra+eng",
    "de": "deu+eng",
    "ar": "ara+eng",
    "zh": "chi_sim+eng",
}


def _resolve_tesseract_lang(language: str) -> str:
    """Normalize an incoming language code to a Tesseract lang string, defaulting to English."""
    if not language:
        return "eng"
    code = language.lower()
    if code in OCR_TESS_LANG:
        return OCR_TESS_LANG[code]
    # Already a Tesseract code (e.g. "tam+eng") or unknown: pass through, else English.
    return language if language.replace("+", "").isalpha() else "eng"


@app.post("/ocr", response_model=OcrResponse)
async def ocr(file: UploadFile = File(...), language: str = Form("en")):
    if not _check_module("pytesseract"):
        return OcrResponse(language=language, regions=[], full_text="")
    try:
        import io as _io
        from PIL import Image
        import pytesseract
        import os
        # Cross-platform: prefer an explicit override, otherwise rely on PATH (Linux/macOS
        # install tesseract to /usr/bin or /opt/homebrew/bin, both already on PATH).
        tess = os.environ.get("TESSERACT_CMD") or shutil.which("tesseract")
        if tess and os.path.exists(tess):
            pytesseract.pytesseract.tesseract_cmd = tess
        # TESSDATA_PREFIX is only set when provided; the system package sets a sane default.
        tessdata = os.environ.get("TESSDATA_PREFIX")
        if tessdata:
            os.environ["TESSDATA_PREFIX"] = tessdata

        data = await file.read()
        img = Image.open(_io.BytesIO(data))
        w, h = img.size
        tess_lang = _resolve_tesseract_lang(language)
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

        _whisper = WhisperModel(os.environ.get("WHISPER_MODEL", "large-v3"), device="cpu", compute_type="int8")
    return _whisper


def _parse_hotwords(prompt: Optional[str]) -> Optional[str]:
    """Turn a comma/semicolon list of names and jargon into a hotwords string.

    faster-whisper biases decoding toward these terms far more reliably than `initial_prompt`.
    """
    if not prompt:
        return None
    words = [w.strip() for part in prompt.replace(";", ",").split(",") for w in [part] if w.strip()]
    joined = " ".join(words)[:500].strip()
    return joined or None


# MetaFrazo only supports these three; auto-detect is clamped to them so Whisper can't
# wander off to an unrelated language on a short or noisy phrase.
SUPPORTED_LANGS = ("en", "ta", "hi")


def _detect_supported_language(model, audio) -> Optional[str]:
    """Detect the spoken language but restrict the choice to English/Tamil/Hindi.

    Returns the most probable of the three, or None if detection fails (caller then
    falls back to the client-provided language).
    """
    try:
        _, _, all_probs = model.detect_language(audio)
    except Exception:
        return None
    # all_probs is a list of (lang, prob); keep only the supported ones.
    best = None
    best_p = -1.0
    for lang, prob in all_probs:
        if lang in SUPPORTED_LANGS and prob > best_p:
            best, best_p = lang, prob
    return best


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
    auto_detect: Optional[bool] = Form(False),
):
    """Transcribes one utterance.

    `encoding=pcm_s16le` with `sample_rate` sends raw mono PCM (what the Android app streams);
    otherwise the upload is decoded as a container file. `initial_prompt` biases decoding toward
    names and domain terms the caller expects; those terms are also passed as Whisper `hotwords`.
    Set `auto_detect=true` to let Whisper pick the language per utterance, which transcribes
    code-mixed speech (e.g. Tamil + English) far better than forcing a single language.
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
        hotwords = _parse_hotwords(initial_prompt)
        model = _get_whisper()
        # When auto_detect is set, detect the language but clamp it to the three MetaFrazo
        # supports (English/Tamil/Hindi). This keeps code-mixed speech intact while stopping
        # Whisper from guessing an unrelated language on a short or noisy phrase.
        decode_lang = language or None
        if auto_detect:
            decode_lang = _detect_supported_language(model, audio) or (language or None)
        segments, info = model.transcribe(
            audio,
            language=decode_lang,
            task="transcribe",
            beam_size=5,
            best_of=5,
            temperature=0.0,
            vad_filter=True,
            condition_on_previous_text=False,
            # hotwords biases decoding toward the caller's names/jargon more reliably than a prompt;
            # initial_prompt still helps with general meeting context.
            hotwords=hotwords,
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


def _get_indic_trans():
    global _nllb_tok, _nllb_model
    if _nllb_tok is None:
        from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
        # IndicTrans2 uses different naming convention for src/tgt. 
        # For now, keeping the structure but pointing to the new weights.
        _nllb_tok = AutoTokenizer.from_pretrained(INDIC_TRANS_MODEL, trust_remote_code=True)
        _nllb_model = AutoModelForSeq2SeqLM.from_pretrained(INDIC_TRANS_MODEL, trust_remote_code=True)
    return _nllb_tok, _nllb_model


@app.post("/translate", response_model=TranslateResponse)
def translate(req: TranslateRequest):
    if not _check_module("transformers") or not _check_module("torch"):
        return TranslateResponse(translated_text=req.text, source_lang=req.source_lang, target_lang=req.target_lang)
    # IndicTrans2 expects the input text to be wrapped in language tags
    # Example: "<2hi> Text to translate"
    # We need to map our simple 'en', 'hi', 'ta' codes to the tags the tokenizer understands
    # IndicTrans2 standard: "en_Latn", "hin_Deva", "tam_Taml"
    lang_tags = {
        "en": "eng_Latn",
        "hi": "hin_Deva",
        "ta": "tam_Taml"
    }
    src_tag = lang_tags.get(req.source_lang)
    tgt_tag = lang_tags.get(req.target_lang)
    
    if not src_tag or not tgt_tag:
         return TranslateResponse(translated_text=req.text, source_lang=req.source_lang, target_lang=req.target_lang)
    
    # Prepend the target language tag
    text_to_translate = f"<{tgt_tag}> {req.text}"
    
    try:
        import torch
        tok, model = _get_indic_trans()
        
        inputs = tok(text_to_translate, return_tensors="pt")
        # IndicTrans2 does not always require forced_bos_token_id when using the prefix tag
        out = model.generate(**inputs, max_length=256)
        translated = tok.batch_decode(out, skip_special_tokens=True)[0]
        
        return TranslateResponse(translated_text=translated, source_lang=req.source_lang, target_lang=req.target_lang)
    except Exception as e:
        return TranslateResponse(translated_text=req.text, source_lang=req.source_lang, target_lang=req.target_lang)