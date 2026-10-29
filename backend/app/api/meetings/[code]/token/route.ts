import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { AccessToken } from "livekit-server-sdk";

const LOCAL_HOST = /^(localhost|127\.\d+\.\d+\.\d+|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)$/;

// Dev only: a LAN/localhost LiveKit URL is rewritten to the host the client used to reach this backend
// (e.g. 10.0.2.2 from the emulator, or the Mac's current LAN IP), so the signal WebSocket is reachable.
function resolveLivekitUrl(req: NextRequest, configured: string): string {
  if (process.env.NODE_ENV === "production") return configured;
  try {
    const lk = new URL(configured);
    const reqHost = (req.headers.get("x-forwarded-host") || req.headers.get("host") || "").split(",")[0].trim();
    const reqHostname = new URL(`http://${reqHost}`).hostname;
    if (!reqHostname || !LOCAL_HOST.test(lk.hostname) || !LOCAL_HOST.test(reqHostname)) return configured;
    lk.hostname = reqHostname;
    return lk.toString().replace(/\/$/, "");
  } catch {
    return configured;
  }
}

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
  const url = resolveLivekitUrl(req, process.env.LIVEKIT_URL || "ws://localhost:7880");
  const user = db.prepare("SELECT display_name FROM users WHERE id = ?").get(r.uid) as { display_name: string | null } | undefined;
  const at = new AccessToken(apiKey, apiSecret, { identity: r.uid, name: user?.display_name || r.uid });
  at.addGrant({ roomJoin: true, room: row.code, canPublish: true, canSubscribe: true, canPublishData: true });
  const token = await at.toJwt();
  return NextResponse.json({ token, url, room: row.code });
}