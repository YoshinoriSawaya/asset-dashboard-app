# E00-07: Claude Codeとの連携確認
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
作成したプロジェクトに対してClaude Codeで簡単な変更(画面のテキスト変更等)
を加え、Android Studioでビルド・実行できることを確認する。

## 完了条件
Claude Code経由の変更が実機/エミュレータに反映されることを確認できた。

## 実施内容
E00-05で作ったエミュレータ `AssetDash_API37` に対して、
「編集 → ビルド → インストール → 画面確認」の一周をCLIだけで実行した。

1. `./gradlew installDebug` でAPKをインストール、`am start` で起動
2. Claude Codeで `app/build.gradle.kts` に `buildConfig = true` を追加し、
   `MainActivity.kt` に `BuildConfig.VERSION_NAME` を表示する行を追加
3. 再ビルド・再インストール後、画面に `v0.1.0 (debug)` が出ることを確認

単なる文字列差し替えではなくBuildConfigの自動生成を経由させたので、
「Kotlinコンパイル + AGPのコード生成 + リソース処理」まで通ったことになる。

### 一周まわすコマンド
PowerShellからだと `JAVA_HOME` とパスに注意が必要。

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$proj = "E:\Engineering\asset-dashboard-app\asset-dashboard-app"
$adb  = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

& "$proj\gradlew.bat" -p $proj installDebug
& $adb shell am force-stop com.yswy.assetdashboard
& $adb shell am start -n "com.yswy.assetdashboard/.MainActivity"
& $adb exec-out screencap -p > screen.png   # 画面の確認はスクショが手軽
```

`-p $proj` を付けないとカレントディレクトリ次第で `gradlew` が見つからない。
`adb exec-out screencap -p` はClaude Code側から画面を直接確認できるので、
UI実装(E03/E04)のときもこの手順が使える。

## ステータス
完了 (2026-09-24)
