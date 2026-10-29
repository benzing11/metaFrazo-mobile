import { NextResponse } from "next/server";
import { getDb } from "./db";

const admin = require("firebase-admin");

let initialized = false;
function ensureAdmin() {
  if (initialized) return;
  if (!admin.getApps || !admin.getApps().length) {
    if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      const sa = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
      admin.initializeApp({ credential: admin.credential.cert(sa) });
    } else {
      admin.initializeApp();
    }
  }
  initialized = true;
}

export type AuthResult = { uid: string; email?: string } | { error: NextResponse };

export async function requireUser(req: Request): Promise<AuthResult> {
  if (process.env.DEV_AUTH_BYPASS === "true" && process.env.NODE_ENV !== "production") {
    const uid = "dev-user";
    const db = getDb();
    const now = new Date().toISOString();
    const existing = db.prepare("SELECT id FROM users WHERE id = ?").get(uid);
    if (!existing) {
      db.prepare("INSERT INTO users (id, email, display_name, created_at, updated_at) VALUES (?,?,?,?,?)").run(uid, "dev@example.com", "Dev User", now, now);
    }
    return { uid, email: "dev@example.com" };
  }
  const authHeader = req.headers.get("authorization") || "";
  const match = authHeader.match(/^Bearer (.+)$/);
  if (!match) {
    return { error: NextResponse.json({ error: "Missing token" }, { status: 401 }) };
  }
  try {
    ensureAdmin();
    const decoded = await admin.auth().verifyIdToken(match[1]);
    return { uid: decoded.uid, email: decoded.email };
  } catch {
    return { error: NextResponse.json({ error: "Invalid token" }, { status: 401 }) };
  }
}