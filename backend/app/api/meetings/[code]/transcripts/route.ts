import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

const SegSchema = z.object({
  source_lang: z.string(),
  original_text: z.string(),
  sequence: z.number().int(),
  speaker_user_id: z.string().optional(),
  start_ms: z.number().int().optional(),
  end_ms: z.number().int().optional(),
  translations: z.array(z.object({ target_lang: z.string(), translated_text: z.string() })).optional(),
});

const AppendSchema = z.object({ segments: z.array(SegSchema).min(1) });

export async function GET(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const segs = db.prepare("SELECT id, speaker_user_id, source_language, original_text, sequence, start_ms, end_ms, created_at FROM transcript_segments WHERE meeting_id = ? ORDER BY sequence ASC").all(meeting.id) as any[];
  const out = segs.map((s) => {
    const tr = db.prepare("SELECT target_language, translated_text FROM translation_results WHERE segment_id = ?").all(s.id);
    return { ...s, translations: tr };
  });
  return NextResponse.json({ segments: out });
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = AppendSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const now = new Date().toISOString();
  const ids: string[] = [];
  const insertSeg = db.prepare("INSERT INTO transcript_segments (id, meeting_id, speaker_user_id, source_language, original_text, sequence, start_ms, end_ms, created_at) VALUES (?,?,?,?,?,?,?,?,?)");
  const insertTr = db.prepare("INSERT INTO translation_results (id, segment_id, target_language, translated_text, created_at) VALUES (?,?,?,?,?)");
  for (const s of parsed.data.segments) {
    const id = crypto.randomUUID();
    insertSeg.run(id, meeting.id, r.uid, s.source_lang, s.original_text, s.sequence, s.start_ms ?? null, s.end_ms ?? null, now);
    ids.push(id);
    for (const t of s.translations || []) {
      insertTr.run(crypto.randomUUID(), id, t.target_lang, t.translated_text, now);
    }
  }
  return NextResponse.json({ inserted: ids.length, ids });
}