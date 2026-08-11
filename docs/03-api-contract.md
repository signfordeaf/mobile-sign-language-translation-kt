# 03 — API contract

Everything the SDK sends and receives. Base URL, key and origin come from the
integration's configuration ([04](04-configuration.md)).

## Transport

| Property | Value |
| --- | --- |
| Base URL | The configured `apiUrl` (e.g. `https://kor01rp02.signfordeaf.com`) |
| `Accept` header | `application/json` |
| `Origin` header | The configured origin — `originUrl` if given, otherwise `apiUrl` |
| Connect / receive / send timeout | **30 s** each |

The origin identifies the calling app to the backend and is sent **twice**: as
the `Origin` header and as the `url` query parameter. They MUST carry the same
value.

## `GET /Translate`

The only call required for a working SDK.

### Request

| Parameter | Value |
| --- | --- |
| `s` | The text to translate — one segment, already split and length-capped ([09](09-sentence-segmentation.md)) |
| `rk` | API key |
| `fdid` | Dictionary id currently in effect |
| `tid` | Translator id currently in effect |
| `language` | Numeric language code — `1` Turkish, `2` English, `6` Arabic |
| `url` | Origin, same value as the `Origin` header |

Language codes `3` (German), `4` (French) and `5` (Spanish) exist in the scheme
but are **not** supported by the backend; a port MUST NOT offer them.

`s` travels in the query string, which is why text is capped at 900 characters
before it is sent — see [09](09-sentence-segmentation.md).

### Response

```json
{
  "state": true,
  "baseUrl": "https://.../videos/",
  "name": "abc123.mp4",
  "cid": "…",
  "st": true,
  "tid": "44",
  "fdid": "36"
}
```

| Field | Meaning |
| --- | --- |
| `state` | Whether the video is ready. `false` means *still rendering* |
| `baseUrl` | Directory the video lives in |
| `name` | File name of the video |
| `cid` | Translation id — needed for feedback and contact ([12](12-events-and-errors.md)) |
| `st` | Backend status flag, carried through but not acted on |
| `tid`, `fdid` | **Optional override.** Present only when the backend served the translation under a different pair than requested ([10](10-placeholder-avatars.md)) |

`tid` and `fdid` MUST be read leniently: the API is not consistent about quoting
them, so both `"44"` and `44` MUST parse to the same value. The SDK keeps ids as
strings everywhere.

### Polling

`state: false` means the video is being rendered. The SDK repeats the **same
request** until it is ready:

- Delay between attempts: **1000 ms**
- Maximum attempts: **30** (so ~30 s of polling on top of the request time)
- On exhaustion: treat as *no translation available* — the state machine goes to
  `error` with `apiError`, and nothing is cached.

This is why prefetching one sentence ahead matters so much
([02](02-architecture.md)): a cold segment can cost half a minute, a warm one
nothing.

### Assembling the video URL

```
videoUrl = baseUrl + name          // simple concatenation, no separator inserted
videoUrl = videoUrl.replaceFirst("http:", "https:")
```

The rewrite replaces only the **first** occurrence, and only the scheme prefix.
It exists because the backend may hand back an `http:` URL that mobile
platforms refuse to load under their transport-security defaults.

If `state` or `baseUrl` is missing from an otherwise successful response, the
translation MUST fail with `apiError` — a response without a video is not a
video.

### Cancellation

An in-flight request MUST be cancellable (the user closing the player or
starting another translation). A cancelled request is reported distinctly from a
failure:

- the state machine returns to `idle`, not `error`;
- a `translationError` event with code `cancelled` is emitted;
- nothing is cached.

**Reference implementation:** cancellation is modelled as a response with
`cid: "cancelled"` and `state: false`, which the controller checks before
anything else. A port SHOULD use its platform's cancellation mechanism instead
of a sentinel value; only the observable behavior above is normative.

### Error mapping

| HTTP status | Error | User-visible result |
| --- | --- | --- |
| 400 | Bad request | `error` state, failure message |
| 401 | Unauthorized | `error` state, failure message |
| 403 | Forbidden | `error` state, failure message |
| 404 | Not found | `error` state, failure message |
| 500 | Server error | `error` state, failure message |
| other | Unknown, carrying the status code | `error` state, failure message |
| transport failure | `networkError` | `error` state, failure message |

The failure message shown to the user is the localized generic string
([13](13-localization-and-accessibility.md)), never the raw error. The specific
code travels on the `translationError` event for hosts that want to log it
([12](12-events-and-errors.md)).

**Reference implementation:** in debug builds these exceptions are rethrown so
integration mistakes surface loudly; in release builds they are converted to an
empty result. A port SHOULD keep an equivalent split — loud in development,
never crashing in production.

## `POST /Feedback` and `POST /Contact` — not yet live

The 👍/👎 and contact affordances are fully implemented in the UI and the event
flow, but the endpoints are **stubbed**: a build-time flag says whether they
point at a real backend. While it is off, the SDK skips the network call and
reports success, so the UI and events still behave correctly end to end.

Ports SHOULD carry the same structure — complete UI and events behind one flag,
so wiring the real endpoints later touches nothing but the constants.

| Constant | Current value |
| --- | --- |
| Feedback path | `/Feedback` |
| Contact path | `/Contact` |
| Translation id parameter | `cid` |
| Text parameter | `s` |
| Vote parameter | `vote` |
| Positive / negative vote | `1` / `0` |
| Endpoints configured | `false` |

Both calls send the same credential block as `/Translate` (`rk`, `fdid`, `tid`,
`language`, `url`) alongside their own parameters.

**Both MUST swallow every failure.** Feedback is a side channel; it may never
disturb playback. A rejected feedback call rolls the vote back in the UI and
emits nothing.

## Source of truth

- `lib/src/service/service.dart`
- `lib/src/model/sign_model.dart`
- `lib/src/service/http_exception.dart`
