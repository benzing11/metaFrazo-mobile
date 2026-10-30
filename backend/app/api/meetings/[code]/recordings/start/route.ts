import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { getEgressClient } from "@/lib/egress";

const StartSchema = z.object({ mode: z.string().default("standard") });

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = StartSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id, code FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT role FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid) as any;
  if (!mem || mem.role !== "host") return NextResponse.json({ error: "Host only" }, { status: 403 });

  // Prevent double-starting a recording while one is already in flight.
  const inFlight = db.prepare("SELECT id, status FROM recordings WHERE meeting_id = ? AND status IN ('starting','recording','stopping','processing') ORDER BY created_at DESC LIMIT 1").get(meeting.id) as any;
  if (inFlight) {
    return NextResponse.json({ recordingId: inFlight.id, status: inFlight.status, alreadyRunning: true }, { status: 409 });
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  const storageDir = process.env.RECORDINGS_DIR || "./data/recordings";
  const fs = await import("node:fs");
  if (!fs.existsSync(storageDir)) fs.mkdirSync(storageDir, { recursive: true });
  const filePath = `${storageDir}/${id}.mp4`;

  db.prepare("INSERT INTO recordings (id, meeting_id, mode, status, storage_path, language_config, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)").run(id, meeting.id, parsed.data.mode, "starting", filePath, null, now, now);

  // consent event
  db.prepare("INSERT INTO consent_events (id, meeting_id, user_id, event_type, created_at) VALUES (?,?,?,?,?)").run(crypto.randomUUID(), meeting.id, r.uid, "recording_start", now);

  try {
    const { EncodedFileOutput } = await import("livekit-server-sdk");
    const client = getEgressClient();
    const output = new EncodedFileOutput({ filepath: filePath });
    const info = await client.startRoomCompositeEgress(meeting.code, output);
    const egressId = (info as any).egressId;
    db.prepare("UPDATE recordings SET status = ?, updated_at = ? WHERE id = ?").run("recording", new Date().toISOString(), id);
    return NextResponse.json({ recordingId: id, egressId, status: "recording" });
  } catch (e: any) {
    db.prepare("UPDATE recordings SET status = ?, updated_at = ? WHERE id = ?").run("failed", new Date().toISOString(), id);
    return NextResponse.json({ recordingId: id, status: "failed", error: e?.message || String(e) }, { status: 500 });
  }
}