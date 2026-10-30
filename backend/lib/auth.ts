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
    // Each device sends its own dev id so two phones don't share one LiveKit identity (which kicks the other out).
    const headerId = req.headers.get("x-dev-user-id") || "";
    const uid = /^[A-Za-z0-9_-]{1,64}$/.test(headerId) ? headerId : "dev-user";
    const rawName = (req.headers.get("x-dev-user-name") || "").trim().slice(0, 40);
    const displayName = rawName || (uid === "dev-user" ? "Dev User" : `Guest ${uid.slice(-4).toUpperCase()}`);
    const email = `${uid}@dev.local`;
    const db = getDb();
    const now = new Date().toISOString();
    const existing = db.prepare("SELECT id, display_name FROM users WHERE id = ?").get(uid) as { id: string; display_name: string | null } | undefined;
    if (!existing) {
      db.prepare("INSERT INTO users (id, email, display_name, created_at, updated_at) VALUES (?,?,?,?,?)").run(uid, email, displayName, now, now);
    } else if (rawName && existing.display_name !== rawName) {
      db.prepare("UPDATE users SET display_name = ?, updated_at = ? WHERE id = ?").run(rawName, now, uid);
    }
    return { uid, email };
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