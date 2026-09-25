# 開発手順

このマシン(Windows)での手順。久しぶりに触るときはここを見る。

## 前提: PATHに何も通っていない

`java` `adb` `gradle` `sdkmanager` はいずれもPATHに無い。
毎回フルパスか環境変数で指定する。

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$proj = "E:\Engineering\asset-dashboard-app\asset-dashboard-app"
$adb  = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$sdk  = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin"
```

`-p $proj` を付けないとカレントディレクトリ次第で `gradlew` が見つからない。

## ビルドとテスト

```powershell
& "$proj\gradlew.bat" -p $proj testDebugUnitTest   # ユニットテスト
& "$proj\gradlew.bat" -p $proj assembleDebug       # APKだけ作る
& "$proj\gradlew.bat" -p $proj installDebug        # 端末に入れる
```

テストが落ちたときの詳細は `app/build/reports/tests/testDebugUnitTest/`
にHTMLで出る。

### DBのテスト(エミュレータが要る)
Roomは本物のSQLiteが要るので、`app/src/androidTest/` に置いてエミュレータで回す。

```powershell
& "$proj\gradlew.bat" -p $proj connectedDebugAndroidTest `
  "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

**最後の `-P` を必ず付ける。** 付けないと、終わったときにアプリを
アンインストールし、エミュレータのDB(`ingested_file`の記録)も
Googleの認可も消える。

結果は `app/build/reports/androidTests/connected/debug/`。
1クラスだけ回すなら
`"-Pandroid.testInstrumentationRunnerArguments.class=<クラスのFQCN>"` を足す。

## エミュレータ

AVD名は `AssetDash_API37`(Pixel 9 / API 37.2 / google_apis x86_64)。

```powershell
# 起動しっぱなしにする(こちらを使う)
Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" `
  -ArgumentList "-avd","AssetDash_API37"

# 起動完了を待つ
& $adb wait-for-device
& $adb shell getprop sys.boot_completed   # 1 になれば起動完了
```

`android.exe emulator start` は**使わない**。エミュレータを子プロセスと
して起動し、ブート完了を報告した時点で終了するので、コマンドが終わると
エミュレータも一緒に落ちる。

`google_apis`(Play Store入りでない)イメージを選んであるので `adb root`
が使える。Play Store版だとrootが取れず、DBを覗けない。

## 画面を見る

```powershell
& $adb shell am force-stop com.yswy.assetdashboard
& $adb shell am start -n "com.yswy.assetdashboard/.MainActivity"

# スクリーンショット
& $adb exec-out screencap -p > screen.png

# 画面のテキストだけ取る(自動確認に便利)
& $adb shell uiautomator dump /sdcard/ui.xml
$xml = (& $adb shell cat /sdcard/ui.xml) -join ""
[regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
```

ボタンを押すには座標が要る。上の dump に `bounds` が入っているので
そこから中心を計算する。

## ログを見る

取り込みの流れはタグで追える。

```powershell
& $adb logcat -c                                       # 消してから
& $adb logcat -d -s CsvIngest InboxSync InboxScanner   # 取り込みの流れ
& $adb logcat -d -s DriveSession SyncLog BackupWriter  # 認可と書き込み
& $adb logcat -d -b crash                              # クラッシュ
```

「inboxに置いたのに拾われない」ときは `InboxScanner` を見る。
判定に使った id と modifiedTime をそのまま出している。

## ローカルDBを覗く

```powershell
& $adb root
& $adb shell sqlite3 /data/data/com.yswy.assetdashboard/databases/asset-dashboard.db `
  '"SELECT * FROM ingested_file;"'
```

**取り込みをやり直したいとき**は記録を消す。

```powershell
& $adb shell am force-stop com.yswy.assetdashboard
& $adb shell sqlite3 /data/data/com.yswy.assetdashboard/databases/asset-dashboard.db `
  '"DELETE FROM ingested_file;"'
```

ただしファイルが `processed` に移動済みなら、Drive側で `inbox` へ
戻す必要もある。inboxにあるファイルは記録があっても読み直す作りなので、
戻すだけでも再取り込みされる。

## オフラインの挙動を試す

```powershell
& $adb shell cmd connectivity airplane-mode enable
& $adb shell svc wifi disable
& $adb shell svc data disable
# 戻すときは enable / disable を逆に
```

## SDKを更新する

`sdkmanager` は非推奨になり `android.exe` に統合された。
**パッケージIDの区切りがツールで違う**ので注意。

```powershell
& "$sdk\android.exe" sdk list
& "$sdk\android.exe" sdk install "platforms/android-37.2"   # スラッシュ区切り
& "$sdk\avdmanager.bat" create avd -n NAME `
    -k "system-images;android-37.2;google_apis_ps16k;x86_64" -d pixel_9  # セミコロン区切り
```

`android.exe` は成功しても終了コード9を返すことがある。
成否は終了コードではなく、実際にファイルが増えたかで判断する。

## Google Cloud Console まわり

OAuthクライアントIDは「パッケージ名 + 署名SHA-1」に紐づく。

| 項目 | 値 |
|------|-----|
| パッケージ名 | `com.yswy.assetdashboard` |
| デバッグ用SHA-1 | `53:C8:05:43:56:58:FA:3D:3A:1F:AF:67:C3:BA:C3:40:A1:92:40:DD` |

```powershell
# SHA-1を取り直す
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android
```

**リリースAPKは署名が変わる。** E00-06でキーストアを作ったら、その
SHA-1でもクライアントIDを登録する必要がある。忘れるとデバッグでは
動くのにリリースだけ認証が通らない。

Console側の設定手順(どのスコープを選んだか、テストユーザーの登録など)は
[issues/tasks/E01-01-oauth-setup.md](../issues/tasks/E01-01-oauth-setup.md)。
ここに載せているのは「すぐ必要になる値」だけ。

## 踏んだ落とし穴

| 症状 | 原因 |
|------|------|
| `kotlin.android` プラグインでビルド失敗 | AGP 9 はKotlinサポートを内蔵した。併用できない |
| テストが `Log not mocked` で落ちる | アダプターからログを呼んでいた。純粋関数にする |
| テストで `JSONObject.put` がnullを返す | android.jarのorg.jsonはスタブ。`testImplementation("org.json:json")` を入れる |
| 圏外で「同意が必要」と出る | 認可より先にネットワークを見る |
| `adb shell` のパスが化ける | Git Bashが `/sdcard/...` を変換する。PowerShellを使う |
| ドキュメント中のパスが壊れる | Pythonで書くとき `\a` がBEL文字になる |
| エミュレータのアプリとDBが消えた | `connectedDebugAndroidTest` は終了時にアンインストールする。上の `-P` を付ける |
| `MigrationTestHelper` が `AbstractMethodError`(kotlinx.serialization) | AGPがテストをアプリと同じ版に固定する。アプリ側を `constraints` で上げる |
| ウィジェットをタップしても開かない | `am force-stop` がPendingIntentを取り消した。アプリを開けば直る。確認で強制停止を使わない |
| ウィジェットの色が同期しても変わらない | Glanceのセッション中は `provideGlance` が呼び直されない。判定は `updateAppWidgetState` に書いて `currentState` で読む |
| ホームに置いたウィジェットが見つからない | HOMEキーは1ページ目に戻る。置いたページ(2ページ目など)へスワイプする |
