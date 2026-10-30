import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { callAi, loadSegments } from "@/lib/transcript";

const AskSchema = z.object({ question: z.string().trim().min(1).max(1000), source_lang: z.string().default("en") });

type AiAnswer = { answer: string; cited_segments: string[] };

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = AskSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });

  const segments = loadSegments(db, meeting.id);
  let ai: AiAnswer;
  if (segments.length === 0) {
    ai = { answer: "There's no transcript for this meeting yet, so I have nothing to answer from.", cited_segments: [] };
  } else {
    try {
      const raw = await callAi<AiAnswer>("/qa", { question: parsed.data.question, context: segments });
      if (!raw || typeof raw.answer !== "string") {
        ai = { answer: "The assistant returned an invalid response. Please try again.", cited_segments: [] };
      } else {
        ai = { answer: raw.answer, cited_segments: Array.isArray(raw.cited_segments) ? raw.cited_segments : [] };
      }
    } catch {
      return NextResponse.json({ error: "Q&A service unavailable" }, { status: 502 });
    }
  }

  const validIds = new Set(segments.map((s) => s.id));
  const cited = ai.cited_segments.filter((id) => validIds.has(id));
  const qId = crypto.randomUUID();
  const aId = crypto.randomUUID();
  const now = new Date();
  const qAt = now.toISOString();
  const aAt = new Date(now.getTime() + 1).toISOString();
  const insert = db.prepare(
    "INSERT INTO chat_messages (id, meeting_id, sender_user_id, original_text, source_language, created_at, is_assistant, cited_segment_ids) VALUES (?,?,?,?,?,?,?,?)",
  );
  db.transaction(() => {
    insert.run(qId, meeting.id, r.uid, parsed.data.question, parsed.data.source_lang, qAt, 0, null);
    insert.run(aId, meeting.id, "assistant", ai.answer, "en", aAt, 1, JSON.stringify(cited));
  })();

  return NextResponse.json({
    question: { id: qId, created_at: qAt },
    answer: { id: aId, text: ai.answer, cited_segment_ids: cited, created_at: aAt },
  });
}
