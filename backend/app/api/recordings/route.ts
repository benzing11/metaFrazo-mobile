import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

export async function GET(req: NextRequest) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const db = getDb();
  const recs = db.prepare(
    `SELECT r.id, r.mode, r.status, r.duration_seconds, r.file_size_bytes, r.language_config, r.created_at, r.updated_at,
            m.code as meeting_code, m.title as meeting_title
     FROM recordings r JOIN meetings m ON m.id = r.meeting_id
     JOIN memberships mem ON mem.meeting_id = m.id
     WHERE mem.user_id = ? ORDER BY r.created_at DESC`,
  ).all(r.uid);
  return NextResponse.json({ recordings: recs });
}