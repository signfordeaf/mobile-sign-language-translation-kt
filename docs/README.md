# SignForDeaf SDK — v2 Behavior Specification

This folder describes **what the SignForDeaf SDK does**, independently of the
framework it is written in. It exists so the iOS (Swift), Android (Kotlin) and
React Native ports can reach v2 parity without reading the Dart source.

The Flutter package in `lib/` is the *reference implementation*: it is where
every rule here was first expressed, and where any ambiguity is resolved. It is
not the contract. Anything in these documents phrased with MUST/SHOULD is
behavior every platform is expected to reproduce; anything marked
**Reference implementation** is a Flutter mechanism described only so you can
recognise what the Dart is doing when you read it.

## Reading order

Read `01`–`03` before writing any code — they decide the shape of the port.
The rest can be read as each area is implemented.

| # | Document | What it settles |
| --- | --- | --- |
| 01 | [Overview](01-overview.md) | What the SDK is, what v2 changed, the principles every later rule follows from |
| 02 | [Architecture](02-architecture.md) | Layers, the translation state machine, player lifecycle, concurrency |
| 03 | [API contract](03-api-contract.md) | Wire format of every backend call |
| 04 | [Configuration](04-configuration.md) | Every setting, its default and its effect |
| 05 | [Design tokens](05-design-tokens.md) | Radii, spacing, sizes, motion, elevation, contrast enforcement |
| 06 | [Player layout](06-player-layout.md) | Player anatomy, the sizing algorithm, collapsed bar, caption |
| 07 | [Floating button](07-floating-button.md) | Placement, persistence, idle behavior, hint budget |
| 08 | [Tap to translate](08-tap-to-translate.md) | How a tap is classified, what passes through, long press |
| 09 | [Sentence segmentation](09-sentence-segmentation.md) | Sentence splitting and length chunking |
| 10 | [Placeholder avatars](10-placeholder-avatars.md) | Signer identity, id resolution, backend override, assets |
| 11 | [Sensitive data](11-sensitive-data.md) | Automatic detection and manual marking |
| 12 | [Events and errors](12-events-and-errors.md) | Event catalogue, payloads, firing order, error codes |
| 13 | [Localization and accessibility](13-localization-and-accessibility.md) | String table, RTL, screen readers, a11y requirements |
| 14 | [Persistence](14-persistence.md) | What is stored, under which key, and what resets |
| 15 | [Conformance](15-conformance.md) | The parity checklist each port works against |

## Conventions

- **MUST / MUST NOT** — required for parity. A port that skips one is not v2.
- **SHOULD** — strongly recommended; deviate only with a platform reason.
- **MAY** — genuinely optional.
- **Reference implementation** — a Flutter-specific mechanism, included for
  orientation only. Ports are free to reach the same behavior any way they like.
- Sizes are in **points** (pt) — logical pixels: `dp` on Android, `pt` on iOS,
  density-independent units in React Native. Never physical pixels.
- Every document ends with a **Source of truth** section naming the Dart files
  it was derived from. When that code changes, that document changes with it.

## Glossary

| Term | Meaning |
| --- | --- |
| **rk** | The integration's API key, sent as the `rk` request parameter |
| **tid** | Translator id — *which signer* performs the translation |
| **fdid** | Dictionary/domain id — *which vocabulary* the signer works from |
| **cid** | Translation id returned by the backend; ties feedback and contact requests back to a specific translation |
| **Segment / sentence** | One unit of translation. A tap normally produces several segments (the sentences of the tapped paragraph) and translates one of them |
| **Stage** | The avatar area of the player — where the video and the idle loop are drawn |
| **Player / card** | The whole floating surface: stage + control bar + caption + window pill |
| **Host app** | The application embedding the SDK |
| **Tap mode** | Tap-to-translate being active, i.e. taps on text start translations |
| **Passthrough** | A tap the SDK deliberately does not claim, handed to the host app instead |
| **Veil** | The blur + spinner laid over the idle avatar while a translation is in flight |
| **TSL / BSL / ASL** | Turkish / British / American Sign Language |

## What is deliberately not specified

- **Widget composition.** How the player is assembled from views is a platform
  decision. Only its geometry, states and behavior are normative.
- **HTTP client, storage engine, video decoder.** Any implementation that meets
  the described behavior is fine.
- **Flutter-only plumbing.** `SignForDeafInit`, `SignForDeafScope`, the overlay
  layering, `SignForDeafText`, the context-menu extension and the widget
  wrappers (`SignForDeaf`, `SignForDeafArea`, `SignForDeafBody`) solve Flutter
  integration problems. Their *purpose* is described in
  [02-architecture.md](02-architecture.md); their shape is not something to
  copy. Each platform exposes whatever entry point is idiomatic for it, as long
  as it can do the four things listed there.

## Source of truth

The whole of `lib/` at package version 2.0.0, plus `test/` for
[15-conformance.md](15-conformance.md).
