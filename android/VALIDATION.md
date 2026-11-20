# Android 9 validation — 2026-11-20

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
