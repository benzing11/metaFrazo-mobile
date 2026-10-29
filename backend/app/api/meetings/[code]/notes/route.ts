import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

const UpsertSchema = z.object({ content: z.string() });

export async function GET(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const notes = db.prepare("SELECT id, content, updated_at FROM notes WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  return NextResponse.json({ note: notes || null });
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = UpsertSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const now = new Date().toISOString();
  const existing = db.prepare("SELECT id FROM notes WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid) as any;
  if (existing) {
    db.prepare("UPDATE notes SET content = ?, updated_at = ? WHERE id = ?").run(parsed.data.content, now, existing.id);
    return NextResponse.json({ id: existing.id, updated_at: now });
  }
  const id = crypto.randomUUID();
  db.prepare("INSERT INTO notes (id, meeting_id, user_id, content, updated_at) VALUES (?,?,?,?,?)").run(id, meeting.id, r.uid, parsed.data.content, now);
  return NextResponse.json({ id, updated_at: now });
}