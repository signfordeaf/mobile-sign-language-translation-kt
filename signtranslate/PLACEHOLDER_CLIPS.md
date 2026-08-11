# Idle signer clips

Drop the four bundled signer loop clips here (doc 10). They are picked up
automatically by `SignerResolver`/`SignPlayerView` via `resources.getIdentifier`,
so the module compiles and runs (with the graceful spinner/mark fallback) before
they are added.

Required files (mp4, boomerang, 900×828, audio stripped):

| File                     | Signer | tid | fdid |
| ------------------------ | ------ | --- | ---- |
| `placeholder_kadir.mp4`  | Kadir  | 23  | 16   |
| `placeholder_hesna.mp4`  | Hesna  | 43  | 35   |
| `placeholder_jason.mp4`  | Jason  | 44  | 36   |
| `placeholder_owais.mp4`  | Owais  | 37  | 29   |

Until they are present the loading stage shows the spinner alone and the idle
stage shows the SDK mark tinted at 35% of the primary color.
