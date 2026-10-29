# Android UI/UX Redesign + Feature Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Android UI bugs and redesign the app in Expressive Material 3 while implementing the unchecked PROGRESS.md features (auth groundwork, adaptive layouts, meeting UX, captions contract, content screens, screen translation UX), keeping heavy runtime features scaffolded with honest status.

**Architecture:** Keep the single-activity NavHost and existing MVVM; introduce a shared AppScaffold with insets, an expressive design system (theme + components + state coverage), an auth repository with Firebase-or-guest fallback, WindowSizeClass-driven adaptive navigation, and redesigned screens. Data screens gain Loading/Empty/Error/Offline states. Heavy features (per-recipient delivery, E2E screen capture) ship with client contract + honest fallback.

**Tech Stack:** Kotlin 2.0, Jetpack Compose BOM 2024.12, Material3 1.3.1, AndroidX Window, Security Crypto, Firebase Auth (optional), Credential Manager, LiveKit Android 2.29.2, ExoPlayer 1.4, Room-less (org.json). Backend Next.js + SQLite unchanged except documented contracts.

---

## File structure map

Create:
- `app/src/main/java/com/metafrazo/app/ui/layout/AppScaffold.kt` (insets + top-level adaptive scaffold)
- `app/src/main/java/com/metafrazo/app/ui/state/UiState.kt` (Loading/Empty/Error/Content/Offline)
- `app/src/main/java/com/metafrazo/app/ui/components/*.kt` (EmptyState, ErrorState, LoadingState, OfflineBanner, RecordingIndicator, ChatBubble, VideoTile, etc.)
- `app/src/main/java/com/metafrazo/app/core/auth/AuthRepository.kt`
- `app/src/main/java/com/metafrazo/app/feature/auth/AuthViewModel.kt`
- `app/src/main/java/com/metafrazo/app/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/metafrazo/app/core/window/WindowSizeUtils.kt`
- `app/src/test/.../ui/...` (state helpers unit tests)

Modify:
- `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`
- `MainActivity.kt` (WindowSizeClass computation, auth bootstrap)
- `ui/navigation/AppNavHost.kt` (adaptive destinations, settings, deep link auto-fill)
- Every screen under `ui/screens/*` (insets, states, redesign)
- `feature/meeting/MeetingViewModel.kt` (screen share safety + auth token)
- `core/network/Api.kt` (Authorization header from auth)
- `core/screen/ScreenTranslateOverlay.kt` (coordinate fix)
- `gradle/libs.versions.toml` + `app/build.gradle.kts` (new deps)

Commit per task. Requires JDK17 + ANDROID_HOME. Heavy features get honest status in Diagnostics/PROGRESS.

---

## Task 1: Fix window-insets bug on all screens

**Files:**
- Create: `app/src/main/java/com/metafrazo/app/ui/layout/Insets.kt`
- Modify: every screen in `app/src/main/java/com/metafrazo/app/ui/screens/...` to apply `WindowInsets.systemBars`
- Test: manual visual check (unit test for helper if added)

- [ ] **Step 1: Add an insets helper**

Create `app/src/main/java/com/metafrazo/app/ui/layout/Insets.kt`:
```kotlin
package com.metafrazo.app.ui.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

object Insets {
    val systemBars: WindowInsets = WindowInsets.systemBars
}
```

- [ ] **Step 2: Use scaffold insets consistently**

For each standalone screen without a Scaffold, wrap content:
```kotlin
Modifier.fillMaxSize()
    .windowInsetsPadding(WindowInsets.systemBars)
    .padding(16.dp)
```
Import `androidx.compose.foundation.layout.windowInsetsPadding`.

- [ ] **Step 3: Run build**

Run: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/metafrazo/app/ui/layout android/app/src/main/java/com/metafrazo/app/ui/screens
git commit -m "fix(ui): apply system-bar insets on all screens"
```

---

## Task 2: Expressive Material 3 theme tokens

**Files:**
- Modify: `app/src/main/java/com/metafrazo/app/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/metafrazo/app/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/metafrazo/app/ui/theme/Shape.kt`

- [ ] **Step 1: Replace Color palette**

Use a tuned triad (not default purple). In `Color.kt`:
```kotlin
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B5CCC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF001551),
    secondary = Color(0xFF1F6F8B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC5E8FF),
    onSecondaryContainer = Color(0xFF001E2B),
    tertiary = Color(0xFFB23A48),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD9),
    onTertiaryContainer = Color(0xFF410009),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7F8FF),
    surface = Color(0xFFF7F8FF),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
)
```
Mirror roles in `DarkColorScheme` with darker surfaces and lighter on-palette values. Keep `dynamicColor` support in `Theme.kt` (already present).

- [ ] **Step 2: Populate full type scale**

In `Type.kt`, define all 15 M3 roles (displayLarge..labelSmall) with consistent weights and line heights. Use `FontFamily.Default` (optionally replace display with a custom bundled font in a later pass).

- [ ] **Step 3: Shape scale**

In `Shape.kt` keep corners: extraSmall 6, small 10, medium 16, large 22, extraLarge 32.

- [ ] **Step 4: Build + smoke test**

Run: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/metafrazo/app/ui/theme
git commit -m "feat(ui): expressive Material 3 theme tokens"
```

---

## Task 3: Fix concrete UI bugs

**Files:**
- Modify: `app/src/main/java/com/metafrazo/app/ui/screens/recordings/PlayerScreen.kt`
- Modify: `app/src/main/java/com/metafrazo/app/ui/screens/captions/CaptionsScreen.kt`
- Modify: `app/src/main/java/com/metafrazo/app/ui/components/ScreenTranslateOverlay.kt`
- Modify: `app/src/main/java/com/metafrazo/app/ui/screens/diagnostics/DiagnosticsScreen.kt`
- Modify: `app/src/main/java/com/metafrazo/app/ui/navigation/AppNavHost.kt`

- [ ] **Step 1: Player uses BuildConfig URL**

Replace hardcoded URL with `${BuildConfig.BACKEND_URL}/api/recordings/$recordingId/stream` and add Authorization property to the data source factory.

- [ ] **Step 2: Captions TTS readiness**

Fix `setLanguage` result capture before comparing.

- [ ] **Step 3: Overlay coordinate math**

Fix letterboxed rect calculation and clip regions.

- [ ] **Step 4: Join deep link auto-fill**

Pass `initialCode`, seed state, remove stale comment.

- [ ] **Step 5: Diagnostics retry**

Wrap fetch with retry and show error status.

- [ ] **Step 6: Build & commit**

Run: `JAVA_HOME=... ANDROID_HOME=... ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.
Commit message: `fix(ui): player URL, captions TTS, overlay coords, diagnostics retry`.
