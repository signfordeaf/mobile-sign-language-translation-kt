# 10 — Placeholder avatars

While a translation is being fetched the player does not show a spinner on an
empty stage. It shows a **signer**, looping, blurred, with a small spinner over
it. This document covers who that signer is, how they are chosen, and how the
clips are built.

## Signer identity

Each bundled clip is a specific person, and each person is identified on the
backend by a `tid` / `fdid` pair.

| Signer | `tid` | `fdid` | Sign language | Asset |
| --- | --- | --- | --- | --- |
| Kadir | `23` | `16` | TSL | `placeholder-kadir.mp4` |
| Hesna | `43` | `35` | TSL | `placeholder-hesna.mp4` |
| Jason | `44` | `36` | BSL | `placeholder-jason.mp4` |
| Owais | `37` | `29` | ASL | `placeholder-owais.mp4` |

The sign language label is informational. What decides the language is the id
pair; it is **not** the same axis as the configured `language`, which names the
*spoken* language of the source text — BSL and ASL both sit under English there.

The SDK's default ids (`tid: 23`, `fdid: 16`) are Kadir, so an integration that
sets nothing gets a consistent pair.

## Why the loop must match

A fixed default avatar looped one person while the translation came back in
another's hands: two different people signing the same sentence, seconds apart.
The idle signer MUST therefore follow the ids in use.

## Resolution

Given the configuration and the ids currently in effect:

1. **An explicitly pinned signer wins.** The integration can hold one signer
   regardless of ids.
2. **Exact pair match** — both `tid` and `fdid` match a bundled signer.
3. **`tid` alone.**
4. **`fdid` alone.**
5. **Fallback**: a stand-in signer (Hesna in the reference implementation), not
   a bare spinner.

`tid` wins over `fdid` when the two disagree, because `tid` *is* the translator
while `fdid` only selects the vocabulary they sign from. A custom dictionary
against a known translator is a real integration; the person on screen is the
translator either way.

Empty strings count as absent. If both ids are absent, nothing resolves and the
fallback applies.

### Host-supplied clips

| `placeholderAsset` | Behavior |
| --- | --- |
| `null` (default) | Use the bundled clip chosen above |
| a path | Use the host's own clip, resolved against the **host app's** assets |
| `""` | Opt out of video entirely — plain spinner |

A host-supplied asset overrides the signer resolution completely.

## Backend override

The backend may serve a translation under a **different** pair than requested —
an account can be pinned to a different translator or dictionary than the app
asked for. When the response carries `tid` and/or `fdid`
([03](03-api-contract.md)):

- the SDK MUST adopt the served value(s) as the current ids;
- the idle signer switches accordingly for the rest of the session;
- the corrected ids MUST go out on subsequent requests;
- absent, empty and unchanged values change nothing;
- either id may be present without the other, and each is honoured on its own.

Two scoping rules:

- **Adopt before the failure check.** An override is worth keeping even when
  the translation itself did not come through — it decides which signer the loop
  shows on the next attempt.
- **Foreground requests only.** A prefetch runs silently behind a video that is
  already playing; letting it swap the signer would change the loop out from
  under the user for a sentence they have not asked for yet
  ([02](02-architecture.md)).

Adopting ids MUST notify the UI, so the loop updates without waiting for the
next state change.

## Playback of the loop

| Rule | Value |
| --- | --- |
| Looping | Continuous |
| Audio | Muted |
| Blur | `2.5` sigma while a translation is in flight, none while idle |
| Spinner | 24 pt, 2.5 pt stroke, in the theme's primary color, centred over the blur |

The veil (blur + spinner) is applied **only** while loading. An idle player —
open with nothing translated yet — plays the loop clean: blurring it with
nothing loading would promise a video that is not coming
([01](01-overview.md), principle 3).

**Reference implementation:** the blur filters the child directly rather than
sampling a backdrop (there is no backdrop to sample), and uses a decal tile mode
— clamping smears edge pixels outward and leaves a dirty rim around the video.

### Lifetime

The loop's decoder MUST be owned by the player and released when the player goes
away. Android caps concurrent hardware decoders, and the SDK has no business
holding one between translations.

Recreate the decoder when the resolved signer or the host-supplied asset
changes; do not keep a decoder per signer.

### Failure

If the clip cannot be played — missing asset, unsupported platform, decoder
failure — the SDK MUST fall back silently:

| While loading | While idle |
| --- | --- |
| The spinner alone | The SDK mark, tinted at 35% of the primary color, 32 pt |

A decorative loop is never worth an error. The card must render normally in
both cases.

## Asset requirements

Ports MUST bundle the same four clips, produced the same way.

| Property | Value | Why |
| --- | --- | --- |
| Motion | **Boomerang** — the clip's own reverse concatenated onto its end | Plain looping then reads as smooth back-and-forth motion |
| Resolution | 540 px | |
| Aspect ratio | 900 × 828 (≈ 1.087) | Matches the real translation videos, so the stage keeps its size from the first idle frame through to playback ([06](06-player-layout.md)) |
| Audio | Stripped | |
| Total size | ~184 KB for all four | Down from 720 KB for the originals, despite each being twice as long |

The boomerang is baked into the asset because video players generally cannot
play in reverse, and simulating it by seeking backwards forces a keyframe decode
per frame, which stutters on device. Baking it costs nothing at runtime.

Source clips are kept alongside the encoded ones (`assets/videos/source/`) so
the encode can be repeated.

## Source of truth

- `lib/src/config/signfordeaf_config.dart` (`PlaceholderAvatar`, `SignForDeafCardConfig`)
- `lib/src/views/placeholder_avatar.dart`
- `lib/src/views/loading_veil.dart`
- `lib/src/signfordeaf_manager.dart` (id adoption)
- `lib/src/signfordeaf_controller.dart` (when adoption happens)
- `test/placeholder_avatar_test.dart`
