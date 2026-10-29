# Security Notes

- Backend verifies Firebase ID token on every protected request; ignores client-supplied user IDs.
- Ownership/membership checked on every meeting, chat, note, recording request.
- Secrets only via env vars; .env.example has placeholders only.
- Android: HTTPS only in release (network_security_config), tokens in encrypted storage, no secrets in APK.
- Private media served via authenticated, range-capable streaming (no public URLs).
- Recording consent events stored; host-only start/stop/delete.
- Never send private meeting content to external AI providers without explicit consent (all AI is local/self-hosted).