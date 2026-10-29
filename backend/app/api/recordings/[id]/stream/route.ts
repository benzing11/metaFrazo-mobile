import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import fs from "node:fs";

export async function GET(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { id } = await params;
  const db = getDb();
  const rec = db.prepare(
    `SELECT r.storage_path FROM recordings r JOIN meetings m ON m.id = r.meeting_id
     JOIN memberships mem ON mem.meeting_id = m.id
     WHERE r.id = ? AND mem.user_id = ?`,
  ).get(id, r.uid) as any;
  if (!rec || !rec.storage_path || !fs.existsSync(rec.storage_path)) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }
  const stat = fs.statSync(rec.storage_path);
  const range = req.headers.get("range");
  if (range) {
    const parts = range.replace(/bytes=/, "").split("-");
    const start = parseInt(parts[0], 10);
    const end = parts[1] ? parseInt(parts[1], 10) : stat.size - 1;
    const chunkSize = end - start + 1;
    const stream = fs.createReadStream(rec.storage_path, { start, end });
    return new NextResponse(stream as any, {
      status: 206,
      headers: {
        "Content-Range": `bytes ${start}-${end}/${stat.size}`,
        "Accept-Ranges": "bytes",
        "Content-Length": String(chunkSize),
        "Content-Type": "video/mp4",
      },
    });
  }
  const stream = fs.createReadStream(rec.storage_path);
  return new NextResponse(stream as any, {
    status: 200,
    headers: {
      "Accept-Ranges": "bytes",
      "Content-Length": String(stat.size),
      "Content-Type": "video/mp4",
    },
  });
}