# SoundConnect Android

Windows版のlibmdr通信エンジンをAndroidのBluetooth RFCOMMに移植した非公式アプリです。
Android 9（API 28）以降でインストールできる構成です。Sonyとは関係ありません。
実機の接続・操作確認が完了するまでは検証用として扱ってください。
Android 9エミュレータでの起動・通信記録の再生テスト5件は合格済みです。
詳細は [VALIDATION.md](VALIDATION.md) を参照してください。

## ビルド

Android Studio、JDK 17以降、Android SDK 35、NDK 28.2.13676358、CMake 3.31.6を使用します。
SDK/NDK/CMakeはAndroid StudioのSDK Managerからインストールできます。
Build Tools 36.0.0もインストールしてください。
Java 21でもGradleを実行できます。Node・Rust・Windows DLLは不要です。

```powershell
git submodule update --init --recursive
cd android
# Android Studioで開くか、local.propertiesにsdk.dirを設定してください。
.\gradlew.bat assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

ARM 32bit（armeabi-v7a）、ARM 64bit（arm64-v8a）、エミュレータ用x86_64を含みます。
初回はGradle、Androidビルドツール、fmt 12.1.0のダウンロードが必要です。
公開配布する場合は自分の鍵でrelease APKを署名してください。debug鍵のAPKは検証用です。

Windowsでは日本語を含まないパスにチェックアウトしてください。既存の保存先に日本語がある場合は、
空いているドライブへ `subst S: "プロジェクトの絶対パス"` で割り当て、`S:\android` でビルドできます。

## 使い方

1. AndroidのBluetooth設定でヘッドホンをペアリングします。
2. Sony Sound Connectなど、ヘッドホンを操作する他のアプリを閉じます。
3. アプリで「機器一覧を更新」を押します。Android 12以降では「付近のデバイス」を許可します。
4. 機器と通信方式を選び、「ヘッドホンに接続」を押します。
   WH-1000XM4以前はV1、XM5以降の対応機器はV2です。
5. ヘッドホンから取得した設定を操作します。非対応機能は表示しません。

機器の新規探索をせずペアリング済み一覧を使うため、位置情報の権限は要求しません。
API 28–30は従来のBluetooth権限、API 31以降は実行時のBLUETOOTH_CONNECT権限を使います。
権限を拒否した場合は機器一覧の更新で再試行できます。再表示されない場合はAndroidのアプリ設定から許可します。

## 実装済みの機能

- ペアリング済み機器の選択、接続・キャンセル・切断、最後に選んだ機器と通信方式の保存
- モデル名・ファームウェア・本体/左右/ケースのバッテリー表示
- ノイズキャンセリング、外音取り込み1～20、オフ、声にフォーカス
- 機器が広告したEQプリセット、各バンド、CLEAR BASS、DSEE
- Speak-to-Chat、再生/一時停止/曲送り、ヘッドホン音量
- 自動一時停止、音声ガイダンス、接続品質、電源オフ

## 制約と検証

- 接続の生存期間は画面表示中です。画面を離れると制御ソケットと通信エンジンを解放します。
  バックグラウンド常駐サービスは使用しません。
- BLE専用機器は非対応です。Classic RFCOMMのSonyサービスを持つ機器が必要です。
- マルチポイントの接続先編集、割当ボタン、音声ガイダンス言語、自動電源オフ設定などの
  詳細設定画面は未移植です。Windows音声設定・トレイ・Windows用自動更新は含みません。
- 対応モデルは固定したupstreamのlibmdrに依存します。すべてのSony製品を保証しません。
- エラー後は機器の接続・通信方式・他アプリの状態を確認して再接続してください。
- Android APIの静的検査は `gradlew lintDebug`、端末テストは `gradlew connectedDebugAndroidTest`。
  Android 9の端末/エミュレータと、実際のヘッドホン両方での検証が必要です。

通信は1本のワーカースレッドでlibmdrを駆動します。別の読み取りスレッドがソケットの
切断を検知し、サイズ制限付きバッファを通じてJNIに渡します。UIスレッドで接続待ちをしません。
外部HTTP通信やWindowsのバックエンドは使いません。

## ライセンス

このアプリとSonyHeadphonesClient/libmdrはMIT、fmtはMIT（任意の例外条項付き）です。
アプリ内の「ライセンスを表示」に同梱ライセンスを記載しています。
