# E00: 開発環境構築

## 概要
Androidアプリ開発に必要なローカル環境を整える。ここが終わらないと
Claude Codeでコードを書いても実行・確認ができない。

## 子イシュー
- [x] [E00-01: プロジェクトフォルダの作成・git init](../tasks/E00-01-project-folder-setup.md)
- [x] [E00-02: Android Studioインストール](../tasks/E00-02-android-studio-install.md)
- [x] [E00-03: プロジェクト作成(Kotlin + Jetpack Compose)](../tasks/E00-03-project-creation.md)
- [ ] [E00-04: 実機デバッグ環境のセットアップ(USBデバッグ有効化)](../tasks/E00-04-device-debug-setup.md)
- [x] [E00-05: エミュレータのセットアップ(実機が使えない場面用)](../tasks/E00-05-emulator-setup.md)
- [ ] [E00-06: 署名用キーストア作成・安全な保管](../tasks/E00-06-keystore-creation.md)
- [x] [E00-07: Claude Codeとの連携確認(プロジェクトを開いて簡単な変更→ビルドできるか)](../tasks/E00-07-claude-code-integration.md)

## 完了条件
空のAndroidプロジェクトが実機またはエミュレータでビルド・起動できる状態。
Claude Codeでコードを編集し、その変更が反映されることも確認済み。

## 現状(2026-09-24)
CLIで完結できるものは全て完了。エミュレータ `AssetDash_API37` 上で
アプリが起動し、Claude Codeでの変更が反映されることまで確認済み。

残りは**本人の手を動かす必要があるもの**だけ:

- **E00-04**: スマホ側で開発者オプション→USBデバッグを有効にする
- **E00-06**: keystoreのパスワードを決めてパスワードマネージャーに保管する

どちらもE01以降の実装をブロックしない(E00-04は実機確認したくなった時、
E00-06はE08のリリース時に必要になる)。

### CLIから触るときの前提
`java` / `adb` / `sdkmanager` はいずれもPATHに入っていない。
`JAVA_HOME` にAndroid Studio同梱のJBRを指定して使う。

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

SDK操作は `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latestinndroid.exe`
(旧`sdkmanager`の後継)。

## 依存関係
なし(最初のエピック)
