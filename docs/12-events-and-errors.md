# 12 — Events and errors

The SDK exposes a lifecycle event stream so hosts can log translations, drive
analytics, or build their own UI around the controller. Events are
**observational**: nothing in the SDK depends on a host consuming them.

## Event payload

| Field | Type | Present on |
| --- | --- | --- |
| `type` | enum | always |
| `text` | string? | the text-related events |
| `videoUrl` | string? | `translationComplete`, `videoStart` |
| `error` | error? | `translationError` |
| `value` | free-form | the v2 events — see the table below |
| `cid` | string? | whenever a translation id is known |
| `timestamp` | timestamp | always, defaulting to emission time |

## Catalogue

In lifecycle order:

| Event | Fires when | Payload |
| --- | --- | --- |
| `blockedSensitive` | The segment was refused before any request ([11](11-sensitive-data.md)) | `text` |
| `textSelected` | A segment passed the sensitive check and is about to be translated — **including on a cache hit** | `text` |
| `translationStart` | A network request is actually being made. **Not** emitted on a cache hit | `text` |
| `translationError` | The request was cancelled, failed, or the video could not be initialised | `error` |
| `panelOpen` | A video became playable | `text` |
| `videoStart` | Playback started | `text`, `videoUrl`, `cid` |
| `translationComplete` | The translation is done and playing | `text`, `videoUrl`, `cid` |
| `videoEnd` | Playback reached the end | `text`, `cid` |
| `segmentChanged` | The user moved to another sentence of the paragraph | `text`, `value` = new index |
| `playbackSpeedChanged` | The speed changed | `text`, `value` = new speed |
| `cardCollapsed` | The player was collapsed or expanded | `text`, `value` = collapsed flag |
| `feedbackSent` | A 👍/👎 was accepted | `text`, `cid`, `value` = positive flag |
| `contactRequested` | The contact button was pressed | `text`, `cid` |
| `panelClose` | The player was dismissed **while a translation was playable** | — |

`panelOpen`, `videoStart` and `translationComplete` fire together, in that
order, on the same transition. They are kept distinct because hosts use them
differently: panel visibility, playback analytics and translation accounting.

`videoEnd` MUST fire **once** per playback run, not on every frame after the
end, and MUST re-arm when playback restarts or loops.

> **Known gap.** The `videoError` event type exists in the enum but is never
> emitted; video failures arrive as `translationError` carrying the `videoError`
> code. Ports SHOULD match the reference (report through `translationError`) and
> MAY simply omit the unused type.

## Error codes

| Code | Raised when |
| --- | --- |
| `networkError` | The request threw — transport failure, timeout |
| `apiError` | The response arrived without a usable video, including after polling was exhausted |
| `videoError` | The video URL could not be initialised or played |
| `configurationError` | Reserved; not currently raised |
| `cancelled` | The request was cancelled by the user or superseded |
| `unknown` | Reserved; not currently raised |

An error carries a human-readable message for logs. That message MUST NOT be
shown to the user — the player shows the localized generic failure string
([13](13-localization-and-accessibility.md)).

## Ordering guarantees

- Every translation emits **exactly one** terminal event: `translationComplete`,
  `translationError`, or `blockedSensitive`.
- A superseded translation (a newer tap arrived first) emits nothing further —
  the request token check aborts it silently ([02](02-architecture.md)).
- Prefetches emit **nothing at all**, ever.
- Cancellation emits `translationError` with `cancelled`, not a completion.

## Feedback and contact

The 👍/👎 pill and the contact button are wired end to end but their endpoints
are stubbed ([03](03-api-contract.md)). Their behavior:

| Step | Rule |
| --- | --- |
| Vote pressed | Recorded optimistically, the pill enters a sending state, one vote at a time |
| Accepted | The vote sticks, an acknowledgement is shown, `feedbackSent` is emitted |
| Rejected | The vote is rolled back, no event is emitted |
| Any failure | Swallowed — feedback may never disturb playback |
| New translation | Vote, acknowledgement and translation id all reset |

The contact button emits `contactRequested` **before** the call is made, so the
host learns about the intent even if the call fails.

## Source of truth

- `lib/src/service/signfordeaf_events.dart`
- `lib/src/signfordeaf_controller.dart`
- `test/signfordeaf_controller_test.dart`
