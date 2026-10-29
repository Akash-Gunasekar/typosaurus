<p align="center">
  <img src="branding/banner.jpg" alt="Typosaurus — the AI keyboard that eats typos" width="100%">
</p>

<p align="center">
  <img src="branding/icon.png" alt="Typosaurus icon" width="96"><br>
  <b>Typosaurus</b> — an AI keyboard for Android (and iOS) that fixes, rewrites, translates and replies in any app.<br>
  Bring your own key: <b>OpenAI · Anthropic · Groq · OpenRouter · Ollama Cloud</b> or any OpenAI-compatible endpoint.
</p>

---

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/keyboard.png" width="180" alt="Keyboard with the AI toolbar"><br><sub>Keyboard with one-tap AI buttons</sub></td>
    <td align="center"><img src="docs/screenshots/menu.png" width="180" alt="All AI shortcuts"><br><sub>⋯ every shortcut</sub></td>
    <td align="center"><img src="docs/screenshots/emoji.png" width="180" alt="Emoji panel"><br><sub>Emoji panel</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="180" alt="Home screen"><br><sub>Home</sub></td>
    <td></td>
    <td></td>
  </tr>
</table>

## What it does

| | |
|---|---|
| ✨ **One-tap AI** | Fix, Rephrase, Detail, Shorten, Tone, Translate, Reply, Ask — on the selection or the whole field |
| ⚡ **Inline triggers** | Type `pls send report tmrw /fix␣` or `good morning /tr:ta␣` and the text is rewritten in place |
| 🧩 **Your own shortcuts** | Custom prompts with their own emoji, keyword, provider, model and temperature; import/export as JSON |
| 👀 **Preview first** | Results stream into a preview: Accept, Retry, Copy or Cancel, then a 5-second Undo |
| 😊 **Emoji, 📋 clipboard, 🎤 voice** | Emoji panel, in-memory clipboard history (API keys and passwords are masked, never kept), voice typing |
| 🔒 **Private by design** | Text leaves the phone only when you tap an action, straight to *your* provider over HTTPS. Never from password, PIN or incognito fields, or apps you block. No Typosaurus server, no logs |
| 🪶 **Small and fast** | 3.3 MB release APK, opens in ~0.4 s; Inter typeface bundled as a 100 KB variable font |

## Install (Android)

Requires JDK 17+ and the Android SDK (compileSdk 35).

```bash
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk (~3.3 MB)
adb install -r app/build/outputs/apk/release/app-release.apk
```

Open **Typosaurus**: setup walks through enabling the keyboard, selecting it, the privacy notice and adding a key.
Without the Play upload key, local release builds are signed with the debug key — fine for testing, never upload them.

From the command line instead:

```bash
adb shell ime enable com.example.aikeyboard/.ime.AiKeyboardService
adb shell ime set    com.example.aikeyboard/.ime.AiKeyboardService
```

## iOS

The shared Kotlin core (prompts, provider adapters, retries, triggers) is compiled into a `TyposaurusCore`
framework and used by a SwiftUI app and keyboard extension in [`ios/`](ios). Building needs a Mac with Xcode 16+:

```bash
brew install xcodegen
cd ios && xcodegen && open Typosaurus.xcodeproj
```

Set your team under *Signing*, run the **Typosaurus** scheme, then on the iPhone: *Settings › General › Keyboard ›
Keyboards › Add New Keyboard › Typosaurus* and turn on **Allow Full Access** (needed to reach the AI provider).
The app stores keys in a shared Keychain group and settings in an App Group, in the same JSON format as Android.

iOS keyboards can't use the microphone or read other apps' text beyond the current field, so voice typing and
clipboard history are Android-only; iOS has the toolbar, all AI shortcuts, inline triggers, preview, Accept and Undo.

## How it works

