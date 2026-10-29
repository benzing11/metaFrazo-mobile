import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

export async function GET(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { id } = await params;
  const db = getDb();
  const rec = db.prepare(
    `SELECT r.*, m.code as meeting_code FROM recordings r JOIN meetings m ON m.id = r.meeting_id
     JOIN memberships mem ON mem.meeting_id = m.id
     WHERE r.id = ? AND mem.user_id = ?`,
  ).get(id, r.uid) as any;
  if (!rec) return NextResponse.json({ error: "Not found" }, { status: 404 });
  return NextResponse.json({ recording: rec });
}

export async function DELETE(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { id } = await params;
  const db = getDb();
  const rec = db.prepare(
    `SELECT r.id, r.storage_path FROM recordings r JOIN meetings m ON m.id = r.meeting_id
     JOIN memberships mem ON mem.meeting_id = m.id
     WHERE r.id = ? AND mem.user_id = ?`,
  ).get(id, r.uid) as any;
  if (!rec) return NextResponse.json({ error: "Not found" }, { status: 404 });
  // only host can delete; check role
  const mem = db.prepare("SELECT role FROM memberships m JOIN recordings r ON r.meeting_id = m.meeting_id WHERE r.id = ? AND m.user_id = ?").get(id, r.uid) as any;
  if (!mem || mem.role !== "host") return NextResponse.json({ error: "Host only" }, { status: 403 });
  const fs = await import("node:fs");
  if (rec.storage_path && fs.existsSync(rec.storage_path)) fs.unlinkSync(rec.storage_path);
  db.prepare("DELETE FROM recordings WHERE id = ?").run(id);
  return NextResponse.json({ deleted: true });
}