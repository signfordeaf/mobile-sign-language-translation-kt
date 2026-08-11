# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-08-11

**v2 behavior.** The translation experience moves from a blocking half-screen bottom sheet to a
**non-modal corner player**, reaching parity with the SignForDeaf v2 spec (`docs/01`–`15`). The
public `SignLanguage` API is **unchanged** — this is a behavior change, not an API change.

### Added
- **Non-modal corner player** — a floating card (stage on top, control bar **below** it so the
  signer's hands are never covered, a scrolling caption sharing the control surface, a window pill
  that hangs above the stage, and a collapsed 132×44 bar). The host app stays readable and
  tappable throughout — no full-screen scrim.
- **Idle signer loop** — a looping signer plays on the stage before/while a translation renders
  (doc 10). While loading it sits under a translucent darkening plus a spinner. Four bundled clips
  (Kadir/Hesna/Jason/Owais) resolve from the `tid`/`fdid` in effect; drop them into `res/raw` (see
  `signtranslate/PLACEHOLDER_CLIPS.md`). Until then it degrades gracefully to a spinner/mark.
- **Smart passthrough** — taps are classified during hit-testing (`PassthroughContainer` +
  `TapTargetProbe`): while the player is open, a **tap** on any text — including a labelled control's
  own text — translates it; an icon/unlabelled control, an editable field, and scrolling all pass
  straight through to the app. A **long press** on a labelled control operates the control instead
  of translating it. Legacy capture is available via `smartPassthrough = false`; an opt-in
  `longPressToTranslate` reaches text the host itself made tappable.
- **Sentence granularity** — a tap translates the sentence under the finger, not the whole
  paragraph (`SentenceSegmenter`, doc 09), with lossless splitting, Turkish abbreviation/number
  handling and 900-char clause chunking.
- **Translation controller** — a proper state machine (`idle`/`loading`/`ready`/`error`/`blocked`)
  with request-token supersession, a 40-entry LRU cache, one-sentence-ahead prefetch, and
  backend `tid`/`fdid` adoption (doc 02/10).
- **Design tokens + WCAG contrast guard** (`SignTokens`, `ContrastGuard`) — foregrounds below
  4.5:1 against a configured background are corrected to black/white (doc 05).
- **Persisted preferences** — playback speed and loop are restored across launches under the
  `weaccess_sl_` key prefix (doc 14).
- **v2 configuration** — `SignLanguageCardConfig`, `granularity`, `maxSegmentChars`,
  `smartPassthrough`, `longPressToTranslate`, `autoEnable`, `originUrl`, and theme
  `onPrimaryColor`/`surfaceColor`/`cornerRadius`. All defaulted, so existing call sites compile.
- **Build-time credential injection in the example** — the `app/` demo reads `SIGNFORDEAF_API_KEY`
  / `_API_URL` / `_ORIGIN_URL` / `_FDID` / `_TID` from a Gradle property, `local.properties`, or an
  environment variable into `BuildConfig` and configures itself on launch (the Android equivalent of
  Flutter's `--dart-define`). See the README.

### Fixed
- **Tap-to-translate** — a tap on text inside a scroll view did not start a translation, because
  the passthrough claimed the gesture on `ACTION_UP` and Android does not route an intercepted UP
  to the container's `onTouchEvent` when a child (the scroll view) is handling it. The translation
  is now fired directly from `onInterceptTouchEvent`.
- **Player-control taps** — tapping the player's own controls (collapse/close/speed/loop) while it
  overlapped host text used to be swallowed by the passthrough (and could translate the text
  underneath). The passthrough now ignores touches over its own overlay.
- **Caption** — auto-scrolls only when the sentence is longer than the two visible lines; text that
  fits stays still.
- **Sensitive-data guard** — a valid T.C. Kimlik No whose checksum intermediate was negative could
  slip past the guard (Kotlin's sign-following `%`); it now uses a non-negative modulo (doc 11).

### Changed
- **`tid`/`fdid` are now optional** (default `null`). Left unset, the SDK auto-selects a default
  translator (**Hesna**) for the idle placeholder and the first request; set them only to pin a
  translator. The strongest selector is the **backend**: when it serves a translation under a
  different `tid`/`fdid` than requested, the SDK adopts it — the idle signer switches to that
  person and subsequent requests use the corrected pair for the rest of the session (doc 10). So
  after the initial placeholder, a different translator returned by the API takes over
  automatically.
- **Operating a labelled control** — a tap reads (translates) the label; a **long press** performs
  the control's action. (The v2 spec's "collapse the player to operate a control" still works too.)
- **Loading visual** — the in-flight signer is shown under a translucent darkening + spinner rather
  than a blur: `RenderEffect` does not blur `TextureView` video on some devices, and a bitmap blur
  read as pixelated, so a scrim is used for a consistent result everywhere.
- Networking moved to a coroutine `TranslateService` (poll/cancel via structured concurrency),
  replacing the thread-sleep polling loop. Response parsing reads `tid`/`fdid` leniently.
- The blocking bottom sheet, full-screen loading overlay and branded notice dialog were removed —
  loading, error and blocked states now render inside the corner player.

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
