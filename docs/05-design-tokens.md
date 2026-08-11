# 05 — Design tokens

Every value the SDK's own surfaces are drawn from. Brand colors are **not**
tokens — they come from the integration's theme ([04](04-configuration.md)) so
each app keeps its palette. Everything else is fixed, so the player, the control
bar, the pills and the hint bubble stay visually one system across platforms.

All sizes are in points (dp / pt / density-independent units).

## Radii

Deliberately small. Large radii on a surface this size read as a chunky block
rather than a compact player.

| Token | Value | Used by |
| --- | --- | --- |
| `radiusLarge` | `16` | Stage and control bar outer radius (overridable via theme `cornerRadius`) |
| `radiusMedium` | `12` | Action pills — feedback, collapse/close |
| `radiusSmall` | `8` | Speed button, hint bubble, logo badge |

## Spacing

| Token | Value |
| --- | --- |
| `spaceXs` | `4` |
| `spaceSm` | `8` |
| `spaceMd` | `12` |
| `spaceLg` | `16` |
| `spaceXl` | `24` |

## Sizes

| Token | Value | Meaning |
| --- | --- | --- |
| `controlSize` | `44` | Tap target of **every** control and pill button |
| `iconSize` | `20` | Icon inside a control |
| `primaryIconSize` | `24` | Play/pause glyph, and the mark in the collapsed bar |
| `pillOverflowFraction` | `0.8` | How much of the window pill hangs above the stage |
| `minStageWidth` | `3 × 44 + 8 = 140` | Narrowest the stage may get |
| `maxPlayerScreenFraction` | `0.42` | Share of screen height the **whole player** may occupy |

`controlSize` is one value for every control on purpose: hierarchy comes from
the icon inside, not from differently sized boxes. It is also the platform
accessibility minimum, which is not negotiable in an SDK built for
accessibility. Ports MUST NOT shrink individual controls to fit a layout — widen
the container instead ([06](06-player-layout.md)).

## Loading

| Token | Value | Meaning |
| --- | --- | --- |
| `loadingBlurSigma` | `2.5` | Gaussian blur over the idle avatar while a translation is in flight |
| `loadingIndicatorSize` | `24` | Spinner diameter over the blurred avatar |

The blur is tuned to read as "not the final video" while still showing motion.
See [10](10-placeholder-avatars.md) for when it is applied.

## Caption

| Token | Value |
| --- | --- |
| `captionFontSize` | `12` |
| `captionLineHeight` | `1.35` (multiplier) |
| `captionMaxLines` | `2` |

## Motion

| Token | Value | Applies to |
| --- | --- | --- |
| `cardTransition` | `320 ms` | Player enter/exit |
| `collapseTransition` | `260 ms` | Collapse/expand |
| `snapTransition` | `300 ms` | Drag-release settle of player and floating button |
| `emphasized` | ease-out-cubic | Entrances |
| `settle` | ease-out-back | Snap-to-edge, with its slight overshoot |

## Elevation

| Token | Color | Offset | Blur |
| --- | --- | --- | --- |
| `floatingShadow` | `#000000` at 12% (`0x1F`) | `(0, 2)` | `12` |
| `pillShadow` | `#000000` at 15% (`0x26`) | `(0, 2)` | `6` |

`floatingShadow` sits under the stage and the control bar; `pillShadow` under
anything laid on top of them, and under the floating button.

## Neutral overlays

These are intentionally *not* themeable — they sit over unpredictable content
(video frames, the host app) where a brand color cannot be trusted to stay
legible.

| Token | Value | Used by |
| --- | --- | --- |
| `hintBackground` | `#000000` at 80% (`0xCC`) | Tap-to-translate hint bubble |
| `hintForeground` | `#FFFFFF` | Hint bubble text |
| `controlFill` | `#FFFFFF` at 12% (`0x1F`) | Fill behind the speed button, over the primary bar |
| `controlBorder` | `#FFFFFF` at 35% (`0x59`) | Speed button border |
| `disabledOpacity` | `0.4` | Any control that is currently unavailable |

## Contrast enforcement

The host chooses `primaryColor` and `surfaceColor` freely, and pairs them with a
foreground guessed at configuration time. A white caption over a yellow brand
bar is unreadable, and unreadable is a defect here, not a styling opinion.

**Rule.** Before painting any foreground on a configured background, compute the
WCAG 2.1 contrast ratio. If it is below **4.5:1**, replace the foreground with
black or white — whichever scores higher against that background. Otherwise use
the configured color unchanged.

```
ratio(a, b) = (max(L(a), L(b)) + 0.05) / (min(L(a), L(b)) + 0.05)
```

where `L` is WCAG relative luminance; the result runs from 1 (identical) to 21
(black on white).

Applies to two pairs:

| Foreground | Background | Where it shows |
| --- | --- | --- |
| `onPrimaryColor` | `primaryColor` | Control icons, caption text, pill glyphs |
| `textColor` | `surfaceColor` | In-stage error and blocked messages |

Requirements:

- Ports MUST resolve through this guard rather than painting the configured
  color directly.
- A substitution SHOULD emit a one-line developer warning in debug builds
  naming the offending color, its actual ratio and the substitute — silently
  ignoring the configured color leaves integrators wondering why their brand
  foreground never appears. It MUST NOT warn in release builds.
- Resolution is pure and the inputs rarely change, so results SHOULD be cached
  per `(background, foreground)` pair rather than recomputed every frame.

## Source of truth

- `lib/src/views/sign_tokens.dart`
- `lib/src/views/color_contrast.dart`