```mermaid
flowchart LR
    app(["📱 Any app<br/>text field"])
    kb["⌨️ Typosaurus keyboard<br/>Android · Compose<br/>iOS · SwiftUI"]
    runner{{"🧠 ActionRunner<br/>shared Kotlin core"}}
    prompts[/"📝 Prompt templates<br/>+ your shortcuts"/]
    trig[/"⚡ Trigger parser<br/>text /fix␣"/]
    keys[("🔐 Your API key<br/>Keystore · Keychain")]
    route{"Provider<br/>router"}
    oa[["OpenAI · Groq<br/>OpenRouter · Ollama"]]
    an[["Anthropic<br/>Claude"]]

    app -- "selection or field" --> kb
    kb -- "tap ✨ or type /fix␣" --> runner
    prompts --> runner
    trig --> runner
    keys -. "read only for the request" .-> runner
    runner -- "HTTPS · retry · fallback" --> route
    route --> oa
    route --> an
    oa -- "streamed result" --> kb
    an -- "streamed result" --> kb
    kb -- "Accept · Undo" --> app

    classDef ink fill:#050505,stroke:#C6FF3D,color:#F2F5EE,stroke-width:2px
    classDef lime fill:#C6FF3D,stroke:#2F4A22,color:#050505,stroke-width:2px
    classDef green fill:#2F4A22,stroke:#C6FF3D,color:#F2F5EE
    class app,oa,an ink
    class kb,runner lime
    class prompts,trig,keys,route green
```

Every AI action goes through the same small state machine, so nothing reaches your text until you accept it:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Idle
    Idle --> Streaming: tap ✨ / type /fix␣
    Streaming --> Preview: result streamed
    Streaming --> Failed: offline · bad key · timeout
    Preview --> Applied: Accept
    Preview --> Streaming: Retry
    Preview --> Idle: Cancel
    Applied --> Idle: Undo (5 s) or keep typing
    Failed --> Streaming: Retry
    Failed --> Idle: Dismiss
```

| Module | What | Platforms |
|---|---|---|
| `:core` | Providers (OpenAI-compatible + Anthropic, SSE streaming, retry/fallback), prompt rendering, trigger parser, `ActionRunner` state machine, local metrics, `Assistant` facade for iOS | Kotlin Multiplatform: JVM/Android (OkHttp) and iOS (NSURLSession via Ktor Darwin) |
| `:data` | DataStore settings, Android Keystore-encrypted API keys, 24 h model cache, local usage metrics | Android |
| `:app` | Keyboard service, Compose keyboard (toolbar, emoji, clipboard, voice, preview), settings app, `PROCESS_TEXT` "Typosaurus Fix" menu | Android |
| `ios/` | SwiftUI setup app + keyboard extension using `TyposaurusCore` | iOS 16+ |

## Development

```bash
./gradlew -PcoreOnly=true :core:jvmTest   # shared core only — no Android SDK needed
./gradlew lintDebug :core:jvmTest :data:testDebugUnitTest :app:testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest       # emulator/device: IME registration smoke test
```

CI (GitHub Actions) runs lint and all unit tests, a dependency vulnerability scan, an emulator test, and a macOS job
that compiles the shared core for iOS and builds the app and keyboard extension for the simulator.

### Design decisions

- **Prompt-injection hardening:** user text is wrapped in `<text>…</text>` and prompts say to treat it as data.
- **Retries never happen after text has streamed** (no duplicated output): one retry (429 honours `Retry-After` up to 5 s, 5xx after 1 s), then the optional fallback provider.
- **Auto-apply falls back to preview** when a result is over 2× the input or was cut off by the token limit.
- **Per-model parameters:** Claude models never get `temperature`; OpenAI reasoning models (`o*`, `gpt-5`, `gpt-6`) get `max_completion_tokens` and no temperature. Override per model in *Settings › Providers › Advanced*.
- **Inline triggers** only fire when the token starts the text or follows whitespace, so `example.com/fix ` is safe.
- Plain `http://` is allowed only for `localhost`, `127.0.0.1` and `10.0.2.2` (local Ollama).

## Before publishing

- Replace the placeholder package `com.example.*`, the bundle ids in `ios/project.yml`, and `privacy_policy_url`.
- Publish a privacy policy; complete Play's Data Safety form and IME/User Data review, and App Store privacy details.
- Add the Play upload-key secrets and set the repo variable `PLAY_RELEASE=true` to enable the signed release job.
- Get a security/privacy review before distributing to an organisation: a keyboard can see everything typed.

---

<p align="center">Made with ❤️ by <b>Akash Gunasekar</b></p>
