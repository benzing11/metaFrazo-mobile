"""MetaFrazo LiveKit agent: transcribes remote audio and publishes captions.

Run: LIVEKIT_URL=ws://... LIVEKIT_TOKEN=<agent token> python agent.py
The agent joins the room, subscribes to audio tracks, segments them, runs
Faster-Whisper ASR, translates with NLLB, and publishes a JSON caption
message on the LiveKit data channel for all participants.
"""
import asyncio
import io
import json
import os
import time

import numpy as np
from faster_whisper import WhisperModel
from livekit import rtc
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
import torch

LIVEKIT_URL = os.environ.get("LIVEKIT_URL", "ws://localhost:7880")
LIVEKIT_TOKEN = os.environ.get("LIVEKIT_TOKEN", "")
LIVEKIT_API_KEY = os.environ.get("LIVEKIT_API_KEY", "devkey")
LIVEKIT_API_SECRET = os.environ.get("LIVEKIT_API_SECRET", "secret")
WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "base")
NLLB_MODEL = os.environ.get("NLLB_MODEL", "facebook/nllb-200-distilled-600M")
LANG_MAP = {"en": "eng_Latn", "ta": "tam_Taml", "hi": "hin_Deva"}

whisper = WhisperModel(WHISPER_MODEL, device="cpu", compute_type="int8")
nllb_tok = AutoTokenizer.from_pretrained(NLLB_MODEL)
nllb_model = AutoModelForSeq2SeqLM.from_pretrained(NLLB_MODEL)


def translate(text: str, src: str, tgt: str) -> str:
    if src == tgt:
        return text
    s = LANG_MAP.get(src, "eng_Latn")
    t = LANG_MAP.get(tgt, "eng_Latn")
    inputs = nllb_tok(text, return_tensors="pt")
    forced = nllb_tok.convert_tokens_to_ids(t)
    out = nllb_model.generate(**inputs, forced_bos_token_id=forced, max_length=256)
    return nllb_tok.batch_decode(out, skip_special_tokens=True)[0]


async def main():
    token = LIVEKIT_TOKEN
    if not token:
        from livekit.api import AccessToken, VideoGrants

        at = AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, identity="metafrazo-agent")
        at.with_grants(VideoGrants(room_join=True, room=os.environ.get("ROOM_NAME", ""), can_publish_data=True, can_subscribe=True))
        token = at.to_jwt()
    if not token:
        raise SystemExit("LIVEKIT_TOKEN env var required")
    room = rtc.Room()
    await room.connect(LIVEKIT_URL, token)
    print("connected to room", room.name)

    audio_buffers: dict[str, list] = {}

    @room.on("track_subscribed")
    def on_track(track, publication, participant):
        if track.kind != rtc.TrackKind.KIND_AUDIO:
            return
        stream = rtc.AudioStream(track)
        asyncio.ensure_future(handle_audio(stream, participant, room))

    async def handle_audio(stream, participant, room):
        buffer = []
        sample_rate = 48000
        async for event in stream:
            frame = event.frame
            data = np.frombuffer(frame.data, dtype=np.int16).astype(np.float32) / 32768.0
            buffer.append(data)
            # segment roughly every 4 seconds
            if len(buffer) * len(data) >= sample_rate * 4:
                audio = np.concatenate(buffer)
                buffer.clear()
                segments, info = whisper.transcribe(audio, language=None, sampling_rate=sample_rate)
                src_lang = info.language if info.language in ("en", "ta", "hi") else "en"
                for seg in segments:
                    text = seg.text.strip()
                    if not text:
                        continue
                    # translate to all distinct targets (here: all three)
                    translations = {}
                    for tgt in ("en", "ta", "hi"):
                        if tgt != src_lang:
                            try:
                                translations[tgt] = translate(text, src_lang, tgt)
                            except Exception as e:
                                translations[tgt] = text
                    payload = json.dumps({
                        "type": "caption",
                        "speaker": participant.identity,
                        "source_lang": src_lang,
                        "original": text,
                        "translations": translations,
                        "ts": time.time(),
                    })
                    await room.local_participant.publish_data(payload.encode(), reliable=True)
                    print(participant.identity, src_lang, text)

    # keep running
    while True:
        await asyncio.sleep(1)


if __name__ == "__main__":
    asyncio.run(main())