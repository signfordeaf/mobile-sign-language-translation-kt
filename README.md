# SignForDeaf Mobile Sign Language (Android / Kotlin)

Add on-device sign-language translation to any Android app. While the SDK is on, a tap on
text translates the **sentence** under the finger and plays it in a small **non-modal corner
player** — the host app stays readable and tappable throughout (no scrim). This is the native
Kotlin counterpart of the `weaccess-ai-signlanguage` React Native SDK, and mirrors its
API, theming and language support.

> **v2 behavior (2.1.0).** The translation experience is now a non-modal corner player with an
> idle signer loop, smart tap-passthrough and per-sentence translation, matching the SignForDeaf
> v2 spec in [`docs/`](docs/). The public `SignLanguage` API is unchanged from 2.0.0.

## 🛠️ Install

The SDK is published on **Maven Central** (recommended) and **JitPack**.

### Option A — Maven Central (recommended)

No extra repository needed — `mavenCentral()` is already in every Android project. Just add
the dependency (note the coordinate is `io.github.signfordeaf:signtranslate`):

```gradle
dependencies {
    implementation 'io.github.signfordeaf:signtranslate:2.1.0'
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

Step 2. Add the dependency (JitPack builds from source, so the version is the git tag):

```gradle
dependencies {
    implementation 'com.github.signfordeaf:mobile-sign-language-translation-kt:v2.1.0'
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

After `configure`, call `enable(this)` (or set `autoEnable = true`) to turn the SDK on. A
draggable **floating button** appears; tapping it opens the **corner player** and turns tap mode
on. The host app is never blocked — it stays readable, scrollable and operable the whole time.

**How text reaches the SDK (smart passthrough).** With the player open, the SDK classifies each
touch during hit-testing, so nothing has to be marked up per-widget — *every* readable text in
the host app becomes translatable automatically, including the labels on buttons:

- **Tap plain text** → translates the **sentence** under the finger.
- **Tap a labelled control** (e.g. a Button) → translates its **label** (a Deaf user must be able
  to read the button before pressing it).
- **Long-press a labelled control** → the SDK steps aside and the control performs its **own
  action** (click). So a tap reads it, a long press operates it.
- **Icon/unlabelled controls, editable fields and scrolling** pass straight through to the app.

(`smartPassthrough = false` restores the v1 "capture everything" behavior; `longPressToTranslate`
opts into translating text the host itself made tappable. Collapsing the player also hands every
tap back to the app.)

**The player.** Before/while a translation renders, the stage shows a looping **idle signer**
(drop the four clips into `res/raw` — see
[`PLACEHOLDER_CLIPS.md`](signtranslate/PLACEHOLDER_CLIPS.md); until then it falls back to a
spinner/mark). While loading, the signer sits under a translucent darkening with a spinner. When
the video is ready it plays with **play/pause, speed and loop** controls **below** the stage
(never over the signer's hands) and the sentence as a **caption** (auto-scrolls only when longer
than two lines). **Collapse** folds the player to a small bar and pauses; **✕** closes it and
brings the floating button back. Error and blocked (sensitive) states render **inside** the
player. `SignLanguage.translate(text)` opens the player programmatically.

## ⚙️ Configuration

`SignLanguageConfig`:

| Field            | Type                   | Default   | Description                                                   |
| ---------------- | ---------------------- | --------- | ------------------------------------------------------------- |
| `apiKey`         | `String`               | —         | API key (sent as the `rk` query param)                        |
| `apiUrl`         | `String`               | —         | Base URL, e.g. `https://your-server.example.com`              |
| `language`       | `Language`             | `TURKISH` | `TURKISH`, `ENGLISH`, `ARABIC`                                |
| `fdid`           | `String?`              | `null`    | Dictionary ID — **optional**; unset ⇒ auto-selects the default translator (Hesna) |
| `tid`            | `String?`              | `null`    | Translator ID — optional; set only to *pin* a translator      |
| `theme`          | `SignLanguageTheme`    | —         | `primaryColor`, `textColor`, `onPrimaryColor`, `surfaceColor`, `cornerRadius` |
| `floatingButton` | `FloatingButtonConfig` | —         | Floating button appearance / behavior                         |
| `card`           | `SignLanguageCardConfig` | —       | Player size, controls, idle avatar, speeds                    |
| `granularity`    | `Granularity`          | `SENTENCE` | Translate the tapped sentence, or the whole `PARAGRAPH`      |
| `smartPassthrough` | `Boolean`            | `true`    | Classify taps so the host app stays usable (`false` = v1 capture) |
| `longPressToTranslate` | `Boolean`        | `false`   | Long-press reaches text the host made tappable                |
| `autoEnable`     | `Boolean`              | `false`   | Turn the SDK on at start                                      |

`SignLanguageTheme(primaryColor, textColor)` — `primaryColor` tints the logo, control bar, the
window pill, the loading spinner **and the caption** in the player.

`FloatingButtonConfig(enabled, idleBehavior, idleDelayMs, sizeDp, backgroundColor,
activeBackgroundColor, iconColor, activeIconColor, borderColor)` controls the floating
button. `idleBehavior` is `PEEK` (default), `FADE`, or `NONE`. The button's active state and
tap-to-translate mode stay **in sync** — toggling one updates the other.

### Translator (signer) selection

`tid`/`fdid` are **optional**. Three layers decide who signs, weakest to strongest:

1. **Auto** — leave both unset and the SDK uses a default translator (**Hesna**) for the idle
   placeholder and the first request.
2. **Pinned** — set `tid`/`fdid` (or `card.placeholderAvatar`) to force a specific signer.
3. **Backend override (strongest)** — if the backend serves a translation under a *different*
   `tid`/`fdid` than requested (e.g. an account pinned to another translator), the SDK **adopts**
   it: the idle signer switches to that person and every later request uses the corrected pair for
   the rest of the session.

So an integration that sets nothing shows Hesna first, then automatically switches to whatever
translator the backend actually returns.

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

- enter your **API Key** / **API URL** at runtime (saved on the device) — or inject them at build
  time (see below) — and pick a sign language,
- open the player with the **floating button**, then **tap** any sample text — or a **button** — to
  translate it, and **long-press** a button to operate it instead,
- translate text programmatically,
- see **sensitive data** (national ID, card, e-mail, and a manually-marked line) get blocked,
- watch every SDK event in a live **event log**.

> The example uses a View/XML UI on purpose: text selection relies on real
> `TextView`/`EditText` instances, which Jetpack Compose does not create.

### Injecting credentials at build time (the `--dart-define` equivalent)

Instead of typing the API Key / URL into the on-screen fields every run, you can inject them at
build time — the same idea as the Flutter example's `--dart-define` launch args. The example
reads them into `BuildConfig` and configures itself automatically on launch (falling back to the
on-screen fields when they are unset).

The recommended place is **`local.properties`** (it is gitignored, so real credentials never get
committed):

```properties
SIGNFORDEAF_API_KEY=YOUR-API-KEY
SIGNFORDEAF_API_URL=https://kor01rp02.signfordeaf.com
# Optional:
SIGNFORDEAF_ORIGIN_URL=https://webplugin.signfordeaf.com
#SIGNFORDEAF_FDID=16
#SIGNFORDEAF_TID=23
```

The same names also work as `-P` project properties or environment variables, e.g.:

```bash
./gradlew :app:installDebug -PSIGNFORDEAF_API_KEY=... -PSIGNFORDEAF_API_URL=...
```

`app/build.gradle.kts` resolves each value in the order **project property → `local.properties` →
environment variable** and bakes it into `BuildConfig.SIGNFORDEAF_*`.

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
