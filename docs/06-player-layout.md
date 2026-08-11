# 06 — Player layout

The player is the only SDK surface that covers the host app, so its size is a
contract, not a style choice. This document specifies its anatomy, the algorithm
that sizes it, and how it moves.

## Anatomy

```
        ┌──────────────┐ ← window pill (collapse, close), 80% above the stage
 ┌──────┴──────────────┴──┐
 │ ▣ 👍👎                 │ ← mark badge (+ optional feedback pill), top-left
 │                        │
 │        signer          │   stage: video, or the idle loop, or a message
 │                        │
 └────────────────────────┘
            ↕ 8 pt
 ┌────────────────────────┐
 │   ⏸     1.2x     ⟳     │ ← control bar, primary-colored
 │  Hesap gerçek kişi     │ ← caption, same surface, no divider
 │  müşterilere...        │
 └────────────────────────┘
```

Two **independent blocks**, not one card: the stage and the control block, with
`spaceSm` (8 pt) between them. The window pill hangs above the stage's top-right
corner, outside its clip.

Rules that follow from *never cover the signer's hands*
([01](01-overview.md)):

- The control bar MUST sit **below** the stage, never over it. The web SDK
  overlays it; the mobile SDK deliberately does not.
- The window pill MUST hang mostly outside the stage — `pillOverflowFraction`
  (0.8) of its 44 pt height is above the top edge, so it costs the video 20% of
  a control.
- The caption MUST live in the control block, not over the video.
- The mark and the optional feedback pill sit in the **top-left** of the stage,
  `spaceXs` (4) from each edge — well clear of the signing space.
- There is **no on-screen sentence navigation.** The page itself is the
  navigation: smart passthrough makes tapping the next sentence a single
  gesture, so a `‹ n/m ›` strip would duplicate that at the cost of covering the
  signer. The controller still exposes next/previous segment for hosts that want
  their own controls ([12](12-events-and-errors.md)).

## Sizing algorithm

Normative. Reproduce this exactly, or the player will be proportionate on one
phone and oversized on another.

```
aspect        = video.aspectRatio, or 900/828 (≈1.087) before a video reports one

chromeHeight  = pillOverflow + spaceSm + controlSize + captionBlockHeight
              = 44×0.8   (35.2)
              + 8
              + 44
              + captionBlockHeight

captionBlockHeight = scaledFontSize × 1.35 × 2  +  8×2
                   = 12 × 1.35 × 2 + 16 = 48.4    (at text scale 1.0)

available     = screenHeight × 0.42 − chromeHeight

stageHeight   = min(avatarHeight,               // configured, default 240
                    avatarMaxWidth / aspect,    // configured, default 212
                    available)

stageWidth    = max(stageHeight × aspect, minStageWidth)   // minStageWidth = 140
```

Why each part:

- **Budget the whole player, not the avatar.** The stage height settles at
  `avatarMaxWidth / aspect` on every screen, so the *fixed* chrome is what
  decides whether the player looks proportionate. Capping the avatar alone
  leaves a compact phone with a player taking over half its height.
- **Reserve the caption before there is a caption.** The player can be open with
  nothing translated yet; budgeting the caption only once text arrives would
  shrink the avatar at the exact moment the user looks at it.
- **The caption height follows the *scaled* font size**, so raising the system
  text size grows the block instead of clipping the words in it.
- **`minStageWidth` is a floor, not a target.** On a short screen the height cap
  would otherwise squeeze the stage below the width three 44 pt controls need.
  Widening letterboxes the video by a point or two against the same surface
  color — invisible in practice — and keeps the bar exactly as wide as the
  stage.
- **The assumed aspect ratio matches the bundled idle clips (900×828)**, which
  come off the same production pipeline as real translations. The stage
  therefore keeps its size from the first frame of the idle loop through to
  playback instead of resizing under the user.

### Worked results

At text scale 1.0, default configuration, portrait. Measured from the reference
implementation, rounded to the nearest point:

| Screen | Stage | Whole player | Share of screen |
| --- | --- | --- | --- |
| 393×852 | 212×195 | 212×331 | 54% × 39% |
| 375×667 | 157×145 | 157×280 | 42% × 42% |
| 360×640 | 145×133 | 145×269 | 40% × 42% |

