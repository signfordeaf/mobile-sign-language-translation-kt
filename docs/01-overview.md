# 01 — Overview

## What the SDK does

The SDK turns any host app into one a Deaf user can read. It sits above the
app, and while it is active a tap on text sends that text to the SignForDeaf
backend, which renders a sign language video of it. The video plays in a small
floating player over the app.

The complete loop:

1. The host app embeds the SDK once, at its root, and supplies credentials.
2. The SDK is **off by default**. The host turns it on — from a settings switch,
   an accessibility profile, or `autoEnable`.
3. Once on, a **floating button** appears. Tapping it opens the **player**.
4. With the player open, a tap on text in the host app translates the sentence
   under the finger. Taps the SDK has no business claiming go to the app.
5. The request goes out; the player shows an idle **signer loop** behind a blur
   and a spinner while the backend renders the video.
6. The video arrives and plays in the player, with play/pause, loop, speed and
   the sentence as a caption underneath.
7. Collapsing the player hands the app fully back. Closing it returns to the
   floating button.

Text can also reach the SDK three other ways: the selection menu ("Sign
Language" item on already-selectable text), a long press (opt-in), and a direct
programmatic call from the host.

## The v1 → v2 shift

v1 blocked the app. A translation opened a half-screen bottom sheet behind a
full-screen scrim, so the page underneath was unreadable and untappable until
the user dismissed it. Tap mode laid a catcher over the whole screen and
swallowed every tap, so the user toggled the mode off to press anything and on
again to read anything.

v2 is built on the opposite premise: **sign language and the host app are used
at the same time.** No scrim, a corner player instead of a sheet, taps
classified as they happen, and one sentence translated instead of a whole
paragraph.

A port coming from a v1 codebase is changing behavior, not API: the v1 entry
points and their parameters survive v2 unchanged, and every new setting has a
default that produces v2 behavior.

## Principles

Every rule in the following documents traces back to one of these. When a
detail seems arbitrary, it is usually one of them applied to a specific case —
and when a port has to make a judgement call these documents do not cover, this
is the ranking to decide by.

### 1. Never take the app away from the user

The host app stays readable, scrollable and operable throughout a translation.
This is why there is no scrim, why the player is a corner surface with a
budgeted maximum size, why taps on controls are handed back to the app, and why
collapsing the player is a first-class action rather than a minimise animation.

A Deaf user must be able to read a contract *and* press the button that agrees
to it.

### 2. Never cover the signer's hands

In sign language the hands carry the meaning. The control bar sits **below** the
video rather than over it — deliberately differing from the web SDK — the
window pill hangs mostly outside the stage, and the caption lives in its own
block. Anything that would occlude the lower third of the signer is rejected,
however convenient.

### 3. Never show something the user can mistake for the answer

The idle signer loop is blurred and carries a spinner while a translation is in
flight, because an unblurred loop is indistinguishable from a finished
translation. The loop plays clean only when nothing is loading, so it never
promises a video that is not coming. Error and blocked states render inside the
player instead of behind a scrim, so the user always knows which of the four
states — idle, loading, ready, refused — they are in.

### 4. Accessibility rules are not styling choices

Contrast below WCAG 4.5:1 is corrected rather than honoured. Every control has a
44 pt tap target. Every string is localized, including screen-reader labels.
In an SDK built for accessibility these are defects when they are wrong, not
preferences.

### 5. Protect the user from their own screen

Text containing an identity number, an IBAN, a card number, a phone number or an
email is never sent to the translation backend, whether it was detected
automatically or marked by the host. Blocking is per sentence, so one sensitive
clause does not make the rest of a page untranslatable.

### 6. Degrade, never break

A missing asset, an unplayable clip, an unsupported platform, an unknown id, a
failed prefetch, a rejected feedback call — none of these may break the player.
Each has a defined fallback, and the user-visible result of every failure is at
worst a plainer player, never a broken one.

## Source of truth

- `CHANGELOG.md` (2.0.0 section) — the v1 → v2 record
- `README.md` — the integrator-facing description
- `lib/src/signfordeaf_host.dart` — how the layers are assembled
