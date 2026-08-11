# 15 — Conformance

The parity checklist. Each item is an observable behavior, derived from the 179
tests the Flutter package holds; how you test it is up to the platform.

Work through it per area as you implement. An item that cannot be reproduced is
either a genuine platform constraint — write it down — or a gap.

## How to use this

- **Every item is testable without reading Dart.** If one is not clear enough to
  turn into a test, the corresponding document is the bug.
- Items marked **★** are regressions from real defects. They are the ones most
  likely to be re-introduced by a port that "simplifies" something.

---

## A. Lifecycle

- [ ] Enabled with the player closed shows the floating button and nothing else.
- [ ] Tapping the button opens the player, turns tap mode on, and hides the
      button.
- [ ] Collapsing turns tap mode off and pauses playback; expanding restores
      both, resuming only if it was playing.
- [ ] Closing turns everything off and brings the button back.
- [ ] Disabling the SDK closes the player too.
- [ ] An open player shows the idle signer **before** anything is translated.
- [ ] A purely programmatic translation shows the player even though the host
      never opened it.
- [ ] ★ The button returns to where the user left it after the player has been
      opened and closed.
- [ ] ★ The player opens on the side the button is docked to.
- [ ] ★ Toggling tap mode does not reset the host app's navigation stack.

## B. Tap classification

- [ ] A tapped paragraph is captured; a read-only selectable field is captured
      too.
- [ ] Nothing is captured while the SDK is disabled.
- [ ] A labelled button is **read**, not pressed.
- [ ] An unlabelled control (switch, icon button, checkbox box) still reaches
      the app and still operates.
- [ ] ★ A bare icon is not mistaken for text.
- [ ] ★ An icon next to a label does not shadow the label.
- [ ] One row, two outcomes: a checkbox row reads when tapped on its label and
      ticks when tapped on its box.
- [ ] An editable field keeps its focus and caret behavior.
- [ ] ★ Plain text inside a scroll view is still translatable, and a labelled
      button inside one is still read.
- [ ] Scrolling passes through while the mode is on.
- [ ] ★ An app-wide "dismiss the keyboard" wrapper does not make the whole page
      interactive.
- [ ] Legacy capture mode (`smartPassthrough: false`) claims every tap,
      including buttons.

## C. Long press

- [ ] Reaches text the host made tappable — the case a tap can never reach.
- [ ] Yields to a long press the host built.
- [ ] Stays out of selectable and editable text, leaving the selection toolbar
      untouched.
- [ ] Does nothing when the feature is off, and nothing for a short tap.
- [ ] Scrolling is untouched.
- [ ] Works only while the player is open **and** expanded — not collapsed, not
      closed.
- [ ] Coexists with tap mode.

## D. Segmentation

- [ ] ★ The ranges partition the input exactly: contiguous, ordered, covering
      the whole string; concatenation reproduces the input character for
      character.
- [ ] Empty input yields no ranges; blank input yields one.
- [ ] No split inside `T.C.` / `A.Ş.`, grouped numbers, a domain name, or an
      e-mail address.
- [ ] A clause number stays with the sentence it introduces.
- [ ] No split after a known abbreviation, or when the next word is lowercase.
- [ ] `!` and `?` split; a hard line break splits.
- [ ] A fragment too short to stand alone is merged into its neighbour.
- [ ] A single sentence yields one range, so the caller can fall back to the
      paragraph.
- [ ] A character offset maps to its sentence; an offset past the end clamps to
      the last one; a negative offset is "not found".
- [ ] Text under the limit is never chunked; real contract clauses (190–250
      chars) never trigger it.
- [ ] Over-limit text chunks so every chunk is under the limit, losslessly, and
      balanced — no one-word remainder at the end.
- [ ] A conjunction cut puts the conjunction at the **start** of the next chunk;
      a comma is preferred over a conjunction.
- [ ] Pathological input with no whitespace is hard-cut rather than sent whole.
- [ ] Length-only splitting ignores sentence boundaries but respects the limit.
- [ ] Inline-content placeholders are stripped and whitespace runs — including
      the soft line breaks of a wrapped paragraph — collapse to one space.

## E. Translation flow

- [ ] Sensitive text blocks the translation and **no request is made**.
- [ ] Empty text does nothing.
- [ ] The tapped sentence is the selected one, and the segment bounds are right.
- [ ] ★ Sensitive data blocks only the sentence containing it.
- [ ] The same sentence twice does not hit the API twice.
- [ ] The next sentence is prefetched as soon as the current one resolves.
- [ ] No prefetch on the last sentence, for sensitive text, or for something
      already cached.
- [ ] Dismissing the player clears the segments.
- [ ] Closing cancels an in-flight request.

## F. Signer identity

