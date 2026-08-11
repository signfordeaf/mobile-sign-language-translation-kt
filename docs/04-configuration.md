# 04 — Configuration

Every setting the SDK accepts, with the default a port MUST reproduce. Defaults
matter more than usual here: most integrations set only the credentials, so the
defaults *are* the product for them.

## Root

| Field | Type | Default | Effect |
| --- | --- | --- | --- |
| `apiKey` | string | **required** | Sent as `rk` on every request |
| `apiUrl` | string | **required** | Backend base URL |
| `originUrl` | string? | `apiUrl` | Identifies the calling app: `Origin` header + `url` parameter |
| `language` | enum | `turkish` | Source language of the text; also picks the UI strings |
| `fdid` | string | `"16"` | Dictionary id |
| `tid` | string | `"23"` | Translator id — with `fdid` this is Kadir ([10](10-placeholder-avatars.md)) |
| `theme` | object | see below | Brand colors and radius |
| `floatingButton` | object | see below | Button appearance and behavior |
| `card` | object | see below | Player appearance and controls |
| `accessibility` | object | see below | Announcements and custom labels |
| `granularity` | enum | `sentence` | Whether a tap translates the sentence or the whole paragraph |
| `maxSegmentChars` | int | `900` | Longest text sent in one request ([09](09-sentence-segmentation.md)) |
| `longPressToTranslate` | bool | `false` | Long press translates text the host made tappable ([08](08-tap-to-translate.md)) |
| `smartPassthrough` | bool | `true` | Hand taps the SDK should not claim to the host app ([08](08-tap-to-translate.md)) |
| `autoEnable` | bool | `false` | Whether the SDK turns itself on at start |

`language` supports `turkish` (API code `1`), `english` (`2`) and `arabic`
(`6`). German, French and Spanish exist in the numbering but are not supported.

## Theme

| Field | Type | Default | Effect |
| --- | --- | --- | --- |
| `primaryColor` | color | `#6750A4` | Control bar fill, logo tint, active button fill, spinner |
| `textColor` | color | `#1C1B1F` | Caption and in-stage messages, over the surface |
| `onPrimaryColor` | color | `#FFFFFF` | Anything drawn *on* the primary color — control icons, caption glyphs |
| `surfaceColor` | color | `#FFFFFF` | Background behind the avatar video |
| `cornerRadius` | number | `16` | Outer radius of the stage and the control bar |

`onPrimaryColor` and `textColor` MUST NOT be painted directly. Read them through
the contrast guard, which substitutes black or white when the configured color
fails WCAG 4.5:1 against the color behind it ([05](05-design-tokens.md)).

## Floating button

| Field | Type | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | bool | `true` | Whether the button appears while the SDK is on |
| `idleBehavior` | enum | `peek` | `peek` (slides partly off the edge and fades), `fade` (fades only), `none` |
| `idleDelayMs` | int | `2500` | Inactivity before the idle behavior starts |
| `hintMaxShows` | int | `2` | How often the hint bubble is shown, ever ([14](14-persistence.md)) |
| `size` | number | `44` | Button diameter |
| `backgroundColor` | color? | white | Fill while tap mode is off |
| `activeBackgroundColor` | color? | `primaryColor` | Fill while tap mode is on |
| `iconColor` | color? | `primaryColor` | Logo tint while off |
| `activeIconColor` | color? | white | Logo tint while on |
| `borderColor` | color? | `primaryColor` | Border, drawn only while off |

## Player (card)

| Field | Type | Default | Effect |
| --- | --- | --- | --- |
| `draggable` | bool | `true` | Whether the user can drag the player around |
| `initialCorner` | enum | `bottomRight` | Corner it animates in from ([06](06-player-layout.md)) |
| `avatarHeight` | number | `240` | Requested stage height; width follows from the video aspect ratio |
| `avatarMaxWidth` | number | `212` | Width ceiling, used when a video turns out landscape |
| `placeholderAvatar` | enum? | `null` | Pins the idle signer. `null` means *follow the ids* ([10](10-placeholder-avatars.md)) |
| `placeholderAsset` | string? | `null` | Host-supplied idle clip, overriding the bundled one. `""` opts out of video entirely |
| `showFeedback` | bool | `false` | 👍/👎 pill over the avatar |
| `showContact` | bool | `false` | Contact button in the control bar |
| `showSpeed` | bool | `true` | Speed cycling button |
| `showLoop` | bool | `true` | Loop toggle |
| `speeds` | list | `[1.0, 1.2, 1.5, 2.0]` | Cycle order of the speed button |
| `defaultSpeed` | number | `1.0` | Speed for a user with no stored preference |
| `defaultLooping` | bool | `true` | Loop setting for a user with no stored preference |

`showFeedback` and `showContact` are off because they compete with the avatar
for space *and* because the endpoints they report to are not live
([03](03-api-contract.md)).

`avatarHeight` and `avatarMaxWidth` are **requests, not guarantees**. The
player's real size comes from the screen-height budget in
[06](06-player-layout.md), which can shrink both.

> **Doc discrepancy.** `README.md` shows `avatarMaxWidth: 170` in its example
> block; the actual default in code is `212`. The code is correct.

## Accessibility

| Field | Type | Default | Effect |
| --- | --- | --- | --- |
| `announceOnOpen` | bool | `true` | Screen-reader announcement when a translation becomes playable |
| `announceOnClose` | bool | `false` | Announcement when it goes away |
| `videoPlayerLabel` | string? | localized default | Accessibility label of the video |
| `closeButtonLabel` | string? | localized default | Accessibility label of ✕ |
| `bottomSheetHint` | string? | localized default | Accessibility hint for the player surface |

## Precedence

1. **A single config object wins outright.** When one is supplied it sets every
   field; individual parameters are ignored.
2. **Otherwise individual parameters apply** where given (credentials, origin,
   language, theme, floating button), and everything else keeps its default.
3. **Runtime setters** exist for every field and take effect from the next read.
   The backend override of `tid`/`fdid` uses this path
   ([10](10-placeholder-avatars.md)).

Configuration MUST be applied before the first frame renders, so the SDK never
flashes default colors or the wrong language.

## Credentials

When the SDK is mounted around the whole app, missing credentials are a
programming error: fail loudly in debug builds, and in release builds render the
host app untouched rather than crashing it. When it is mounted around a region
only, the credentials belong to the root mount and are not re-checked.

## Source of truth

- `lib/src/config/signfordeaf_config.dart`
- `lib/src/signfordeaf_manager.dart`
- `lib/src/signfordeaf_host.dart`
