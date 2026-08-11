# 07 — Floating button

The button is the SDK's only permanent presence in the host app. It appears when
the SDK is enabled and disappears while the player is up.

## Visibility

Shown only when **all** of these hold:

- the SDK is enabled;
- the button is enabled in configuration;
- the player is **not** visible.

The last one is deliberate: the player carries its own expand and close, so a
second affordance for the same thing is clutter.

## Appearance

| Property | Value |
| --- | --- |
| Size | `44` (configurable) |
| Logo | 60% of the button size, centred |
| Docked shape | Half-rounded "tab": full radius on the two corners facing away from the edge, square against it |
| Dragging shape | Full circle |
| Shadow | `pillShadow` ([05](05-design-tokens.md)) |
| Border | 2 pt, only while tap mode is **off** |

Colors fall back to the theme, but the defaults are **brightness-aware**, since
the SDK sits above the host's own theme and cannot read it:

| Slot | Light default | Dark default |
| --- | --- | --- |
| Background (off) | white | `#2A2A2A` |
| Background (on) | `primaryColor` | `primaryColor` |
| Logo tint (off) | `primaryColor` | white |
| Logo tint (on) | white | black |
| Border (off) | `primaryColor` | white |

Any color the integration sets wins over these. The invariant to preserve is
that **off reads as outlined and on reads as solid**, in both appearances.

## Placement

The resting place is stored as an **edge plus a vertical fraction**, never as
absolute coordinates:

| Field | Meaning |
| --- | --- |
| `dockRight` | Which edge it is docked to |
| `verticalFraction` | Position along the draggable band, `0` = top, `1` = bottom |

A fraction because a rotation or any other size change would otherwise strand
the button off-screen.

**This state MUST live outside the button.** The button is unmounted while the
player is open, so a position kept in the button's own state dies with it — that
is exactly the v1 bug where dragging the button to the left edge and then
opening and closing the player sent it back to the middle of the right edge. The
integration layer holds it, so it survives:

- the player opening and closing;
- the SDK being disabled and re-enabled.

It resets to its default — **middle of the right edge** — on the next app
launch. It is deliberately not persisted to disk ([14](14-persistence.md)).

The player opens on the side the button is docked to
([06](06-player-layout.md)), which is the other reason this lives on the host.

## Gestures

The button is driven by raw pointer events, not a tap recogniser, because a tap
and a drag start identically here.

| Event | Behavior |
| --- | --- |
| Pointer down | Cancel the idle timer, stop any animation, wake to full opacity, reset the travel counter. Keep the docked shape — switching to a circle here would flash on every plain tap |
| Pointer move | Follow the finger. Once travel exceeds **6 pt**, this is a drag: switch to the circle shape |
| Pointer up, travel ≤ 6 pt | **Tap** — settle at the nearest edge *and* open the player, in that order |
| Pointer up, travel > 6 pt | **Drag** — settle at the nearest edge |

Two rules matter more than the rest:

1. **One tap always acts.** A peeked button MUST NOT spend the first tap waking
   up — it wakes and opens the player in the same gesture. Spending a tap on
   waking left users tapping twice with nothing on screen explaining why the
   first did nothing.
2. **The 6 pt threshold is the guard**, not a separate wake-up tap. It is what
   keeps a stray edge swipe from opening the player.

Order matters on tap: settle first, then act. Acting opens the player, which
unmounts the button, so the settle animation must already be running.

### Snapping

On release the button springs to the **nearest** horizontal edge, keeping its
vertical position, clamped to the safe area. Animation: 300 ms on the
ease-out-back curve, whose slight overshoot is what makes it read as a snap.

Every resting position — dragged there or tapped back into place — reports the
new placement to the integration layer.

## Idle behavior

After `idleDelayMs` (default 2500) of no interaction, and only while resting at
an edge:

| Mode | Behavior |
| --- | --- |
| `peek` (default) | Slides **35%** of its width off the docked edge and fades to **55%** opacity |
| `fade` | Fades to 55% opacity, stays fully on screen |
| `none` | Nothing |

The idle peek MUST NOT be reported as a placement change: it is a temporary
slide-off, not a resting place, and recording it would make the button reappear
half off-screen next time.

## Hint bubble

A one-time bubble teaching the tap-to-translate gesture.

| Property | Value |
| --- | --- |
| Width | `200` |
| Max lines | 3, centred |
| Font | 13 pt, line height 1.3 |
| Background / foreground | `hintBackground` / `hintForeground` ([05](05-design-tokens.md)) |
| Radius | `radiusSmall` (8) |
| Gap to the button | 8 |
| Position | Below the button when it sits in the top half of the screen, above it otherwise |
| Alignment | Grows leftward when right-docked, rightward when left-docked, so it never spills off-screen |

Budget rules:

- Shown at most `hintMaxShows` times (default **2**) across the app's whole
  lifetime — the count is persisted ([14](14-persistence.md)).
- One show is consumed each time the player is opened, or tap mode is toggled on
  directly.
- Never shown while the button is peeked, or while tap mode is off.

## Accessibility

The button MUST expose itself as a button, carry the **localized** mode label
([13](13-localization-and-accessibility.md)), and report its selected state so a
screen reader can say whether translation mode is on. The v1 hardcoded Turkish
label was a bug.

## Source of truth

- `lib/src/signfordeaf_floating_button.dart`
- `lib/src/signfordeaf_host.dart` (placement ownership, visibility rules)
- `lib/src/signfordeaf_controller.dart` (hint budget)
