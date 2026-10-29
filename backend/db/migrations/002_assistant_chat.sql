ALTER TABLE chat_messages ADD COLUMN is_assistant INTEGER NOT NULL DEFAULT 0;
ALTER TABLE chat_messages ADD COLUMN cited_segment_ids TEXT;

CREATE TABLE IF NOT EXISTS chat_translations (
  id TEXT PRIMARY KEY,
  message_id TEXT NOT NULL,
  target_language TEXT NOT NULL,
  translated_text TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transcript_segments_meeting_seq ON transcript_segments(meeting_id, sequence);
