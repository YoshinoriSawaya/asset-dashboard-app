# E00-02: Android Studioインストール
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
Android Studio最新安定版をインストールする。JDKは同梱のものを使用。

## 完了条件
Android Studioが起動し、SDK Managerで最新のAndroid SDKが導入されている。

## 実施内容
Android Studio本体は既にインストール済みだった(`C:\Program Files\Android\Android Studio`、
バージョン 2026.1.4 / AI-261.26222.65、同梱JBRは OpenJDK 25.0.3)。
一方SDKは古い状態(platformはandroid-32のみ、build-toolsは32.1.0-rc1まで)
だったため、以下をCLIで追加した。

- `cmdline-tools/latest` (23.0.0) ... 未導入だったので新規インストール
- `platforms/android-37.2` ... 最新安定版プラットフォーム
- `build-tools/37.0.0` ... 最新安定版ビルドツール
- `platform-tools` (37.0.1) / `emulator` (37.1.11) は既に最新だった

### CLIの注意点
`sdkmanager` は非推奨になり、`cmdline-tools/latest/bin/android.exe` に
統合された。パッケージIDの区切りは `;` ではなく `/` を使う。

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\android.exe" sdk install "platforms/android-37.2"
```

また `java` / `adb` はPATHに入っていないため、CLIから叩くときは
`JAVA_HOME` にAndroid Studio同梱のJBRを指定する必要がある。

## ステータス
完了 (2026-09-24)
