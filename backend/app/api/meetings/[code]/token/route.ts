import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { AccessToken } from "livekit-server-sdk";

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const norm = code.trim().toUpperCase().replace(/\s+/g, "");
  const db = getDb();
  const row = db.prepare("SELECT m.id, m.code FROM meetings m JOIN join_codes jc ON jc.meeting_id = m.id WHERE jc.code = ?").get(norm) as any;
  if (!row) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  // membership check
  const mem = db.prepare("SELECT role FROM memberships WHERE meeting_id = ? AND user_id = ?").get(row.id, r.uid) as any;
  if (!mem) return NextResponse.json({ error: "Not a member of this meeting" }, { status: 403 });
  const apiKey = process.env.LIVEKIT_API_KEY || "devkey";
  const apiSecret = process.env.LIVEKIT_API_SECRET || "secret";
  const url = process.env.LIVEKIT_URL || "ws://localhost:7880";
  const at = new AccessToken(apiKey, apiSecret, { identity: r.uid, name: r.uid });
  at.addGrant({ roomJoin: true, room: row.code, canPublish: true, canSubscribe: true, canPublishData: true });
  const token = await at.toJwt();
  return NextResponse.json({ token, url, room: row.code });
}