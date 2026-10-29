import type Database from "better-sqlite3";

export const AI_BASE = process.env.AI_SERVICE_BASE_URL || "http://localhost:8000";

export type StoredSegment = { id: string; speaker: string; text: string };

export function loadSegments(db: Database.Database, meetingId: string): StoredSegment[] {
  const rows = db
    .prepare(
      `SELECT s.id, s.original_text, COALESCE(u.display_name, s.speaker_user_id, 'Speaker') AS speaker
       FROM transcript_segments s
       LEFT JOIN users u ON u.id = s.speaker_user_id
       WHERE s.meeting_id = ?
       ORDER BY s.sequence ASC, s.created_at ASC`,
    )
    .all(meetingId) as { id: string; original_text: string; speaker: string }[];
  return rows.map((r) => ({ id: r.id, speaker: r.speaker, text: r.original_text }));
}

export async function callAi<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${AI_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  });
  if (!res.ok) throw new Error(`AI service ${path} returned ${res.status}`);
  return (await res.json()) as T;
}