A port SHOULD hold these with a regression test on all three sizes. The
invariants are: **height ≤ 42% of the screen** and **width < 65% of the screen**
— the first design of this player took 72% × 64%, which defeated the point of
dropping the scrim.

### Control bar width

- Expanded: `max(stageWidth, controlCount × 44)` — normally exactly the stage
  width, growing only if an optional control (contact) needs more room. It MUST
  grow rather than clip.
- Collapsed: exactly `3 × 44 = 132`.
- Control count when expanded: play/pause, plus speed, loop and contact when
  each is enabled.

Every control MUST keep its full 44×44 hit area regardless. Shrinking the bar
never comes out of the controls' tap targets.

## Collapsed state

Collapsed, the player is **one piece**: a single 132×44 bar holding the mark,
expand and close. No stage, no separate window pill.

- The mark takes the play button's place, so branding stays visible.
- Speed and loop are **hidden, not disabled**: they belong to a video that is off
  screen, and the user's choices are preserved for when it comes back.
- Collapsing also turns tap mode off and pauses playback
  ([02](02-architecture.md)).

## Position and dragging

- The player opens from a corner: `initialCorner` decides top vs bottom, and the
  **floating button's docked side** decides left vs right, so it appears where
  the user's finger already is. With no button gesture to follow — a purely
  programmatic translation — `initialCorner` is used as configured.
- Initial inset from the safe area: `spaceMd` (12) on both axes.
- Dragging anywhere on the player moves it when `draggable` is on. A stationary
  tap must still reach the controls underneath — the drag only takes over past
  the platform's drag slop.
- The position MUST be clamped to the safe area on every drag update, on
  release, and **after every frame**: the player's size changes with the video's
  aspect ratio and the collapse state, and without the post-layout correction
  the bottom of the control bar can end up off screen.
- Entrance: fade plus a small upward slide (15% of its height), over
  `cardTransition` (320 ms) on the emphasized curve.

## Stage contents by state

| State | Stage shows |
| --- | --- |
| `ready` | The translation video, centred, at its own aspect ratio |
| `loading` | Idle signer loop, blurred, spinner over it ([10](10-placeholder-avatars.md)) |
| `idle` | Idle signer loop, clean — no blur, no spinner, no "translating" label |
| `error` | Localized failure message, centred in the stage |
| `blocked` | Localized sensitive-data message, centred in the stage |

Failure states render **inside** the stage, so the layout never jumps and the
app stays usable. The message MUST be vertically centred in the stage and MUST
scroll if it outgrows it. No logo is drawn in these states — the corner badge
already carries the mark, and a second copy competes with it on a stage this
small.

## Caption behavior

The caption is the sentence being translated. It appears from the moment a
translation starts — before the video arrives — so the user can confirm what
they tapped.

| Rule | Value |
| --- | --- |
| Visible height | 2 lines at 12 pt × 1.35 line height, plus 8 pt padding above and below |
| Fits in 2 lines | Centred and still |
| Longer | Auto-scrolls vertically |
| Hold at each end | 1600 ms |
| Scroll speed | 16 pt/second, clamped to a 400–8000 ms travel |
| Rewind to top | 450 ms, ease-in-out |
| After the user scrolls by hand | Auto-scroll stops, resuming 4 s after they stop |
| New sentence | Jumps back to the top and restarts the cycle |

The hold at the top is what makes the first words readable at all — without it
the sentence starts sliding before the eye has landed on it. A caption the
reader has to drag is a caption most readers never finish, which is why this
scrolls itself rather than waiting to be dragged.

The caption draws **no background of its own** and no divider separates it from
the controls: they share one surface so the block reads as a single object
rather than two stacked cards.

## Source of truth

- `lib/src/views/signfordeaf_card.dart`
- `lib/src/views/sign_control_bar.dart`
- `lib/src/views/sign_caption.dart`
- `lib/src/views/action_pills.dart`
- `test/sign_card_test.dart` (size budget, tap targets, bar width)
