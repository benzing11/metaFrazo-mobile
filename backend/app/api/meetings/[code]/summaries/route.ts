import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";
import { callAi, loadSegments } from "@/lib/transcript";

type AiSummary = {
  summary: string;
  abstract: string;
  key_points: string[];
  decisions: string[];
  action_items: string[];
};

function parseContent(content: string) {
  try {
    const j = JSON.parse(content);
    if (j && typeof j === "object" && "abstract" in j) return j;
  } catch { }
  return { abstract: content, key_points: [], decisions: [], action_items: [] };
}

export async function GET(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });
  const sums = db.prepare("SELECT id, content, created_at FROM summaries WHERE meeting_id = ? ORDER BY created_at DESC").all(meeting.id) as any[];
  return NextResponse.json({ summaries: sums.map((s) => ({ ...s, ...parseContent(s.content) })) });
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ code: string }> }) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const { code } = await params;
  const db = getDb();
  const meeting = db.prepare("SELECT id FROM meetings WHERE code = ?").get(code.trim().toUpperCase()) as any;
  if (!meeting) return NextResponse.json({ error: "Meeting not found" }, { status: 404 });
  const mem = db.prepare("SELECT 1 FROM memberships WHERE meeting_id = ? AND user_id = ?").get(meeting.id, r.uid);
  if (!mem) return NextResponse.json({ error: "Not a member" }, { status: 403 });

  const segments = loadSegments(db, meeting.id);
  if (segments.length === 0) {
    return NextResponse.json({ error: "No transcript yet. Speak with captions on, then try again." }, { status: 409 });
  }
  const text = segments.map((s) => `${s.speaker}: ${s.text}`).join("\n");

  let ai: AiSummary;
  try {
    ai = await callAi<AiSummary>("/summarize", { text });
  } catch (e) {
    return NextResponse.json({ error: "Summary service unavailable" }, { status: 502 });
  }

  if (typeof ai.abstract !== "string" || !Array.isArray(ai.key_points)) {
    return NextResponse.json({ error: "Summary service returned an unexpected response. Restart the AI service." }, { status: 502 });
  }
  const structured = {
    abstract: ai.abstract,
    key_points: ai.key_points,
    decisions: ai.decisions ?? [],
    action_items: ai.action_items ?? [],
  };
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  db.prepare("INSERT INTO summaries (id, meeting_id, content, created_at) VALUES (?,?,?,?)").run(id, meeting.id, JSON.stringify(structured), now);
  return NextResponse.json({ id, created_at: now, ...structured });
}
