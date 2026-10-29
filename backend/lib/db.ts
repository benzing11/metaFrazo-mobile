import Database from "better-sqlite3";
import fs from "node:fs";
import path from "node:path";

let db: Database.Database | null = null;

export function getDb(): Database.Database {
  if (db) return db;
  const dbPath = process.env.DATABASE_PATH || "./data/metafrazo.db";
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  db = new Database(dbPath);
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