# AI Shortcut Keyboard (Android)

A system keyboard (IME) that works in any app and adds one-tap AI writing actions (Fix, Rephrase, Detail,
Shorten, Tone, Translate, Reply, Ask), inline triggers (`text /fix␣`), custom shortcuts, and bring-your-own-key
support for OpenAI, Anthropic, Groq, OpenRouter, Ollama Cloud and any OpenAI-compatible endpoint.

Package/product names are placeholders (`com.example.aikeyboard`). Replace before release.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35).

```bash
./gradlew :core:test                      # pure-Kotlin logic, no Android SDK needed with -PcoreOnly=true
./gradlew lintDebug :data:testDebugUnitTest :app:testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest       # emulator/device: IME registration smoke test
```

Core only, without the Android SDK or Google Maven access:

```bash
./gradlew -PcoreOnly=true :core:test
```

Enable it on a device/emulator (the debug build id has a `.debug` suffix):

```bash
adb shell ime enable com.example.aikeyboard.debug/com.example.aikeyboard.ime.AiKeyboardService
adb shell ime set    com.example.aikeyboard.debug/com.example.aikeyboard.ime.AiKeyboardService
```

Or just open the app: onboarding walks through enable, select, consent, key, try-it.

## Layout

| Module | What | Android? |
|---|---|---|
| `:core` | Providers (OpenAI-compatible + Anthropic, SSE, retry/fallback), prompt rendering, trigger parser, **`ActionRunner` state machine**, settings model, action import/export | No (pure Kotlin, 72 unit tests) |
| `:data` | DataStore repositories, Keystore `KeyCipher`, 24 h model cache, local metrics store, device state | Yes |
| `:app` | IME service, Compose keyboard (letters/symbols, shift, accents, backspace repeat, toolbar, preview), settings app (8 screens), `PROCESS_TEXT` activity | Yes |

The runner, retry/fallback and trigger logic live in `:core` behind small ports (`TextPort`, `ClipboardPort`,
`SettingsSource`) so they are tested on the JVM; `:app` only supplies the `InputConnection`/clipboard/DataStore adapters.

## What is implemented (vs. the spec)

All of sections 2, 5–15 of the spec: 8 built-in actions; toolbar + inline triggers (configurable prefix); custom
actions with provider/model/temperature/max-tokens/auto-apply/toolbar toggle, reorder, JSON import/export;
streaming preview with Accept / Retry / Copy / Cancel and a 5 s Undo; selection → whole-field → before-cursor capture
with mismatch detection; AI disabled in password/PIN/non-text/incognito fields, per-app blocklist (+ starter list),
and before first unlock; clipboard read only on tap; Keystore-encrypted keys; model lists fetched (never
hardcoded) and cached 24 h; Ollama `/api/tags` fallback; offline indicator; per-model parameter quirks
(`max_completion_tokens`, omit temperature); non-streaming mode; keyboard theme/accent/height/haptics/sound/number row; local-only usage metrics (§16.5) —
time to first token and total latency per provider/model, error kind/HTTP code counts, replace success and undo rate
per action, keyboard cold start — shown in Settings › Diagnostics, with an off switch and "Clear stats". Events carry
ids and timings only (§14); no text, prompts, results or keys.

### Deliberate changes from the draft spec

These fix problems found while reviewing the draft's code samples:

- **Prompt-injection hardening:** user text is wrapped in `<text>…</text>` and system prompts say to treat it as data.
- **Single-pass template substitution:** text containing `{arg}`/`{tone}` can no longer be rewritten.
- **Safer output cleaning:** only strips known preambles/wrapper tags/quotes the user didn't type themselves.
- **No `callTimeout` on streams** (it would cut long generations); total 60 s limit enforced by the runner.
- **API keys resolved on IO inside the request flow**, not on the main thread.
- **Consent gate:** nothing is sent until the privacy notice is accepted in onboarding.
- **Config errors are a distinct kind** (missing key/model/base URL) with an "Open settings" button instead of a retry.
- **Inline-trigger regex** requires the token to start the text or follow whitespace (`example.com/fix ` never fires).
- **Retries never happen after text has streamed**, so output is not duplicated; one retry (429 honours
  `Retry-After` up to 5 s, 5xx after 1 s), then the optional fallback provider.
- **Auto-apply is downgraded to preview** when the result is >2× the input or was cut off by the token limit.
- Plain `http://` is allowed only for `localhost`, `127.0.0.1`, `10.0.2.2` (network security config + `BaseUrlPolicy`).

### Not implemented (spec backlog / out of scope)

Word suggestions, swipe typing, voice, multilingual layouts, clipboard manager, emoji/GIF, on-device model, hosted mode.

## Verification status — read this

| Area | Status |
|---|---|
| `:core` (providers vs. MockWebServer, SSE for both adapters, error mapping, timeouts, retry/fallback, runner state machine, parser, prompts, codec) | **72 tests pass** (JVM) |
| `TextController` against a simulated host field, `FieldPolicy` | **17 tests pass** against real Android 14 framework classes (`android-all`); the same tests ship in `:app/src/test` |
| `:data` and `:app` Kotlin sources | **Type-checked** (compiled) against Compose Multiplatform 1.7.3 plus hand-written shims for AndroidX lifecycle/navigation/DataStore/activity. This catches typos and signature mismatches between modules but is **not** a real Android build |
| Manifest/resource references | Statically checked (XML well-formed, all `@xml/@style/@string` references resolve) |
| AGP/Gradle build, lint, `:data`/`:app` unit tests, debug APK | **Pass** (`lintDebug :data:testDebugUnitTest :app:testDebugUnitTest assembleDebug`) |
| R8 release build, Compose-on-IME runtime behaviour, real DataStore/Keystore, instrumented test | **Not run** — needs a device/emulator |

So before shipping: run the Gradle build above, then walk the manual matrix (spec §16.4) on a Pixel and a Samsung
device. Things most likely to need adjustment on first real run:

1. Compose-in-IME lifecycle wiring (`AiKeyboardService`) and `Modifier.windowInsetsPadding(navigationBars)` on Android 15 edge-to-edge.
2. The hand-rolled key gesture handling (`KeyCap`) — tune long-press/repeat feel.
3. Host-app quirks in the compatibility table (WebViews, Teams/Outlook).
4. Provider URLs/headers and which models accept which parameters — re-verify against current provider docs.

## Release checklist

- Replace `com.example.*`, `privacy_policy_url`, app name, add real launcher artwork.
- Add `UPLOAD_KEYSTORE*` secrets (see `.github/workflows/ci.yml`); enrol in Play App Signing.
- Publish a privacy policy; complete the Data Safety form (text is sent to user-chosen AI providers, not collected by you in BYOK mode — verify the wording against the current form) and Play's IME/User Data policy review.
- Get a security/privacy review before distributing to an organisation: a keyboard can see everything typed.
- Staged rollout: internal → closed beta (20–50 users) → 10 % → 100 %.
