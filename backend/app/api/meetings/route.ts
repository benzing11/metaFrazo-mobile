import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { AccessToken } from "livekit-server-sdk";

const CreateSchema = z.object({ title: z.string().nullable().optional() });

function genCode() {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < 6; i++) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
}

export async function GET(req: NextRequest) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const db = getDb();
  const rows = db.prepare(
    `SELECT m.id, m.code, m.title, m.created_at, m.ended_at, mem.role
     FROM meetings m JOIN memberships mem ON mem.meeting_id = m.id
     WHERE mem.user_id = ? ORDER BY m.created_at DESC LIMIT 50`,
  ).all(r.uid);
  return NextResponse.json({ meetings: rows });
}

export async function POST(req: NextRequest) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const body = await req.json().catch(() => ({}));
  const parsed = CreateSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const now = new Date().toISOString();
  let code = genCode();
  // ensure unique
  while (db.prepare("SELECT 1 FROM meetings WHERE code = ?").get(code)) code = genCode();
  const id = crypto.randomUUID();
  db.prepare("INSERT INTO meetings (id, code, host_user_id, title, created_at) VALUES (?,?,?,?,?)").run(id, code, r.uid, parsed.data.title || null, now);
  db.prepare("INSERT INTO memberships (meeting_id, user_id, role, joined_at) VALUES (?,?,?,?)").run(id, r.uid, "host", now);
  db.prepare("INSERT INTO join_codes (code, meeting_id, created_at) VALUES (?,?,?)").run(code, id, now);
  return NextResponse.json({ meetingId: id, code, title: parsed.data.title || null });
}