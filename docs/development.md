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

### 知らない形のCSVに対応する
アダプターに当たらなかったCSVは、デバッグビルドで `CsvIngest` に**形**が出る。
文字は出さず、セルを種類に置き換えてある(`D` 日付 / `N` 数字 / `M` 伏せ字 / `T5` 5文字 /
`T7様` 様で終わる / `-` 空。中身の意味は `csv/CsvShape.kt`)。

```
L1 (3) T7様|M|T14          ← 1行目に氏名とカード番号
L2 (7) D|T9|N|N|N|N|-
  …×7 (7) D|T16|N|N|N|N|-   ← 途中は形ごとの行数
L47 (7) -|-|-|-|-|N|-        ← 合計の行
```

実物のCSVをリポジトリにも会話にも出さずに、これだけ見てアダプターを書ける(E01-14で使った)。
一度フォールバックで取り込まれたファイルは `processed` に移るので、アダプターを足したら
Driveで `inbox` に戻して同期し直す(`backup` の同名のJSONが差し替わる)。

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

## リリース(スマホに入れる)

判断の経緯は [E08-01](../issues/tasks/E08-01-signed-apk-build.md) /
[E08-02](../issues/tasks/E08-02-install-update-flow.md) /
[E08-03](../issues/tasks/E08-03-version-history.md)。

### 最初の1回だけ
1. **署名鍵を作る**([E00-06](../issues/tasks/E00-06-keystore-creation.md))。
   鍵とパスワードはパスワードマネージャーへ。リポジトリ直下に
   `keystore.properties` を置く(gitには入らない)
2. **リリース鍵のSHA-1をGoogle Cloud Consoleに登録する**。
   しないと、リリース版で「Driveと同期」が `失敗: 10:`(DEVELOPER_ERROR)になる
   ```powershell
   & "$env:JAVA_HOME\bin\keytool.exe" -list -v `
     -keystore E:\Engineering\_secrets\asset-dashboard-release.jks -alias asset-dashboard
   ```
   出てきた `SHA1:` で、Androidのクライアントを**もう1つ**作る
   (パッケージ名 `com.yswy.assetdashboard`)。手順はデバッグ用と同じ
   ([E01-01](../issues/tasks/E01-01-oauth-setup.md))
3. **スマホに入っているデバッグ版を消す**。デバッグ版とリリース版は署名が違い、
   上書きできない(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)。消すとスマホの
   キャッシュも消えるが、同期すればDriveから戻る。Googleの同意はやり直し

### 毎回
```powershell
# 1. app/build.gradle.kts の appVersion を上げ、CHANGELOG.md に書く
# 2. ビルド(keystore.properties が無ければ理由を出して止まる)
& "$proj\gradlew.bat" -p $proj assembleRelease
#    → app\build\outputs\apk\release\app-release.apk
# 3. スマホに入れる(USBデバッグ。E00-04)
& $adb install -r "$proj\app\build\outputs\apk\release\app-release.apk"
```

USBでつなげないときは、APKをDriveに上げてスマホで開いてもよい
(Driveアプリに「不明なアプリのインストール」を許可する必要がある)。
**`資産アプリ/inbox` には置かない**。CSVとして読もうとして失敗扱いになる。

`-r` の上書きでスマホのデータは残る。バージョンを下げると入らない
(`INSTALL_FAILED_VERSION_DOWNGRADE`)。

## 通知を試す(E05)

通知は1日1回、朝9時ごろのアラームで出る。待たずに試すには、デバッグ版の
トップの一番下にある「(デバッグ)今すぐ通知を確認」を押す。

```powershell
# 出た通知の中身
& $adb shell dumpsys notification --noredact | Select-String "android.title=|android.text="
# 予約されているアラーム(1件あればよい)
& $adb shell dumpsys alarm | Select-String "RTC_WAKEUP #\d+: Alarm\{[^}]*com.yswy.assetdashboard\}"
```

「最後に出した日」の記録は `shared_prefs/notify.xml`。消すと同じ通知がもう一度出る。

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
| adbでタップしたのにボタンが反応しない | キーボードがボタンを覆っていて、キーボードに当たっている。`dumpsys input_method` の `mInputShown` を見て、出ていればBACKで閉じてから押す(出ていないときのBACKは画面を閉じる) |
| インストルメントテストが `Expecting '('` でコンパイルできない | テスト名(関数名)に「、」を入れた。Kotlinの識別子に使えない |
| adbでスイッチを押したのに変わらない | Composeのスイッチやチップはテキストを持たない `android.view.View`(`checkable="true"`)。見出しの文字の位置ではなく、そのノードの位置を押す |
| アラームの予約が消えた | `am force-stop` はアプリのアラームも消す。更新・再起動・アプリを開くと予約し直される |
| 再起動したのにアラームが予約されない | `BOOT_COMPLETED` は起動から1分ほど遅れて届く。待ってから見る |
| adbでロックを試したい | `adb shell locksettings set-pin 1234` で端末にPINを付ける。消すのは `locksettings clear --old 1234` |
| PowerShellで自作の関数 `Where` が動かない | `where` は `Where-Object` の別名。別の名前にする |
| リリース版を入れると `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | デバッグ版と署名が違う。デバッグ版を消してから入れる |
| リリースのビルドで `Configuration cache problems` | `doFirst` の中でスクリプトの変数を直接つかんでいた。ローカルに写してから使う |
