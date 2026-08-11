# 08 — Tap to translate

While the player is open, a tap on text in the host app translates it. This is
the SDK's central interaction and its most invasive one: it sits between the
user and every tap they make. The rules below are what keep the app usable.

## The core constraint

**The decision must be made while the tap is being routed, not after.**

Once the SDK's gesture recogniser has claimed a pointer, it cannot hand it back
to the app. So the SDK MUST classify the touch position *during hit testing* and
decline to be in the hit path at all when the tap does not belong to it. A
catcher that accepts every tap and then "ignores" the ones it does not want is
the v1 design, and it is why users had to keep toggling the mode off to press
anything.

| Approach | Result |
| --- | --- |
| Decline during hit testing (**required**) | The app's own recogniser owns the pointer from the first event; the control behaves exactly as it would without the SDK |
| Claim, then ignore | The gesture is already lost; the control never fires |

Ports MUST find their platform's equivalent of "decline the hit test": on iOS
`hitTest(_:with:)` returning `nil`, on Android an overlay returning `false` from
`onInterceptTouchEvent`, in React Native a view whose pointer-events are
computed per touch.

Two further rules hold everywhere:

- **Scrolling always passes through.** The SDK claims taps only, never drags.
- **Collapsing the player hands every tap back.** That is what collapsing means
  ([02](02-architecture.md)).

## Classification

Probe the host app's view hierarchy at the touch point, walking **deepest
first**, and produce one of three outcomes:

| Outcome | Meaning | Behavior |
| --- | --- | --- |
| `text` | Plain text, **including the label of a control** | The SDK claims the tap and translates |
| `interactive` | A control with no text of its own, or an editable field | Handed to the app |
| `none` | Empty space, an image, a decoration | Handed to the app, which may ignore it |

### Why a control's label is translatable

A Deaf user could otherwise translate a contract but not the button that agrees
to it — the one word that matters most. So:

- a control **with** a text label → the label is translated;
- a control **without** one (icon button, switch, slider, the box of a
  checkbox) → the tap goes to the app.

The same `CheckboxListTile` therefore reads when tapped on its label and ticks
when tapped on its box. To operate a labelled control, collapse the player.

### Walk rules

Applied in order while walking from the innermost target outward:

1. **Stop at the innermost scrollable.** A scroll view's own pointer listeners
   are *ancestors* of its content, so without this guard every tap inside any
   list would read as interactive and nothing would ever be translatable.
2. **Editable field** → `interactive`, so it keeps its focus and caret. A
   **read-only** selectable field carries no editing affordance and stays
   translatable.
3. **Pointer listener / gesture handler:**
   - skip it if it is **ambient** (see below);
   - if text has already been found deeper, that text wins → `text`;
   - otherwise → `interactive`.
4. **Text node** → remember it as the candidate, unless it fails the
   translatable-content test below.
5. Nothing found → `none`.

### Ambient listeners

A pointer listener covering **≥ 80%** of the probed area is treated as ambient
and skipped.

Host apps commonly wrap a whole page in a tap handler that just dismisses the
keyboard. Without this guard that single widget marks every tap on the page as
interactive and translation never triggers.

### Icons are not text

Icon fonts draw a glyph from a **private use area** codepoint, and UI frameworks
routinely render icons as text nodes. Without a filter, tapping an icon sends a
meaningless glyph to the translation API — and, since a control's label makes it
translatable, would also stop icon buttons from ever being pressed.

A text candidate is accepted only if it contains at least one character that is
neither whitespace (space, tab, CR, LF) nor in a private use area:

| Range | |
| --- | --- |
| `U+E000` – `U+F8FF` | BMP private use area |
| `U+F0000` – `U+FFFFD` | Supplementary PUA-A |
| `U+100000` – `U+10FFFD` | Supplementary PUA-B |

**This MUST be an exclusion test, not an "is this a letter" test.** Arabic is a
supported language and its script is uncased, so any heuristic built on letter
case quietly rejects it.

### Text extraction

When extracting the string from a text node, ports MUST:

- exclude accessibility/semantics labels — they would substitute different text
  from what is on screen;
- **include placeholders** for inline embedded content — dropping them shifts
  every character index after the placeholder, which silently misaligns the
  tap-position-to-character mapping used to pick the sentence.

## From tap to segments

Once a tap is classified as `text`:

1. Take the node's full text and normalize it (collapse whitespace runs, trim).
   Empty → do nothing.
2. **Paragraph granularity:** report the whole text as one segment — unless it
   exceeds `maxSegmentChars`, in which case report the fewest clause-bounded
   chunks that fit ([09](09-sentence-segmentation.md)). A 3000-character
   paragraph in one request breaks at the gateway; chunking is the only way it
   reaches the user at all.
3. **Sentence granularity (default):**
   - split the text into sentences;
   - map the touch position to a character index, and that index to a sentence;
   - report **all** sentences plus the index of the tapped one, so the host can
     step through the paragraph without re-reading the screen.
4. **Fallbacks.** If the touch position cannot be mapped to a character, or the
   split yields nothing usable, report the whole paragraph as a single segment.
   The worst case is always the v1 behavior, never a failure.
5. Segments that normalize to nothing are dropped, and the reported index is
   adjusted so it still points at the tapped sentence.

The reported list is never empty and the index always points inside it.

## Legacy capture mode

`smartPassthrough: false` restores v1: the catcher claims every tap and returns
the deepest text under the point regardless of interactivity. It exists as an
escape hatch for host apps whose custom gesture handling confuses the probe.

## Long press

`longPressToTranslate` (off by default) reaches the one thing passthrough
cannot: **text the host app made tappable**, where a tap always belongs to the
app and so can never be translated.

| Rule | Value / behavior |
| --- | --- |
| Deadline | **600 ms**, deliberately longer than the framework default of 500 ms |
| Availability | Only while the SDK is enabled, the player is open **and** expanded |
| Over editable/selectable text | Does not participate at all |
| Interactivity check | Skipped — tappable text is exactly what this is for |
| Passthrough | Not applied on this path |
| Gesture handling | Joins the normal gesture competition; it does not block the gesture |

The longer deadline matters: this layer sits above the host app and would win a
tie, so any long press the host built must fire first and take the gesture.

Over editable text the SDK MUST stay out of the competition entirely rather than
joining and declining later — winning and then withdrawing cancels the user's
selection, and the selection toolbar is what long press means there.

The availability rule is not just about visibility: collapsed, a long press
would start a translation into a stage nobody can see, and closed it would
contradict the button being in its off position. Either way the probe would then
run on every touch-down in the host app, for nothing.

## Other ways text reaches the SDK

| Path | Notes |
| --- | --- |
| **Selection menu** | An extra "Sign Language" item on already-selectable text, shown only while the SDK is enabled and the selection is non-empty. Selecting it hides the toolbar and translates the selection |
| **Opt-in text component** | A wrapper the host can use where global hit testing is unreliable (custom painting, transforms): while tap mode is on, tapping it translates its own text directly |
| **Programmatic** | The host calls the controller directly. No gesture involved, so the player opens in the configured corner ([06](06-player-layout.md)) |

The SDK MUST NOT force text to be selectable. v1 wrapped the app in a selection
layer, which broke normal interaction; selection-based translation is offered
only where the host already made text selectable.

## Source of truth

- `lib/src/tap_to_translate.dart`
- `lib/src/service/tap_target_probe.dart`
- `lib/src/signfordeaf_menu.dart`, `lib/src/signfordeaf_text.dart`
- `test/tap_passthrough_test.dart`, `test/long_press_translate_test.dart`