- [ ] Every bundled signer declares a unique `tid`/`fdid` pair and a real asset.
- [ ] A matched pair resolves to its signer; either id alone still resolves.
- [ ] `tid` wins when the two ids disagree.
- [ ] Unknown or absent ids resolve to the stand-in, never to a bare spinner.
- [ ] An explicitly pinned signer overrides the ids.
- [ ] The SDK defaults resolve to the signer behind them (Kadir).
- [ ] Served ids from a response replace the requested pair and move the signer;
      an absent, empty or unchanged id changes nothing.
- [ ] The response parses ids whether the API quotes them or not.
- [ ] A translation adopts the ids it came back under; a response that states
      none leaves the configured pair alone.

## G. Player layout

- [ ] The player renders while a translation is still loading.
- [ ] ★ It is non-modal: the app underneath still receives taps.
- [ ] It covers only a corner, never the whole screen.
- [ ] ★ Height ≤ 42% and width < 65% of the screen on 393×852, 375×667 **and**
      360×640.
- [ ] Every control keeps the 44 pt minimum tap target.
- [ ] The control bar is exactly as wide as the avatar.
- [ ] Collapsed it folds into a single bar showing the mark, expand and close —
      nothing else; expanding restores the controls and the preferences;
      closing from the collapsed bar dismisses the player.
- [ ] The window pill hangs above the avatar without being clipped.
- [ ] ★ No sentence navigation is rendered anywhere.
- [ ] No layout overflows at raised system text scales, on every phone size.

## H. Idle avatar

- [ ] The loop is blurred with a spinner over it while loading, and the spinner
      stays small enough to leave the avatar visible.
- [ ] It falls back to the spinner when the clip cannot be played, and the card
      still renders.
- [ ] ★ No "translating" label is ever shown.
- [ ] The loop stands in only while loading — not for a blocked sentence.
- [ ] ★ A failure message is centred in the stage, not pinned to its top, and
      does not repeat the mark.
- [ ] The mark is always visible over the avatar.

## I. Caption

- [ ] Shares one surface with the controls — not a second card.
- [ ] Present while the translation is still loading.
- [ ] Caps at two lines and scrolls beyond; reaches the very end of the
      sentence.
- [ ] A sentence that fits never moves.
- [ ] A new sentence starts from its beginning.
- [ ] ★ Grows with the system text scale instead of clipping.
- [ ] Absent once the player is closed, hidden while collapsed.

## J. Playback controls

- [ ] The speed button cycles through the configured speeds.
- [ ] Play is disabled until the video is ready; **speed and loop are not**.
- [ ] Speed and loop persist across sessions and are restored on the next
      launch.
- [ ] ★ A restored preference does not overwrite one the user changed during
      this session.
- [ ] The collapse state is tracked and emits its event.

## K. Contrast and theme

- [ ] Contrast ratio: black-on-white is the maximum, a color against itself the
      minimum, and the function is symmetric.
- [ ] A foreground that already passes is kept; one that fails is replaced with
      whichever of black or white reads better.
- [ ] The theme's `onPrimary` and `onSurface` apply this to the bar and to the
      failure messages.
- [ ] The default theme is legible out of the box.

## L. Integration surface

- [ ] The selection menu shows the "Sign Language" item while the SDK is
      enabled, and does not while it is disabled.
- [ ] A single config object sets every field; `apiUrl` becomes the origin
      unless `originUrl` is given.
- [ ] The SDK is disabled by default; enable/disable work, and disabling also
      turns tap mode off.
- [ ] The hint budget is consumed and enforced.
- [ ] Language codes map `tr=1`, `en=2`, `ar=6`, and only those three exist.
- [ ] ★ SDK chrome does not inherit host-app text styling defaults (the Flutter
      symptom was the yellow error underline).

## M. Floating button

- [ ] A tap fires the action.
- [ ] ★ One tap is enough even when the button has slipped into its idle state.
- [ ] A drag does not fire the action.

---

## Suggested order of work

1. **E + D** (translation flow and segmentation) — pure logic, no UI, the
   easiest to port and the most exactly specified.
2. **F + K** — also pure logic.
3. **B + C** — the platform-specific part, and the one with the most ways to be
   subtly wrong.
4. **G + I + H** — the player.
5. **A + M + L** — the wiring around them.

## Source of truth

`test/` — 179 tests across `sign_card_test.dart`, `tap_passthrough_test.dart`,
`sentence_splitter_test.dart`, `signfordeaf_controller_test.dart`,
`card_lifecycle_test.dart`, `placeholder_avatar_test.dart`,
`long_press_translate_test.dart`, `color_contrast_test.dart`,
`sensitive_data_guard_test.dart`, `signfordeaf_widgets_test.dart`,
`signfordeaf_init_test.dart`.
