# Third-Party Notices

This file records third-party components distributed in this repository that are **not** covered by
the repository's GPL-3.0 `LICENSE` file, together with the attribution their licences require.

> **Read this before assuming the GPL-3.0 badge covers everything in this tree.**
> The `LICENSE` file at the repository root applies to Aura's own source code. It does **not** apply to
> the third-party binaries listed below, and those binaries are **not** redistributable under GPL-3.0
> terms. Anyone forking, vendoring or redistributing this repository is responsible for obtaining their
> own licence for them.

---

## Superpowered Audio SDK — proprietary, NOT GPL-3.0

**Files in this repository:**

| Path | Contents |
| --- | --- |
| `app/src/main/cpp/superpowered/lib/<abi>/libSuperpoweredAndroid.a` | Proprietary static libraries (arm64-v8a, armeabi-v7a, x86, x86_64) |
| `app/src/main/cpp/superpowered/include/*.h` | Proprietary SDK headers |

Aura's own JNI glue (`app/src/main/cpp/superpowered/SuperpoweredBridge.cpp`) is Aura code and **is**
covered by the repository `LICENSE`. Everything else in `app/src/main/cpp/superpowered/` is
Superpowered Inc.'s property, licensed — not assigned — to this project.

**Governing agreement:** SUPERPOWERED SDKS MASTER LICENSE AGREEMENT, effective 16 October 2019.
A verbatim copy is included in this repository as required by Section 2.1(a)(ii):

- [`docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.pdf`](docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.pdf) — the authoritative document
- [`docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.txt`](docs/licenses/SUPERPOWERED_SDKS_MASTER_LICENSE_AGREEMENT_2019-10-16.txt) — plain-text extraction, for diffing and searching only

The current version of the agreement always lives at <https://superpowered.com/licensing> and, per the
agreement's own Preamble, a newer effective date there supersedes the copy included here.

**Required attribution** (Section 5.2(b)) — also shown in the app under Ajustes ▸ Acerca de ▸
Información legal:

```
Aura Hi-Res Player uses Superpowered SDKs. Superpowered.com
Copyright 2013 – 2026, Superpowered, Inc. All rights reserved.
```

**Why the static libraries are in a public repository at all:** Section 2.1 of the agreement expressly
permits it — *"The Superpowered SDKs may be embedded into open-source, source-code and/or source-code
repo, provided such use in compliance with the terms of this Agreement"* — subject to the two
conditions in 2.1(a): Superpowered must be mentioned in the README, and a copy of the agreement must be
included. Both are satisfied by this file, the README section, and `docs/licenses/`.

**What that permission does *not* grant.** Section 1.1(b) still forbids furnishing the SDK to third
parties, and Section 1.1(e) forbids combining or distributing the SDK with code under a licence that
would place the SDK itself under terms other than the agreement's. A licence for the Superpowered SDK
is per-Application (Section "What if I have more than one application?") and is not transferable
(Section 1.1(a)), so cloning this repository does **not** convey any right to use these binaries.
Obtain your own licence from <mailto:licensing@superpowered.com>.

---

## yt-dlp EJS player solver — Unlicense, bundles meriyah (ISC) and astring (MIT)

| Path | Contents |
| --- | --- |
| `app/src/main/assets/solver/yt.solver.lib.js` | `yt-dlp-ejs` 0.8.0 library bundle (meriyah ISC, astring MIT), unmodified |
| `app/src/main/assets/solver/yt.solver.core.js` | `yt-dlp-ejs` 0.8.0 core, one line patched (marked `AURA PATCH` in its header) |

Source: <https://github.com/yt-dlp/ejs> (Unlicense). All three licences are GPL-3.0 compatible, so this
component **is** redistributable with the rest of the repository. It solves YouTube's signature and `n`
challenges by running the real player in a WebView; see `EjsNTransformSolver.kt`.

---

## Engine availability is verifiable, not assumed

Superpowered validates a licence key at runtime, and the agreement states service may be interrupted
"with or without notice" if a licence is out of compliance (Introduction; Sections 3.1(a)(iii),
8.2). Because `Superpowered::Initialize()` returns `void` and the SDK exposes no licence-status API, a
disabled key would otherwise be **silent**: the app would keep running with an inert equalizer.

Aura therefore runs a one-shot empirical DSP probe immediately after initialization
(`probeSuperpoweredDsp` in `SuperpoweredBridge.cpp`) and records the verdict in the persisted
application log as `SUPERPOWERED engine=HEALTHY|DEGRADED|UNAVAILABLE`. The licence key is never logged.

---

## SongMirror recording-matching rules — MIT

| Path | Contents |
| --- | --- |
| `migration/src/main/kotlin/com/aura/migration/resolver/RecordingMatcher.kt` | Kotlin adaptation of SongMirror's `songmirror/engine/matching.py` (`score_candidate`, recording-version gates, catalog-name rules, featured-credit and performer-credit comparison) and of the RapidFuzz `token_sort_ratio` / `token_set_ratio` semantics it relies on |

Source: <https://github.com/ahnafnafee/songmirror> at commit `e5bad7a` (MIT). Adapted, not copied verbatim:
rewritten in Kotlin with no new dependency; the differences are listed in the file's header. MIT is
GPL-3.0 compatible, so this code **is** redistributable with the rest of the repository under the notice below.

```
MIT License

Copyright (c) 2026 Ahnaf An Nafee

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- **SongMirror — Amazon Music read path** (MIT, Copyright (c) 2026 Ahnaf An Nafee, <https://github.com/ahnafnafee/songmirror> @ `e5bad7a`): `migration/src/main/kotlin/com/aura/migration/source/amazon/` (`AmazonMusicWeb.kt`, `AmazonMusicClient.kt`, `AmazonMusicSource.kt`) adapts the read side of `songmirror/amazon_music_web.py` and `songmirror/engine/targets/amazon_music.py`; the full MIT permission notice is in the header of `AmazonMusicWeb.kt`.
