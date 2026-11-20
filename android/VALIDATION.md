# Android 9 validation — 2026-11-20

## 0.3.1 — dark mode

Build, test APK and lint passed. Android 9 / API 28: **14 tests passed**.
Explicit dark mode overrides a light system configuration, explicit light overrides a dark configuration,
and automatic mode preserves the system choice. Theme selection persists through activity recreation.
The menu page is restored on recreation; automatic reconnection no longer forces that page back to Home.
Night resources select a native dark Material theme for dialogs, sliders, switches and system bars.
Light and dark Home/EQ/Noise/Settings screenshots were captured from the same recorded device state;
all four dark screens were inspected. Contrast: primary/card 13.17:1, accent/card 8.40:1,
on-accent/accent 8.55:1 (WCAG AAA). Theme choice is exposed in Menu > Display theme and stored locally.

## 0.3.0 — reference UI and automatic protocol selection

`assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` passed (0 errors / 18 warnings).
Installed over the previous APK on Android 9 / API 28 AOSP x86_64: **11 tests passed**.

- Existing six activity/transport/V1/V2 replay tests continue to pass.
- Service UUIDs override conflicting model/cache hints; missing SDP uses saved success then model hints.
- An initial failure closes the first transport and tries the alternate protocol exactly once.
- Successful attempts alone are remembered; stale/canceled callbacks cannot publish or reconnect.
- A live disconnect does not trigger another-protocol reconnect.
- Actual recorded WH-1000XM3 state renders My Device, EQ, Noise and Device Settings. Navigation,
  detailed setting labels and removal of the manual protocol selector were checked.

Native screenshots were inspected against the Sony Japanese reference captures listed in
design/REFERENCE.md. Product photographs, light-gray hero, white cards, blue active controls,
battery/codec identity, preset graph/carousel and four-tab navigation were reviewed at 1080×1920.
Text/card contrast: 14.94:1; blue/card: 5.84:1; white/blue: 6.42:1.
The style scanner reports A but scans little Java, so the native screenshot review is the useful evidence.

The reference UI is independently implemented and not a pixel-identical copy of private Sony assets.
Scene/account/cloud services are not implemented. Physical-headphone protocol discovery/fallback and
audible setting changes remain unverified on real hardware.

## 0.2.1 — settings acquisition fix

The connected transport's native poll callback incorrectly returned INPROGRESS when RX was empty.
libmdr only sends queued requests when poll returns OK, so real devices could wait for a request that
was never sent. Poll now reports the connected socket as usable, while recv still reports would-block
for an empty receive buffer. An independent main-thread watchdog closes stalled settings sessions.

`assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` passed. Android 9 / API 28 emulator:
**6 tests passed**, including sending the first request before any response and replaying V1/V2 captures
with initial silence, idle gaps and 64-byte reads. Hardware confirmation is still required.

## Original 0.2.0 validation

Build: version 0.2.0 / io.soundconnect.android, minSdk 28, targetSdk 35.

- `assembleDebug` and `assembleDebugAndroidTest`: passed.
- `lintDebug`: passed, 0 errors / 14 warnings. Remaining warnings concern dependency/target updates,
  string localization and a redundant API check; no unsupported Android 9 API errors were reported.
- APK manifest inspected with aapt: minSdk 28; arm64-v8a, armeabi-v7a and x86_64 native libraries present.
- Installed on a fresh Android 9 / API 28 AOSP x86_64 emulator and ran AndroidJUnitRunner: **5 tests passed**.
  - Native activity starts successfully.
  - Transport close is idempotent and reports disconnect to native callbacks.
  - WH-1000XM3 V1 recorded packets initialize/sync and a noise-control commit transmits bytes.
  - LinkBuds Clip V2 recorded packets initialize/sync and a noise-control commit transmits bytes.
  - Incomplete WF-1000XM5 recording identifies the device but keeps controls unavailable.
- Japanese native screen inspected at 1080×1920; content scrolls and the connection status remains readable.
- Token contrast on the dark surface: primary text 15.72:1, secondary text 9.79:1, accent 9.58:1.

Physical Bluetooth transport, headphone command acknowledgements and resulting audible changes remain
unverified. The emulator has no Bluetooth hardware. Replay tests exercise JNI and protocol serialization;
they do not establish real-world model compatibility. ARM binaries were compiled but not run on ARM hardware.

The added Android CI workflow repeats the build/lint and API 28 emulator tests. It has not yet run remotely.
Windows validation used JDK 21, Gradle 8.13, NDK 28.2.13676358, Build Tools 36.0.0 and locally installed
CMake 4.3.2 via `-PcmakeVersion=4.3.2`. The default source build selects SDK CMake 3.31.6.
