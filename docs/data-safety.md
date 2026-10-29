# Play Console Data Safety (draft)

- Data collected: account email/display name/photo (Firebase), meeting audio/video (live only unless recorded), chat/notes/transcripts, recordings (if enabled), screen-share frames (only while sharing), crash/perf diagnostics.
- Data shared: none to third parties; all AI processing self-hosted/local.
- Data handling: in transit via HTTPS/WSS; at rest in app's encrypted storage + server-side DB/storage.
- Deletion: user can request deletion (not yet implemented endpoint — TODO).
- Security: follow HTTPS, encryption at rest where possible; private media served via authenticated endpoints.
- Foreground services: microphone, camera, media projection declared with rationale.