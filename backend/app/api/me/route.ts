import { NextRequest, NextResponse } from "next/server";
import { requireUser } from "@/lib/auth";
import { getDb } from "@/lib/db";

export async function GET(req: NextRequest) {
  const r = await requireUser(req);
  if ("error" in r) return r.error;
  const db = getDb();
  const user = db.prepare("SELECT * FROM users WHERE id = ?").get(r.uid);
  const prefs = db.prepare("SELECT * FROM user_preferences WHERE user_id = ?").get(r.uid);
  return NextResponse.json({ user, preferences: prefs });
}