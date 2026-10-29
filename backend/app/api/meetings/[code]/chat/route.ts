import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

const SendSchema = z.object({ text: z.string().min(1), source_lang: z.string().default("en") });

export async function GET(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const rows = db.prepare("SELECT id, sender_user_id, original_text, source_language, created_at, is_assistant, cited_segment_ids FROM chat_messages WHERE meeting_id = ? ORDER BY created_at ASC").all(meeting.id) as any[];
  const msgs = rows.map((m) => ({
    id: m.id,
    sender_user_id: m.sender_user_id,
    original_text: m.original_text,
    source_language: m.source_language,
    created_at: m.created_at,
    is_assistant: m.is_assistant === 1,
    cited_segment_ids: m.cited_segment_ids ? JSON.parse(m.cited_segment_ids) : [],
  }));
  return NextResponse.json({ messages: msgs });
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = SendSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  db.prepare("INSERT INTO chat_messages (id, meeting_id, sender_user_id, original_text, source_language, created_at) VALUES (?,?,?,?,?,?)").run(id, meeting.id, r.uid, parsed.data.text, parsed.data.source_lang, now);
  return NextResponse.json({ id, created_at: now });
}