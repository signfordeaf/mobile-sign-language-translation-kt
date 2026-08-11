# 14 — Persistence

The SDK stores very little, and what it stores is deliberate: preferences the
user set by hand, and a counter that stops an onboarding hint repeating forever.

## Storage interface

Two operations, both asynchronous, both string-keyed and string-valued:

```
getItem(key) -> string?
setItem(key, value)
```

Two implementations:

| Implementation | Lifetime | Used by |
| --- | --- | --- |
| In-memory | Current session | Default, and tests |
| Platform key–value store | Across launches | What the integration layer installs in a real app |

Ports SHOULD keep this seam. Tests that pin down preference behavior need a
store with no platform underneath it.

**Key prefix: `weaccess_sl_`** — prepended to every key by the persistent
implementation, so the SDK cannot collide with the host app's own preferences.
Ports MUST keep the same prefix so a device migrating between SDK versions keeps
its settings.

## What is stored

| Key | Full key on disk | Value | Written when |
| --- | --- | --- | --- |
| `hint_shown_count` | `weaccess_sl_hint_shown_count` | Integer as a string | Each time a hint show is consumed ([07](07-floating-button.md)) |
| `playback_speed` | `weaccess_sl_playback_speed` | Double as a string | The user changes the speed |
| `looping` | `weaccess_sl_looping` | `"true"` / `"false"` | The user toggles loop |

That is the complete list. Nothing about the user's content — no text, no
translation URL, no translation id — is ever written to disk.

## Restore rules

- Preferences are restored **once per session**, and restoring MUST NOT override
  a preference the user has already changed during that session.
- Restoration happens **before** the translation request, not after the video is
  ready, so the control bar shows the user's stored speed and loop choice while
  the translation is still loading. The integration layer SHOULD also trigger it
  on mount, so the bar opens correct even before the first translation.
- An unparsable or absent value falls back to the configured default
  (`defaultSpeed`, `defaultLooping` — [04](04-configuration.md)). A stored speed
  MUST be positive to be accepted.
- A stored speed that is no longer in the configured `speeds` list is still
  honoured; the speed button simply restarts its cycle from the first entry
  rather than getting stuck.
- The hint counter is restored once, lazily, before the first time a hint show is
  considered.

## What deliberately does **not** persist

| State | Lifetime | Why |
| --- | --- | --- |
| Floating button placement | Session, held by the integration layer | Survives the player opening/closing and the SDK being disabled, but resets to the right edge on the next launch ([07](07-floating-button.md)) |
| Translation cache | Session | URLs are backend-scoped and short-lived; a stale cached URL is worse than a re-fetch ([02](02-architecture.md)) |
| Enabled / disabled | Not stored | The host owns this — it belongs in the app's own settings, not the SDK's |
| Player position and collapse state | Per player | Opening the player is a fresh start |
| Feedback votes | Per translation | Reset on every new translation ([12](12-events-and-errors.md)) |

The enabled state is the one integrators most often expect the SDK to remember.
It MUST NOT: an accessibility preference belongs in the host's settings, where
the user can find and change it, and where it can be tied to an account or a
profile.

## Source of truth

- `lib/src/storage/signfordeaf_storage.dart`
- `lib/src/signfordeaf_controller.dart` (keys, restore rules, timing)
- `lib/src/signfordeaf_host.dart` (which store is installed, placement ownership)
