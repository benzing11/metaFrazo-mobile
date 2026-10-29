import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

const AI = process.env.AI_SERVICE_BASE_URL || "http://localhost:8000";
const GenSchema = z.object({ text: z.string().min(1) });

export async function GET(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const sums = db.prepare("SELECT id, content, created_at FROM summaries WHERE meeting_id = ? ORDER BY created_at DESC").all(meeting.id);
  return NextResponse.json({ summaries: sums });
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = GenSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });

  let content = parsed.data.text;
  try {
    const res = await fetch(`${AI}/summarize`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text: parsed.data.text }) });
    const j = await res.json();
    content = j.summary || content;
  } catch { }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  db.prepare("INSERT INTO summaries (id, meeting_id, content, created_at) VALUES (?,?,?,?)").run(id, meeting.id, content, now);
  return NextResponse.json({ id, content, created_at: now });
}