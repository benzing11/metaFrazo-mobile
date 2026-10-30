import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import fs from "node:fs";

function contentTypeFor(path: string): string {
  const ext = (path.split(".").pop() || "").toLowerCase();
  switch (ext) {
    case "mkv":
      return "video/x-matroska";
    case "webm":
      return "video/webm";
    case "mov":
      return "video/quicktime";
    case "m4v":
    case "mp4":
    default:
      return "video/mp4";
  }
}

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
  const contentType = contentTypeFor(rec.storage_path);
  const range = req.headers.get("range");
  if (range) {
    const m = /^bytes=(\\d*)-(\\d*)$/.exec(range.trim());
    if (!m || (m[1] === "" && m[2] === "")) {
      return new NextResponse(null, { status: 416, headers: { "Content-Range": `bytes */${stat.size}` } });
    }
    let start = m[1] === "" ? Math.max(0, stat.size - parseInt(m[2], 10)) : parseInt(m[1], 10);
    let end = m[2] === "" ? stat.size - 1 : parseInt(m[2], 10);
    if (isNaN(start) || isNaN(end) || start < 0 || end < 0 || start >= stat.size) {
      return new NextResponse(null, { status: 416, headers: { "Content-Range": `bytes */${stat.size}` } });
    }
    end = Math.min(end, stat.size - 1);
    if (start > end) {
      return new NextResponse(null, { status: 416, headers: { "Content-Range": `bytes */${stat.size}` } });
    }
    const chunkSize = end - start + 1;
    const stream = fs.createReadStream(rec.storage_path, { start, end });
    return new NextResponse(stream as any, {
      status: 206,
      headers: {
        "Content-Range": `bytes ${start}-${end}/${stat.size}`,
        "Accept-Ranges": "bytes",
        "Content-Length": String(chunkSize),
        "Content-Type": contentType,
      },
    });
  }
  const stream = fs.createReadStream(rec.storage_path);
  return new NextResponse(stream as any, {
    status: 200,
    headers: {
      "Accept-Ranges": "bytes",
      "Content-Length": String(stat.size),
      "Content-Type": contentType,
    },
  });
}