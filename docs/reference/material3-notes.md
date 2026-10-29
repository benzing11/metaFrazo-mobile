# Material 3 Reference Notes

Collected from official sources for use when expanding the Metafrazo app.
Components/effects should only be introduced when a feature actually needs them.

## Sources
- Material Design 3 guidelines: https://m3.material.io/
- Material 3 for Jetpack Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Android Developers UI design: https://developer.android.com/design/ui/mobile
- Material Theme Builder: https://material-foundation.github.io/material-theme-builder/
- Android accessibility: https://developer.android.com/guide/topics/ui/accessibility
- MDC-Android (maintenance mode, Views-based): https://github.com/material-components/material-components-android
- MDC component docs index: https://github.com/material-components/material-components-android/tree/master/docs/components

## Key decision (2026)
MDC-Android is in **maintenance mode**. Google is "all-in" on Compose. All new work uses **Jetpack Compose Material 3** (`androidx.compose.material3`). MDC is reference-only.

## Component inventory (MDC catalog -> Compose Material 3 equivalents)
- BadgeDrawable -> `Badge` / `BadgedBox`
- Banner -> custom (no direct Compose equivalent; use Card + actions)
- BottomAppBar -> `BottomAppBar`
- BottomNavigation -> `NavigationBar`
- BottomSheet -> `ModalBottomSheet` / `BottomSheetScaffold`
- Button / CommonButton / IconButton -> `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `IconButton`, `FilledIconButton`, `FilledTonalIconButton`, `OutlinedIconButton`
- ButtonGroup / ToggleButtonGroup -> `ToggleButton` / `ToggleButtonGroup` (experimental)
- Card -> `Card`, `ElevatedCard`, `OutlinedCard`
- Carousel -> `HorizontalMultiBrowseCarousel`, `HorizontalUncontainedCarousel` (experimental)
- Checkbox -> `Checkbox`, `TriStateCheckbox`
- Chip -> `AssistChip`, `FilterChip`, `InputChip`, `SuggestionChip`
- DataTable -> custom (no direct equivalent)
- DatePicker / TimePicker -> `DatePicker`, `DateRangePicker`, `TimePicker`, `TimeInput`
- Dialog -> `AlertDialog`, `BasicAlertDialog`
- Divider -> `HorizontalDivider`, `VerticalDivider`
- DockedFloatingToolbars / FloatingToolbar -> `FloatingToolbar` (experimental)
- ExtendedFloatingActionButton / FloatingActionButton / FAB Menu -> `FloatingActionButton`, `ExtendedFloatingActionButton`, `FloatingActionButtonMenu`
- ImageList -> `LazyVerticalGrid` + cards
- List -> `ListItem`
- LoadingIndicator -> `LoadingIndicator` (experimental, M3 Expressive)
- Menu -> `DropdownMenu`, `ExposedDropdownMenuBox`
- NavigationDrawer -> `ModalNavigationDrawer`, `PermanentNavigationDrawer`, `DismissibleNavigationDrawer`
- NavigationRail -> `NavigationRail`
- ProgressIndicator -> `LinearProgressIndicator`, `CircularProgressIndicator`
- RadioButton -> `RadioButton`
- Search -> `SearchBar`, `DockedSearchBar`
- SideSheet -> custom drawer/sheet
- Slider -> `Slider`, `RangeSlider`
- Snackbar -> `Snackbar`, `SnackbarHost`
- SplitButton -> `SplitButton` (experimental)
- Switch -> `Switch`
- Tabs -> `Tab`, `TabRow`, `ScrollableTabRow`, `PrimaryTabRow`, `SecondaryTabRow`
- TextField -> `TextField`, `OutlinedTextField`
- Tooltip -> `TooltipBox`, `PlainTooltip`, `RichTooltip`
- TopAppBar -> `TopAppBar`, `CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`

## Visual effects & tokens to consider (introduce only when needed)
- Elevation: 0-5 levels; use tonal elevation (surface tint) rather than heavy shadows.
- Ripple / state layers for hover, focus, pressed, dragged, disabled.
- Motion: container transform, shared axis, fade through, fade; respect reduced motion.
- Dynamic color (Android 12+) with static fallback.
- Shape scale: extra-small 4, small 8, medium 12, large 16, extra-large 28 dp.
- Typography roles: display/headline/title/body/label x large/medium/small.
- Scrim, modal scrim opacity.
- Adaptive layouts: window size classes (compact/medium/expanded), canonical layouts (list-detail, feed, supporting pane).
- Edge-to-edge, predictive back, haptics.

## Material Theme Builder workflow
1. Open https://material-foundation.github.io/material-theme-builder/
2. Pick a seed color (and optional secondary/tertiary).
3. Export "Android (Compose)" -> produces a `Color.kt` with tonal palettes (light + dark) and `Theme.kt`.
4. Replace `ui/theme/Color.kt` palette values; keep `MetafrazoTheme` wiring (dynamic color + fallback).
5. Supports custom colors and dark mode; regenerate when brand color changes.

## Accessibility checklist
- contentDescription on meaningful visuals; null for decorative.
- Touch targets >= 48dp.
- Contrast: text on surfaces meets WCAG.
- Respect font scaling (sp units; avoid fixed heights for text).
- Clear focus indicators; keyboard navigation.
- Don't convey info by color alone.