import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { getEgressClient } from "@/lib/egress";

const StopSchema = z.object({ egressId: z.string(), recordingId: z.string() });

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const body = await req.json().catch(() => ({}));
  const parsed = StopSchema.safeParse(body);
  if (!parsed.success) return NextResponse.json({ error: parsed.error.flatten() }, { status: 400 });
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT role FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid) as any;
  if (!mem || mem.role !== "host") return NextResponse.json({ error: "Host only" }, { status: 403 });

  const now = new Date().toISOString();
  db.prepare("UPDATE recordings SET status = ?, updated_at = ? WHERE id = ?").run("stopping", now, parsed.data.recordingId);

  try {
    const client = getEgressClient();
    await client.stopEgress(parsed.data.egressId);
    db.prepare("UPDATE recordings SET status = ?, updated_at = ? WHERE id = ?").run("processing", new Date().toISOString(), parsed.data.recordingId);
    return NextResponse.json({ status: "processing" });
  } catch (e: any) {
    db.prepare("UPDATE recordings SET status = ?, updated_at = ? WHERE id = ?").run("failed", new Date().toISOString(), parsed.data.recordingId);
    return NextResponse.json({ status: "failed", error: e?.message || String(e) }, { status: 500 });
  }
}