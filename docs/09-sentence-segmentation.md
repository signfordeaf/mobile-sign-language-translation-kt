# 09 — Sentence segmentation

A tap translates the sentence under the finger, not the whole paragraph. On a
long legal clause the difference is a minutes-long video and a request that
polls for half a minute versus a few seconds of signing.

There is no ICU sentence breaker in play — the rules are explicit, and a port
MUST reproduce them rather than substitute a platform sentence tokenizer.
Platform breakers disagree on exactly the constructs these rules were tuned for
(`T.C.`, `A.Ş.`, `5.000.000 TL`, numbered clauses), and a different split
changes what is sent to the backend and what the cache keys on.

## The losslessness invariant

**The ranges returned for a text partition `[0, length)` exactly: concatenating
them reproduces the input character for character.**

- Trailing whitespace belongs to the segment that precedes it.
- A sentence can therefore never go missing. The worst a bad rule can do is put
  a boundary in the wrong place.
- Every later step (merging short fragments, length chunking) only ever *adds or
  removes boundaries*, never characters.

Ports MUST hold this with a property test over varied input.

## Boundary detection

Walk the text. A boundary exists at position `i` when:

1. **`text[i]` is a hard line break (`\n`)** — always a boundary; or
2. **`text[i]` is a terminator** — `.` `!` `?` `…` — *and* it genuinely ends a
   sentence (see below), *and*:
   - any run of closing punctuation immediately after it is skipped —
     `)` `]` `}` `"` `'` `»` `”` `’` — so `(…gibi.)` and `“…gitti.”` still break;
   - what follows the closers is **whitespace**. A terminator glued to the next
     character (`3.5`, `dosya.txt`) never ends a sentence, and neither does one
     at the very end of the text;
   - after skipping that whitespace there is more text, and it **looks like the
     start of a sentence**: a digit, an opening punctuation mark
     (`(` `[` `{` `"` `'` `«` `“` `‘` `-` `–` `—`), or an uppercase letter.

The boundary index is placed **after** the trailing whitespace, which is what
keeps the partition lossless.

A hard line break always starts a new sentence; a terminator only does when what
follows actually looks like one.

### When a period does not end a sentence

`!`, `?` and `…` are unambiguous. A `.` is rejected as a terminator in four
cases:

| Case | Example | Rule |
| --- | --- | --- |
| Decimal / thousands separator | `5.000.000`, `1.2` | Digit on both sides |
| Initialism | `T.C.`, `A.Ş.` | Preceded by a lone uppercase letter (the character before *that* is not a letter) |
| Known abbreviation | `vb.`, `Ltd.`, `Prof.` | The letter run before the period is in the abbreviation list |
| List marker | `7. Para yatırma…` | Only digits and periods precede it, and everything from the start of the current segment up to that number is whitespace |

URLs and e-mail addresses need no rule of their own: a boundary requires
whitespace after the terminator, and the dots inside `www.ziraatbank.com.tr` are
each followed immediately by a letter.

### Abbreviation list (Turkish)

Multi-letter only — single-letter initialisms are handled structurally by the
rule above. Matched case-insensitively against the letter run before the period.

```
vb  vs  vd  bkz  örn  age  çev  haz  ed  dr  doç  prof  av  sn
bay  bayan  öğr  gör  arş  ltd  şti  tic  san  md  gen  alb  yzb  ütğm
mah  cad  sok  apt  blv  no  tel  faks  kat  üniv  fak  böl  ans  yy  mö  ms
```

A port MAY extend this list per language, but MUST keep these entries — they are
what makes Turkish banking and legal prose split correctly.

### Merging short fragments

After boundaries are found, a segment containing **fewer than 2 cased letters**
is merged into its neighbour: a lone bullet, a dangling quote or a stray number
is not a sentence. If the final tail is too short to stand alone it is appended
to the previous segment.

Dropping a boundary only ever *joins* neighbours, so this stays lossless.

## Length chunking

Any range longer than `maxSegmentChars` (**900**) is subdivided.

### Why 900

Derived from the transport, not from taste. The text travels as the `s` query
parameter of a GET. The most conservative common server ceiling for a whole URL
is 2048 characters; the base URL and the other parameters take ~150; and Turkish
text roughly doubles under percent-encoding (a space becomes `%20`, `ç` becomes
six characters). That leaves ~900 raw characters before requests start failing
**at the gateway**, with nothing to show the user.

Ordinary prose never reaches this — the contract clauses in the example app are
190–250 characters. It is a guard, not a feature. It applies in both granularity
modes.

### Where to cut

For an over-long range, find the boundary **closest to the middle** of the
range, taking the first kind that has any candidates:

| Priority | Kind | Definition |
| --- | --- | --- |
| 1 | Punctuation | Just after `,` `;` `:` `—` `–` — or a `-` that is preceded by whitespace — when whitespace follows it; the cut lands after that whitespace |
| 2 | Coordinating conjunction | Immediately **before** `ancak`, `fakat`, `çünkü`, `veya`, `ya da`, `ve`, `ise`, `ki` when it stands as a whole word, so the conjunction opens the next chunk the way it opens the clause |
| 3 | Word boundary | After any whitespace run |
| — | Hard cut | Only when there is no usable boundary at all: cut at exactly `maxChars` |

Then **recurse into both halves**. Do not peel `maxChars` off the front: that
leaves a stub of a few words at the very end, which reads as a broken fragment.

Why clause boundaries at all: sign language is not a word-for-word transcoding —
Turkish Sign Language has its own grammar — so cutting every N words produces
clips that are individually plausible and collectively wrong. Punctuation and
conjunctions are natural pauses in signing too.

## Mapping a tap to a sentence

Given the character index under the finger:

- return the index of the range containing it (`start ≤ offset < end`);
- a tap **past the last character** belongs to the last sentence;
- anything else is "not found", and the caller falls back to translating the
  whole paragraph ([08](08-tap-to-translate.md)).

## Normalization — what is actually sent

Before a segment leaves the SDK:

1. Replace the object-replacement character `U+FFFC` (inserted where inline
   non-text content sits, to keep indices aligned) with a space. It MUST NOT
   reach the API.
2. Collapse every whitespace run — including the soft line breaks of a wrapped
   paragraph — to a single space.
3. Trim.

Step 2 is not cosmetic: the same sentence must always produce the same request
string, because that string is the cache key ([02](02-architecture.md)).

Whitespace for these purposes is space, `\n`, `\t`, `\r` and the non-breaking
space `U+00A0`.

## Granularity

| Mode | Behavior |
| --- | --- |
| `sentence` (default) | Translate the tapped sentence; report all sentences of the paragraph and which one was hit |
| `paragraph` | Translate the whole paragraph as one segment — still length-chunked |

`sentence` is never *worse* than `paragraph`: whenever splitting yields a single
segment, or the tap cannot be mapped to a character, the whole paragraph is
translated, which is exactly the v1 behavior.

## Character helpers

Two definitions that a port must get right rather than assume:

- **Letter** = a character whose lowercase and uppercase forms differ. This is
  what keeps Turkish `ı/İ` and `ş/Ş` working without a locale-specific alphabet.
- **Uppercase letter** = a letter equal to its own uppercase form.

Note the consequence: uncased scripts (Arabic) contain no "letters" by this
definition, so the short-fragment merge and the sentence-start test behave
differently there. That is deliberate — the rules were tuned for Latin-script
Turkish, and Arabic text falls back to whole-paragraph behavior rather than
being mis-split.

## Source of truth

- `lib/src/service/sentence_splitter.dart`
- `test/sentence_splitter_test.dart` (including the losslessness invariant)
