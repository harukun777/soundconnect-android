# Android direction

Anchor type: typography and live connection state. No new photography is needed for the compatibility port.
Adopted system: src/styles/tokens.css charcoal/gold palette, adapted to Android resource tokens.
Primary action: select a paired device and connect. Confirmed battery and device state follow below it.
Design language: dark / flat / restrained / native / compact / sans / vertical / practical / direct.
Alternative considered: embedded desktop WebView; rejected because its modern CSS and Windows bridge would
add compatibility work on Android 9 and preserve desktop navigation inappropriate for the phone.

References used: existing Status/Noise/EQ screens and source tokens, Android native Bluetooth Settings,
Android Bluetooth API documentation, Android platform typography and touch-target conventions.
Taken: reuse brand color; system pairing flow; standard permissions; readable Japanese; 48dp touch targets.
No animation or image generation required. Layout grows vertically with font scaling and system insets.

Review: Android 9 native screen inspected at 1080×1920; scrollable Japanese text and platform controls
remain legible. Contrast pairs pass WCAG AAA. Static style scan: A (limited native-source coverage).
Hardware Bluetooth validation is still pending; no physical-device compatibility grade is claimed.
