# 13 — Localization and accessibility

An accessibility SDK that ships a hardcoded label is a contradiction. Every
user-visible string, including every screen-reader label, is localized.

## Language selection

The strings follow the configured `language`, in this precedence:

1. The configured language, when the integration set one (via the config object
   or the language parameter);
2. otherwise the host's active locale, mapped by ISO code (`tr`, `en`, `ar`);
3. otherwise Turkish.

If a language somehow has no string table, fall back to **English**.

Note that the SDK may be mounted above the host's localization scope, so it MUST
NOT depend on the host providing one.

## String table

| Key | Turkish | English | Arabic |
| --- | --- | --- | --- |
| `menuTitle` | İşaret Dili | Sign Language | لغة الإشارة |
| `businessName` | Engelsiz Çeviri | SignForDeaf | SignForDeaf |
| `loading` | Çeviriliyor... | Translating... | جارٍ الترجمة... |
| `error` | Çeviri işlemi şu anda gerçekleştirilemiyor. Lütfen daha sonra tekrar deneyiniz. | Translation is not available at the moment. Please try again later. | لا يمكن إجراء عملية الترجمة في الوقت الحالي. يرجى المحاولة مرة أخرى في وقت لاحق. |
| `close` | Kapat | Close | إغلاق |
| `videoPlayerLabel` | İşaret dili videosu oynatılıyor | Sign language video is playing | يتم تشغيل فيديو لغة الإشارة |
| `translationReady` | İşaret dili çevirisi hazır | Sign language translation is ready | ترجمة لغة الإشارة جاهزة |
| `tapToTranslateHint` | Cümlelere tıklayarak işaret dili çevirilerini başlatabilirsiniz. | Tap a sentence to start its sign language translation. | انقر على جملة لبدء ترجمتها إلى لغة الإشارة. |
| `sensitiveBlocked` | Bu içerik hassas veri içerdiği için işaret diline çevrilemez. | This content contains sensitive data and cannot be translated. | يحتوي هذا المحتوى على بيانات حساسة ولا يمكن ترجمته. |
| `translationModeLabel` | İşaret dili çeviri modu | Sign language translation mode | وضع الترجمة بلغة الإشارة |
| `playLabel` | Oynat | Play | تشغيل |
| `pauseLabel` | Duraklat | Pause | إيقاف مؤقت |
| `loopLabel` | Tekrarla | Repeat | تكرار |
| `speedLabel` | Oynatma hızı | Playback speed | سرعة التشغيل |
| `contactLabel` | İletişime geçin | Contact us | تواصل معنا |
| `collapseLabel` | Küçült | Collapse | تصغير |
| `expandLabel` | Genişlet | Expand | توسيع |
| `previousSentenceLabel` | Önceki cümle | Previous sentence | الجملة السابقة |
| `nextSentenceLabel` | Sonraki cümle | Next sentence | الجملة التالية |
| `feedbackPositiveLabel` | Çeviri anlaşılır | Translation is clear | الترجمة واضحة |
| `feedbackNegativeLabel` | Çeviri anlaşılır değil | Translation is unclear | الترجمة غير واضحة |
| `feedbackThanks` | Geri bildiriminiz için teşekkürler | Thanks for your feedback | شكرًا على ملاحظاتك |

Plus one formatted string: the sentence counter, `"{index + 1} / {total}"`.

Notes:

- `loading`, `previousSentenceLabel`, `nextSentenceLabel` and
  `sentenceCounterLabel` are currently **unused by the UI** — the player shows no
  "translating" label ([01](01-overview.md), principle 3) and no on-screen
  sentence navigation ([06](06-player-layout.md)). They are kept for hosts
  building their own controls. Ports SHOULD keep them for the same reason.
- German, French and Spanish tables are intentionally absent: the backend does
  not support those languages ([03](03-api-contract.md)). Add them together, not
  separately.

## Text direction

Arabic renders right-to-left. Requirements:

- The SDK MUST provide its own text direction when the host does not — it can be
  mounted above the host's directionality scope.
- Screen-reader announcements MUST carry the matching direction.
- The player's own geometry does **not** mirror: it is positioned by drag and by
  the floating button's docked edge ([06](06-player-layout.md),
  [07](07-floating-button.md)), both of which are user-driven and direction-
  independent.

## Screen readers

| Element | Requirement |
| --- | --- |
| Floating button | Exposed as a button, labelled `translationModeLabel`, reporting its selected state so the reader can say whether the mode is on |
| Player surface | Exposed as one container, carrying the configured hint (`bottomSheetHint`) |
| Video | Labelled `videoPlayerLabel`, or the integration's override |
| Every control | Labelled — play/pause switches between `playLabel` and `pauseLabel`; loop, speed, contact, collapse/expand and close each carry their own |
| Logo / mark | Excluded from accessibility — it is decoration, and announcing it on every focus traversal is noise |

### Announcements

| Trigger | Default | String |
| --- | --- | --- |
| A translation becomes playable | **on** | `translationReady` |
| The player stops being playable | off | `close` |

Both are configurable ([04](04-configuration.md)). The announcement MUST use the
current text direction and MUST NOT run during initialisation — only on an
actual transition.

## Accessibility requirements restated

These come from other documents but belong on one checklist:

- **44 pt minimum tap target** for every control, in every state, including the
  collapsed bar ([05](05-design-tokens.md)). Shrinking a bar never comes out of
  a control's hit area.
- **WCAG 4.5:1 contrast** enforced for foregrounds over configured backgrounds,
  substituting black or white when the configured color fails
  ([05](05-design-tokens.md)).
- **Text scaling honoured**: the caption's height derives from the *scaled* font
  size, so raising the system text size grows the block rather than clipping the
  words in it ([06](06-player-layout.md)).
- **Control labels are translatable** — a Deaf user must be able to read the
  button before pressing it ([08](08-tap-to-translate.md)).
- **The host app stays operable** throughout ([01](01-overview.md)).

## Source of truth

- `lib/src/l10n/strings.dart`
- `lib/src/signfordeaf_host.dart` (language resolution, announcements, direction)
- `lib/src/signfordeaf_init.dart` (direction fallback above the app)
- `lib/src/views/sign_control_bar.dart`, `lib/src/views/action_pills.dart`
