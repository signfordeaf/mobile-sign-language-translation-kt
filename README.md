# SignForDeaf Mobile Sign Language (Android / Kotlin)

Add on-device sign-language translation to any Android app. Users select (or tap) any
text, and a looping sign-language video is played in a bottom sheet. This is the native
Kotlin counterpart of the `weaccess-ai-signlanguage` React Native SDK, and mirrors its
API, theming and language support.

> **v2.0.0 is a breaking change.** The old `SignForDeafUtil` / `SignForDeafTranslate`
> API has been replaced by a single `SignLanguage` facade. See **Migration** below.

## 🛠️ Install

The SDK is published on **Maven Central** (recommended) and **JitPack**.

### Option A — Maven Central (recommended)

No extra repository needed — `mavenCentral()` is already in every Android project. Just add
the dependency (note the coordinate is `io.github.signfordeaf:signtranslate`):

```gradle
dependencies {
    implementation 'io.github.signfordeaf:signtranslate:2.0.0'
}
```

Artifacts here are **signed and immutable**, so a given version can never change under you.

### Option B — JitPack

[![](https://jitpack.io/v/signfordeaf/mobile-sign-language-translation-kt.svg)](https://jitpack.io/#signfordeaf/mobile-sign-language-translation-kt)

Step 1. Add the JitPack repository to your `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Step 2. Add the dependency (JitPack builds from source, so the coordinate is repo-based):

```gradle
dependencies {
    implementation 'com.github.signfordeaf:mobile-sign-language-translation-kt:2.0.0'
}
```

> The SDK targets **Java 17** and `minSdk 24`. Make sure your app module also compiles
> with `sourceCompatibility`/`targetCompatibility = JavaVersion.VERSION_17` and
> `jvmTarget = "17"`.

### Permission

The SDK declares `INTERNET` and `ACCESS_NETWORK_STATE` in its own manifest, so no extra
permission is required in the host app.

## 🧑🏻‍💻 Usage

Call `SignLanguage.configure(...)` from `onCreate` of an `AppCompatActivity`, after
`setContentView`:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SignLanguage.configure(
            this,
            SignLanguageConfig(
                apiKey = "YOUR-API-KEY",
                apiUrl = "https://your-server.example.com",
                language = Language.TURKISH,
                theme = SignLanguageTheme(primaryColor = "#6750A4")
            )
        )

        // Optional: draggable floating button that toggles tap-to-translate mode.
        SignLanguage.showFloatingButton(this)
    }
}
```

After `configure`, every readable `TextView` / `EditText` in the activity gains a
**"Sign Language"** item in its text-selection menu. Interactive controls (`Button`,
`Switch`/`CompoundButton`, `ImageButton`, and views with their own click listener) are
skipped, so tapping them never triggers a translation.

Selecting text and tapping it (or `SignLanguage.translate(text)`) shows a **full-screen
loading screen** — a translucent dim scrim over the app, with a **thin spinning ring and your
corporate logo centered inside it**, plus a **close (✕)** button. The **bottom sheet opens only
once the video is buffered and ready to play**, so the sheet never shows its own spinner.
Tapping the ✕, the backdrop, or pressing back **cancels** the request (nothing is left running);
a network error or timeout shows an inline error with **Retry / Close**. New views added later
are picked up automatically (a 2-second observer re-scans the view tree).

The translated text shown under the video in the bottom sheet is rendered in the theme
**`primaryColor`** (matching the React Native SDK).

## ⚙️ Configuration

`SignLanguageConfig`:

| Field            | Type                   | Default   | Description                                                   |
| ---------------- | ---------------------- | --------- | ------------------------------------------------------------- |
| `apiKey`         | `String`               | —         | API key (sent as the `rk` query param)                        |
| `apiUrl`         | `String`               | —         | Base URL, e.g. `https://your-server.example.com`              |
| `language`       | `Language`             | `TURKISH` | `TURKISH`, `ENGLISH`, `ARABIC`                                |
| `fdid`           | `String?`              | `"16"`    | Dictionary ID                                                 |
| `tid`            | `String?`              | `"23"`    | Translator ID                                                 |
| `theme`          | `SignLanguageTheme`    | —         | `primaryColor`, `textColor`                                   |
| `floatingButton` | `FloatingButtonConfig` | —         | Floating button appearance / behavior                         |

`SignLanguageTheme(primaryColor, textColor)` — `primaryColor` tints the logo, title, close
button, loading ring **and the translated text** in the bottom sheet.

`FloatingButtonConfig(enabled, idleBehavior, idleDelayMs, sizeDp, backgroundColor,
activeBackgroundColor, iconColor, activeIconColor, borderColor)` controls the floating
button. `idleBehavior` is `PEEK` (default), `FADE`, or `NONE`. The button's active state and
tap-to-translate mode stay **in sync** — toggling one updates the other.

## 🧩 Public API (`SignLanguage`)

```kotlin
SignLanguage.configure(activity, config)      // configure + auto-enable text selection
SignLanguage.enable(activity)                 // re-enable for a new activity
SignLanguage.disable()
SignLanguage.isEnabled()
SignLanguage.translate(text)                  // translate programmatically
SignLanguage.cancelTranslation()
SignLanguage.dismissBottomSheet()
SignLanguage.isBottomSheetVisible()
SignLanguage.setTapToTranslateMode(enabled)   // single tap on text translates it
SignLanguage.isTapToTranslateEnabled()
SignLanguage.showFloatingButton(activity)     // draggable, edge-snapping FAB
SignLanguage.hideFloatingButton()

// Sensitive-data protection
SignLanguage.registerSensitive(text)          // mark a string sensitive (never sent)
SignLanguage.unregisterSensitive(text)
SignLanguage.isRegisteredSensitive(text)      // marked only
SignLanguage.isSensitive(text)                // marked OR auto-detected
SignLanguage.markSensitive(view)              // mark every TextView/EditText in a subtree

// Listeners
SignLanguage.setOnTranslationListener(listener)
SignLanguage.setOnBottomSheetListener(listener)
SignLanguage.setOnTapToTranslateModeChangeListener { enabled -> /* keep a UI switch in sync */ }
```

Listeners:

```kotlin
SignLanguage.setOnTranslationListener(object : SignLanguage.TranslationListener {
    override fun onTextSelected(text: String) {}
    override fun onTranslationStart(text: String) {}
    override fun onTranslationComplete(text: String, videoUrl: String) {}
    override fun onTranslationError(code: String, message: String) {}
    override fun onTranslationBlocked(text: String) {}   // blocked as sensitive; no request sent
})

SignLanguage.setOnBottomSheetListener(object : SignLanguage.BottomSheetListener {
    override fun onBottomSheetOpen() {}
    override fun onBottomSheetClose() {}
    override fun onVideoStart() {}
    override fun onVideoEnd() {}
})
```

## 🔒 Protecting sensitive data

Personal data is **never** sent to the translation server. Before any request, the selected
text is checked; if it contains sensitive data the request is **blocked** and the user sees a
notice instead.

Two layers:

1. **Automatic detection** (no setup needed) — text that contains a **T.C. Kimlik No**
   (checksum-validated), **credit card** (Luhn-validated), **Turkish IBAN**, **e-mail**, or
   **GSM phone number** is blocked before the request leaves the device.
2. **Manual marking** — mark specific content as sensitive:

```kotlin
// Mark an exact string (matched bidirectionally against selections):
SignLanguage.registerSensitive("Gizli Not")
SignLanguage.unregisterSensitive("Gizli Not")

// Or mark every TextView/EditText in a subtree (text stays visible & selectable):
SignLanguage.markSensitive(binding.myPrivateSection)

// Ask directly:
SignLanguage.isSensitive(someText)          // marked OR auto-detected
SignLanguage.isRegisteredSensitive(someText) // marked only
```

The text itself stays visible and selectable; only sending it for translation is blocked.
When that happens, `TranslationListener.onTranslationBlocked(text)` fires and a branded notice
(your logo + a localized message: "Bu içerik hassas veri içerdiği için işaret diline
çevrilemez." / "This content contains sensitive data and cannot be translated.") is shown — and
**no `/Translate` request is made**.

## 🎬 Example app

The `app/` module is a full example that demonstrates every feature. Open the
project in Android Studio and run the **app** configuration (or
`./gradlew :app:installDebug`). In the app you can:

- enter your **API Key** / **API URL** at runtime (saved on the device) and pick a sign language,
- long-press the Turkish sample text to translate via the selection menu,
- toggle **tap-to-translate** and try the draggable floating button (the two stay in sync),
- translate text programmatically,
- see **sensitive data** (national ID, card, e-mail, and a manually-marked line) get blocked,
- watch every SDK event in a live **event log**.

> The example uses a View/XML UI on purpose: text selection relies on real
> `TextView`/`EditText` instances, which Jetpack Compose does not create.

## 🔁 Migration from 1.x

> See [`CHANGELOG.md`](CHANGELOG.md) for the full list of 2.0.0 changes.

```kotlin
// Before (1.0.3)
SignForDeafUtil.initialize("YOUR-API-KEY", requestUrl = "YOUR-REQUEST-URL")
SignForDeafTranslate.makeAllTextSelectable(this)

// After (2.0.0)
SignLanguage.configure(this, SignLanguageConfig(
    apiKey = "YOUR-API-KEY",
    apiUrl = "YOUR-REQUEST-URL"
))
```
