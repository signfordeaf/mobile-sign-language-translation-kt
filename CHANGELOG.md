# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-07-27

**Breaking change.** The old `SignForDeafUtil` / `SignForDeafTranslate` API has been
replaced by a single `SignLanguage` facade configured with `SignLanguageConfig`. See
[Migration](#migration-from-1x) below.

### Added
- **`SignLanguage` facade** — one entry point for the whole SDK: `configure`, `enable`,
  `disable`, `translate`, `cancelTranslation`, plus bottom-sheet and tap-to-translate
  controls.
- **`SignLanguageConfig`** — declarative configuration (`apiKey`, `apiUrl`, `language`,
  `fdid`, `tid`, `theme`, `floatingButton`).
- **Theming** via `SignLanguageTheme(primaryColor, textColor)` — tints the logo, title,
  close button, loading ring and the translated text in the bottom sheet.
- **Sensitive-data protection** — personal data is never sent for translation:
  - automatic detection of T.C. Kimlik No (checksum), credit card (Luhn), Turkish IBAN,
    e-mail and GSM phone numbers;
  - manual marking via `registerSensitive`, `unregisterSensitive`, `markSensitive(view)`;
  - a branded "blocked" notice and `TranslationListener.onTranslationBlocked(text)`.
- **Full-screen loading overlay** (spinning ring + centered logo) shown while the video
  is prepared. The bottom sheet opens only once the video is buffered, so it never shows
  its own spinner.
- **Draggable, edge-snapping floating button** (`showFloatingButton` /
  `FloatingButtonConfig`) that toggles tap-to-translate mode and stays in sync with it.
- **Tap-to-translate mode** (`setTapToTranslateMode`) and a mode-change listener
  (`setOnTapToTranslateModeChangeListener`) to keep a host UI switch in sync.
- **Listeners**: `TranslationListener` (text selected / start / complete / error /
  blocked) and `BottomSheetListener` (open / close / video start / end).
- **Smooth bottom-sheet slide-up** — the sheet now animates up from the bottom instead
  of appearing abruptly.
- **Minimal error notice** — on any request failure the loading overlay is dismissed and
  a minimal branded notice ("something went wrong, try again later") is shown; the bottom
  sheet is never opened on failure.
- **Distribution on Maven Central** (`io.github.signfordeaf:signtranslate`) in addition
  to JitPack. Artifacts are signed via the vanniktech maven-publish plugin.

### Changed
- Targets **Java 17** and `minSdk 24`.
- Public API surface moved under the `SignLanguage` object; internals reorganized into
  `bottomsheet`, `floating`, `loading`, `network`, `sensitive` and `textselection`
  packages.

### Removed
- Old `SignForDeafUtil`, `SignForDeafTranslate`, `SignForDeafConfig`, `ErrorActivity`,
  `LoadingActivity` and `TranslateVideo` APIs.
- **German, French and Spanish** sign-language options — the SDK now supports
  **Turkish (`tr`)**, **English (`en`)** and **Arabic (`ar`)** only. The remaining
  languages are not yet supported.

### Migration from 1.x

```kotlin
// Before (1.0.3)
SignForDeafUtil.initialize("YOUR-API-KEY", requestUrl = "YOUR-REQUEST-URL")
SignForDeafTranslate.makeAllTextSelectable(this)

// After (2.0.0)
SignLanguage.configure(
    this,
    SignLanguageConfig(
        apiKey = "YOUR-API-KEY",
        apiUrl = "YOUR-REQUEST-URL",
        language = Language.TURKISH,
        theme = SignLanguageTheme(primaryColor = "#6750A4")
    )
)
```

- Call `SignLanguage.configure(...)` from `onCreate` of an `AppCompatActivity`, after
  `setContentView`. Text selection is enabled automatically.
- If you passed `Language.GERMAN`, `Language.FRENCH` or `Language.SPANISH`, switch to a
  supported language (`TURKISH`, `ENGLISH`, or `ARABIC`).
- The SDK now requires Java 17 (`sourceCompatibility`/`targetCompatibility` and
  `jvmTarget = "17"`).

[2.0.0]: https://github.com/signfordeaf/mobile-sign-language-translation-kt/releases/tag/v2.0.0
