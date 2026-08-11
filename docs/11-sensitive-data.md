# 11 — Sensitive data

Text containing personal data is never sent to the translation backend. This is
a two-layer defence: patterns the SDK detects on its own, and text the host app
marked explicitly.

The check runs **per segment**, immediately before the request, on the exact
string that would have been sent. One clause carrying an ID number blocks only
itself; the rest of the paragraph stays translatable.

## What happens when text is refused

- The state machine goes to `blocked` ([02](02-architecture.md)).
- The player shows the localized refusal message inside the stage
  ([13](13-localization-and-accessibility.md)).
- A `blockedSensitive` event is emitted ([12](12-events-and-errors.md)).
- **Nothing leaves the device** — not the request, and not a prefetch: blocked
  text is skipped by the prefetcher too.

## Evaluation order

Trim the text. Empty → not sensitive. Then, first match wins:

1. Overlap with a manually marked text (below).
2. Email address.
3. Turkish IBAN.
4. Turkish mobile number.
5. 11-digit sequences that pass the national-ID checksum.
6. 13–19 digit sequences that pass Luhn.

Checksum-validated patterns come last because they are the expensive ones.

## Patterns

| Pattern | Expression | Notes |
| --- | --- | --- |
| Email | `[\w.+-]+@[\w-]+\.[\w.-]+` | |
| Turkish IBAN | `\bTR\d{2}(?:[ ]?\d{4}){5}[ ]?\d{2}\b`, case-insensitive | `TR` + 24 digits, spaces allowed between groups |
| Turkish mobile | `(?:\+90\|0)?[ ]?5\d{2}[ ]?\d{3}[ ]?\d{2}[ ]?\d{2}` | Optional `+90`/`0` prefix, spaces allowed |
| National ID candidate | `\b\d{11}\b` | Validated by checksum |
| Card candidate | `\b(?:\d[ -]?){12,18}\d\b` | Spaces and dashes stripped, then 13–19 digits validated by Luhn |

### Turkish national ID (TCKN) checksum

An 11-digit sequence is an identity number when **all** of these hold — with
digits `d[0]…d[10]`:

1. `d[0] != 0`;
2. `d[9] == ((d[0]+d[2]+d[4]+d[6]+d[8]) × 7 − (d[1]+d[3]+d[5]+d[7])) mod 10`;
3. `d[10] == (d[0]+…+d[9]) mod 10`.

The checksum matters: without it every 11-digit order number, reference code or
timestamp on the page would be refused, and users would conclude the SDK is
broken.

Note the modulo in rule 2 can be negative in languages where `%` follows the
sign of the dividend (C, Java, Kotlin, Swift, JavaScript). Dart's `%` always
returns a non-negative result. **A port MUST use a non-negative modulo here**
(`((x % 10) + 10) % 10`), or valid identity numbers will slip through.

### Luhn

Standard: from the rightmost digit leftwards, double every second digit,
subtract 9 from any result above 9, sum everything; valid when the total is
divisible by 10.

### Worked examples

| Text | Refused? | Why |
| --- | --- | --- |
| `iletisim: ali@example.com` | ✅ | Email |
| `TR33 0006 1005 1978 6457 8413 26` | ✅ | IBAN |
| `0532 123 45 67` | ✅ | Mobile |
| `Sipariş no 12345678901` | ❌ | 11 digits, but fails the checksum |
| `Kart: 4242 4242 4242 4242` | ✅ | 16 digits, passes Luhn |
| `Toplam 5.000.000 TL ödendi` | ❌ | No pattern matches |
| `Referans 2024 0001 0002` | ❌ | 12 digits, fails Luhn |

## Manual marking

The host can mark any part of its UI as sensitive. Marked text stays visible and
selectable on screen — only *translation* is refused.

Requirements:

- Marked strings are held in a process-wide registry, added when the marked
  content is mounted and removed when it is unmounted. A port MUST remove only
  the strings that mount registered, so two marked regions do not clear each
  other's entries.
- Empty and whitespace-only strings are ignored; entries are trimmed before
  storage.
- Matching is **two-way containment**: a candidate matches when it contains a
  registered string *or* a registered string contains it. The user may select
  all, part, or more than the marked region, and every one of those must be
  refused.

## Limits worth stating

The automatic layer is a **safety net, not a guarantee**. It is tuned for
Turkish personal data and general PII; it does not detect addresses, names,
account numbers in unusual formats, or personal data expressed in prose. Hosts
that know their content is sensitive MUST mark it explicitly rather than relying
on detection.

## Source of truth

- `lib/src/service/sensitive_data_guard.dart`
- `lib/src/signfordeaf_sensitive.dart` (marking widget)
- `lib/src/signfordeaf_manager.dart` (registry and matching)
- `test/sensitive_data_guard_test.dart`
