import Database from "better-sqlite3";
import fs from "node:fs";
import path from "node:path";

// Cache on globalThis, not module scope: Next.js dev reloads modules, and a fresh
// native handle per reload leaves better-sqlite3 statements to be finalized after
// their environment is torn down, which aborts the process on a native assertion.
const globalForDb = globalThis as typeof globalThis & { __metafrazoDb?: Database.Database };

export function getDb(): Database.Database {
  const cached = globalForDb.__metafrazoDb;
  if (cached) return cached;
  const dbPath = process.env.DATABASE_PATH || "./data/metafrazo.db";
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new Database(dbPath);
  globalForDb.__metafrazoDb = db;
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  db.exec("CREATE TABLE IF NOT EXISTS _migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)");
  const migrationsDir = path.join(process.cwd(), "db", "migrations");
  if (fs.existsSync(migrationsDir)) {
    const files = fs.readdirSync(migrationsDir).filter((f) => f.endsWith(".sql")).sort();
    for (const f of files) {
      const exists = db.prepare("SELECT 1 FROM _migrations WHERE name = ?").get(f);
      if (!exists) {
        const sql = fs.readFileSync(path.join(migrationsDir, f), "utf8");
        db.exec(sql);
        db.prepare("INSERT INTO _migrations (name, applied_at) VALUES (?,?)").run(f, new Date().toISOString());
      }
    }
  }
  return db;
}