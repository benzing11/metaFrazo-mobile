# Android UI/UX Redesign + Feature Completion Design

Date: 2026-10-30

## Context and goal

Fix UI bugs, make the Android app feel like a polished product, and bring in the features
still unchecked in PROGRESS.md: auth groundwork, adaptive layouts, meeting UX polish,
per-recipient translation contract, screen-translation UX, and content-screen polish. Heavy
runtime dependencies (Docker, Tesseract, Faster-Whisper, IndicTrans2/Torch) remain scaffolded
with honest status reporting; verification is limited to the local toolchain
(`./gradlew assembleDebug`/`test`, backend `typecheck`/`build`, AI `/health`).

## Aesthetic direction

Expressive Material 3: Pixel/Material You-inspired. Keep Compose Material3 1.3.1 (stable,
per PROGRESS constraint) but stop using the default purple fallback. Use a tuned triad
(indigo primary, coral tertiary, teal secondary), full M3 type scale (all 15 roles), generous
shape (cards 16dp, containers 28dp), springy-but-subtle motion, dynamic color in debug with
a strong static fallback. Dark theme is a first-class target.

## Information architecture

- Keep the single-activity NavHost.
- Top-level scaffold switches by `WindowSizeClass`: `NavigationBar` (compact),
  `NavigationRail` (medium), persistent drawer (expanded).
- Nested screens use a `TopAppBar` with back, scroll-aware elevation, contextual actions.
- All lists support pull-to-refresh where data is fetched.
- `AppScaffold` owns edge-to-edge insets; no screen draws under system bars (current bug).

## Shared design system

`ui/theme`, `ui/components`, `ui/state`:
- Tokens: `Color`, `Type`, `Shape` rewritten with expressive scale.
- Reusable components: `AppScaffold`, `AppTopBar`, `EmptyState`, `ErrorState`,
  `LoadingState`, `OfflineBanner`, `StatusBanner`, `MetricCard`, `VideoTile`,
  `ChatBubble`, `LanguageChipRow`, `ConfirmationDialog`, `RecordingIndicator`.
- Every screen renders exactly one of `Loading`/`Empty`/`Error`/`Content` plus optional
  `Offline` overlay.

## Auth groundwork

- `AuthRepository` backed by Firebase Auth + Credential Manager when Firebase config is
  present; otherwise "Guest (dev)" mode using the existing `X-Dev-User-Id` header.
- Settings exposes current mode honestly; no screen is gated in guest mode.
- ID token stored in `EncryptedSharedPreferences`; backend calls use
  `Authorization: Bearer <idToken>` when signed in, dev header otherwise.

## Adaptive layouts

`androidx.window:window` provides `WindowSizeClass`, computed once in `MainActivity`.
- Compact: single pane.
- Medium: list/detail for Chat/Notes/Transcript/Recordings.
- Expanded: nav rail + persistent detail pane.
- Meeting room adaptive video grid; controls collapse into an overflow sheet on compact.

## Meeting room UX

- Bottom control bar (mic, camera, share, record, more) + overflow sheet (no off-screen buttons).
- Active speaker: animated primary border + elevated scale + initials avatar fallback.
- Status header: code, connection state, reconnecting banner, recording indicator.
- PiP/foreground-service UX: notification actions.
- Empty state with copy-code action.

## Captions / translation

- Broadcast captions retained; chips use full language names, auto-scroll, pinned "last
  spoken", TTS readiness fixed.
- Add client preference store + settings UI; per-recipient delivery contract:
  client publishes target lang over a data channel; backend/AI filters broadcasts.
  Until backend filtering ships, broadcast remains a clearly labeled fallback.

## Content screens

- Chat: bubbles, grouping, retry, offline banner, empty state.
- Notes: 1s debounce autosave with "Saving…/Saved" status; safe on failure.
- Transcripts: search filter, language tabs, focus assistant as collapsible card.
- Recordings: empty state, pull-to-refresh, confirm-delete, metadata, player with
  play/pause, seek, buffering, error.

## Screen translation

- Fix overlay coordinate math (normalized → letterboxed displayed rect), clip, avoid overlap.
- Wire `ScreenTranslateManager` generation gating through MediaProjection + ImageReader
  capture (throttled) + overlay updates; defensive fallback with honest status.
- Meeting toggle "Translate shared screen" with permission flow.

## Concrete UI bugs to fix

1. Window insets on every screen (system bars overlap).
2. Player URL hardcoded to `10.0.2.2` — use `BuildConfig.BACKEND_URL`.
3. Join deep link `join/{code}` does not auto-fill code (currently skipped with comment).
4. Captions TTS readiness captured incorrectly.
5. Fragile JSON string building (injection risk) in Chat/Notes recording flows.
6. Diagnostics: no refresh error detail; add retry + structured status.
7. Overlay coordinate mismatch; regions can overflow.
8. Meeting controls overflow off-screen.
9. Missing empty/loading/error states across data screens.

## Accessibility

Semantics on icon buttons, 48dp touch targets, contrast via theme tokens, TalkBack
labels, reduced-motion respected.

## New dependencies (version catalog)

- `com.google.firebase:firebase-bom` + `firebase-auth-ktx`
- `androidx.credentials:credentials` + `credentials-play-services-auth`
- `androidx.security:security-crypto`
- `androidx.window:window`
- `androidx.compose.material3:material3-window-size-class` (optional)

## Verification

- `./gradlew clean assembleDebug` and `./gradlew test` (unit; requires JDK17 + ANDROID_HOME).
- `cd backend && npm run typecheck && npm run build`.
- AI: `/health` honest reporting.
- Manual QA checklist for device-only flows.

## Risks / reversal

Work in a single feature branch with commits per sub-domain. Dev mode keeps the app usable
without Firebase. Heavy features stay scaffolded with honest status. No dependency on
Docker/ML in this pass.
