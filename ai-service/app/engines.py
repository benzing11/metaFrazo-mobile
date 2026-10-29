"""Summary and Q&A engines.

Everything the HTTP layer needs goes through `get_engine()`. The heuristic engine is
dependency-free; a model-backed engine can be added by subclassing `Engine` and
selecting it with METAFRAZO_AI_ENGINE.
"""
import os
import re
from typing import Optional, TypedDict


class Segment(TypedDict, total=False):
    id: str
    speaker: str
    text: str


_SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+|\n+")
_WORD = re.compile(r"\w+", re.UNICODE)

_STOP = frozenset(
    "a an and are as at be but by for from had has have he her his i if in into is it its me my of on or our she so "
    "that the their them then there these they this to us was we were what when where which who why will with you your "
    "about did do does how can could would should".split()
)

_ACTION = re.compile(
    r"\b(will|need to|needs to|have to|has to|must|should|todo|to-do|action item|follow up|follow-up|let's|please|assign(?:ed)?|by (?:monday|tuesday|wednesday|thursday|friday|tomorrow|next week|end of))\b",
    re.IGNORECASE,
)
_DECISION = re.compile(
    r"\b(decided|decision|agreed|we(?:'ll| will) go with|approved|finali[sz]ed|settled on|going with|confirmed)\b",
    re.IGNORECASE,
)


def split_sentences(text: str) -> list[str]:
    return [s.strip() for s in _SENTENCE_SPLIT.split(text) if s and s.strip()]


def _stem(word: str) -> str:
    w = word.lower()
    for suffix in ("ing", "ed", "es", "s"):
        if len(w) > len(suffix) + 3 and w.endswith(suffix):
            return w[: -len(suffix)]
    return w


def _content_tokens(text: str) -> list[str]:
    return [_stem(w) for w in _WORD.findall(text) if w.lower() not in _STOP and len(w) > 2]


def _strip_speaker(line: str) -> str:
    return re.sub(r"^[^:\n]{1,40}:\s+", "", line)


class Engine:
    def summarize(self, text: str, max_sentences: int = 5) -> dict:
        raise NotImplementedError

    def answer(self, question: str, segments: list[Segment]) -> dict:
        raise NotImplementedError


class HeuristicEngine(Engine):
    def summarize(self, text: str, max_sentences: int = 5) -> dict:
        text = text.strip()
        empty = {"abstract": "", "key_points": [], "decisions": [], "action_items": []}
        if not text:
            return empty
        lines = split_sentences(text)
        if not lines:
            return empty

        freq: dict[str, int] = {}
        for tok in _content_tokens(text):
            freq[tok] = freq.get(tok, 0) + 1

        def score(line: str) -> float:
            toks = _content_tokens(_strip_speaker(line))
            if not toks:
                return 0.0
            return sum(freq.get(t, 0) for t in toks) / (len(toks) ** 0.5)

        decisions = [l for l in lines if _DECISION.search(l)]
        actions = [l for l in lines if _ACTION.search(l) and l not in decisions]

        ranked = sorted(range(len(lines)), key=lambda i: -score(lines[i]))
        chosen = sorted(ranked[:max_sentences])
        abstract = " ".join(_strip_speaker(lines[i]) for i in chosen)

        taken = set(chosen)
        extra = [i for i in ranked if i not in taken][:max_sentences]
        key_points = [lines[i] for i in sorted(extra)]
        return {
            "abstract": abstract,
            "key_points": key_points,
            "decisions": decisions[:max_sentences],
            "action_items": actions[:max_sentences],
        }

    def answer(self, question: str, segments: list[Segment]) -> dict:
        q_tokens = set(_content_tokens(question))
        scored: list[tuple[float, int]] = []
        for idx, seg in enumerate(segments):
            toks = set(_content_tokens(seg.get("text", "")))
            overlap = len(q_tokens & toks)
            if overlap:
                scored.append((overlap / (len(toks) ** 0.5), idx))
        if not q_tokens or not scored:
            return {"answer": "I couldn't find that in this meeting's transcript.", "cited_segments": []}
        scored.sort(key=lambda x: -x[0])
        top = sorted(idx for _, idx in scored[:3])
        lines = []
        cited = []
        for idx in top:
            seg = segments[idx]
            speaker = seg.get("speaker") or "Someone"
            lines.append(f"{speaker}: {seg.get('text', '').strip()}")
            if seg.get("id"):
                cited.append(seg["id"])
        return {"answer": "From the transcript:\n" + "\n".join(lines), "cited_segments": cited}


_ENGINES: dict[str, type[Engine]] = {"heuristic": HeuristicEngine}


def get_engine(name: Optional[str] = None) -> Engine:
    key = (name or os.environ.get("METAFRAZO_AI_ENGINE", "heuristic")).lower()
    return _ENGINES.get(key, HeuristicEngine)()
