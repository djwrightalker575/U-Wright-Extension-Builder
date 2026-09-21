# WIRE Android Browser v0.1

A first runnable Android browser/runtime built around a privileged `chatgpt.com` operator tab and a closed-loop `WIRE_ANDROID_REQUEST_V1` / `WIRE_ANDROID_RESULT_V1` protocol.

## V0.1 capabilities

- Multiple persistent in-process browser tabs (Android WebView)
- Dedicated ChatGPT operator tab
- Automatic detection of WIRE request code blocks emitted by ChatGPT
- Automatic result return into the ChatGPT composer, with manual fallback dialog if the site UI changes
- Tab listing, activation, navigation, back/forward/reload
- Page snapshot, selector query, click, fill and scroll
- Postcondition-aware terminal states: VERIFIED / OBSERVED / FAILED
- Persistent append-only event log in app-private storage
- Browser-scoped self-extension through capability packs (host pattern + JavaScript), persisted locally
- Capability registry and arbitrary capability execution against target tabs
- Titanium compatibility notes and packaged extension scaffold

## Operator protocol

ChatGPT emits:

```text
WIRE_ANDROID_REQUEST_V1
{"request_id":"wire-001","action":"tabs","args":{}}
```

WIRE returns:

```text
WIRE_ANDROID_RESULT_V1
{"request_id":"wire-001","action":"tabs","status":"VERIFIED","data":{...}}
```

The browser only accepts requests originating from a tab currently loaded on `chatgpt.com` or `www.chatgpt.com`.

## Self-extension model

`install_capability` stores browser-scoped JavaScript adapters. It does **not** grant new Android permissions, native-code execution, shell access or arbitrary Java reflection. A capability declares an id, host pattern, description and script. `run_capability` executes that script in a matching page and returns its result.

This makes V0.1 extensible without turning generated page adapters into an unrestricted native execution path.

## Build

```bash
gradle assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Titanium path

Titanium Browser for Android supports Chromium extensions, including unpacked extensions. V0.1 therefore includes `titanium-extension/` as a portability scaffold. A future Titanium-native build can reuse the same WIRE protocol while replacing WebView tab execution with Chromium extension APIs.
