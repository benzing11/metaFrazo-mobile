import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

const JoinSchema = z.object({ code: z.string().min(1) });

export async function POST(req: NextRequest) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const body = await req.json().catch(() => ({}));
  const parsed = JoinSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const code = parsed.data.code.trim().toUpperCase().replace(/\s+/g, "");
  const db = getDb();
  const row = db.prepare("SELECT m.id, m.code, m.title, m.host_user_id FROM meetings m JOIN join_codes jc ON jc.meeting_id = m.id WHERE jc.code = ?").get(code) as any;
  if (!row) return NextResponse.json({ error: "Invalid meeting code" }, { status: 404 });
  const now = new Date().toISOString();
  db.prepare("INSERT OR IGNORE INTO memberships (meeting_id, user_id, role, joined_at) VALUES (?,?,?,?)").run(row.id, r.uid, row.host_user_id === r.uid ? "host" : "participant", now);
  return NextResponse.json({ meetingId: row.id, code: row.code, title: row.title, hostUserId: row.host_user_id });
}