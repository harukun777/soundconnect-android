# Sound Connect visual reference — 0.3.0

User direction: reproduce Sony Sound Connect's UI and configure connections automatically.
Audience: Japanese, Android 9+. Surface: Operate; expression: R1; interaction: I3.
Anchor type: real product photograph, centered device identity and battery/codec state.

Reference images were inspected locally, not inferred from page descriptions:

- https://www.sony.jp/headphone/update/images/wf-1000xm6/v/01.jpg
  Taken: current My Device hero, all-device-settings row, shortcut cards and bottom navigation.
- https://www.sony.jp/headphone/update/images/wf-1000xm6/v/03.jpg
  Taken: compact overview cards, blue circular selections and Japanese labels.
- https://support.sony.jp/electronics/support/articles/00353894
  Taken: feature order, detailed noise settings and native device-settings navigation.
- https://www.sony.jp/headphone/update/version_WF-1000XM6.html
  Taken: current System section and firmware identity placement.
- https://cdnv2.tgdd.vn/mwg-static/common/News/1575807/snoy-ehad-cach-su-dung-1-2.jpg
  Taken: equalizer graph geometry, centered preset selector and secondary adjustment control.

Direction: gray #E7E7E7 surface, #F4F4F4 cards, #4659AA controls, black text; resource tokens in
app/src/main/res/values/styles.xml. Default platform controls are retained for permission dialogs,
sliders and switches; the product and navigation layout follow the references.
The user's explicit Sony reference supersedes the old gold desktop palette.

Products use the existing Sony photographs documented in
../../docs/research/SoundConnect Desktop - Product Image Sources.md. Unknown models show a generic
headphones glyph; an unrelated product is never presented as the connected model.

Scope differences: scene learning, Sony account history and cloud services are unavailable and
explained on their tabs. No fake battery, codec, presets, activity or connection state is shown.
This is an independent implementation; icon vectors and platform rendering are not pixel-identical
to Sony's private app resources. Hardware behavior still requires physical-headphone verification.

Runtime review: API 28 native screenshots from actual recorded WH-1000XM3 state cover Home,
Equalizer, Noise and Settings. Review images are temporary artifacts, not shipping assets.
Final review: controls and navigation remained legible; Home spacing was tightened after the first
render to keep all three noise choices above the bottom bar. EQ preset names now use advertised text
with libmdr's defined preset names as a fallback. Expanded sliders live behind the adjustment icon.
Primary text/card 14.94:1, blue/card 5.84:1, white/blue 6.42:1. Native review grade B: strong reference
alignment, with platform icon/slider rendering and unsupported cloud sections as declared differences.

0.3.1 dark variant: separate Android night resource palette rather than inverting the light screen.
Surface #1C1C1C, cards #282828, text #F2F2F2, accent #BABFFE, on-accent #1E2250.
Native dark controls/dialogs and light system-bar icons accompany the palette. Four native dark screens
were inspected. Text/card, accent/card and on-accent/accent contrast all exceed 7:1.
