# E00-06: 署名用キーストア作成
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
APK配布用の署名鍵(keystore)を作成し、パスワードマネージャー等
安全な場所に保管する。

## 完了条件
keystoreファイルが作成され、パスワードとともに安全に保管されている。

## 前提: この鍵を失うと更新APKを配布できなくなる
ストア非公開の個人配布なので、鍵を紛失すると
「既存アプリをアンインストールして入れ直す(= ローカルDBが消える)」
しか手がなくなる。Driveが正のデータソースなので致命傷ではないが、
**keystoreとパスワードは必ずパスワードマネージャーに保管する。**

## 手順(パスワードを決める必要があるため手作業)
リポジトリ内には置かない。`.gitignore` で `*.jks` / `keystore.properties`
は既に除外済みだが、そもそもリポジトリ外(例: `E:\Engineering\_secrets\`)に
置くのが安全。

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore E:\Engineering\_secrets\asset-dashboard-release.jks `
  -alias asset-dashboard `
  -keyalg RSA -keysize 4096 -validity 10000
```

対話で聞かれるもの:
- keystoreパスワード(2回) ← パスワードマネージャーに保存
- 姓名(CN)など ← 個人利用なので適当でよい(例: CN=asset-dashboard)
- 鍵のパスワード ← keystoreと同じにして構わない(Enterで流用)

## 作成後にやること
`keystore.properties` をリポジトリ直下に作る(gitignore済み)。

```properties
storeFile=E:/Engineering/_secrets/asset-dashboard-release.jks
storePassword=<パスワード>
keyAlias=asset-dashboard
keyPassword=<パスワード>
```

これを読んで署名する設定はE08-01で入れた(使い捨ての鍵で確認済み)。
鍵を作ったら、Cloud ConsoleへのSHA-1の登録とスマホへの入れ方は
[docs/development.md](../../docs/development.md) の「リリース」。

## 実施 (2026-09-26)
本人が鍵を作り、パスワードを決めて保管した。

- 鍵: `E:\Engineering\_secrets\asset-dashboard-release.jks`(別名 `asset-dashboard`、RSA 4096、10000日)
- `keystore.properties` をリポジトリ直下に置いた(gitignore済みで、`git status` に出ないことを確かめた)
- Cloud Consoleに、リリース鍵のSHA-1でAndroidのクライアントを**もう1つ**作った。デバッグ用のクライアントは残した
  (Androidのクライアントは「パッケージ名 + SHA-1」1組しか持てず、書き換えるとデバッグ版で同期できなくなる)

### 踏んだこと
- **長いコマンドを貼ると壊れた**。PowerShellに複数行や長い1行を貼ると、改行が消えて1行につながったり
  (`&` が行の途中に来て `AmpersandNotAllowed`)、途中で切れたり(`-alias` の引数が無い、`@opt` が
  届かず `-keyalg` が無い)、先頭の `&` が落ちたりした。keytoolを呼ぶだけのスクリプトを鍵の置き場所に置き、
  `powershell -ExecutionPolicy Bypass -File <スクリプト>` の短い1行で動かして通った。スクリプトにパスワードは書かない
- keytoolの最後の確認(「…でよろしいですか。[いいえ]」)はEnterだけだと「いいえ」になり、名前の入力からやり直しになる。
  `はい` と入れる
- 証明書の名前(CN)は本人が入れたもの。リポジトリはPublicなので、ここには書かない

## 検証 (2026-09-26)
- 本体で `assembleRelease` が通り、`app-release.apk` ができた
- `apksigner verify --print-certs` で署名を検証した(v2方式、署名者1人)。証明書のSHA-1はデバッグ鍵のものと違い、
  本物のリリース鍵で署名されている

## 未検証のまま残したこと
- **Cloud Consoleに登録したSHA-1とAPKの証明書のSHA-1を、こちらでは突き合わせていない**(本人がkeytoolの出力を
  見て登録した)。食い違っていれば、リリース版の同期が `失敗: 10:` になって分かる
- リリース版でDriveと同期できるかは、スマホに入れるまで分からない(E00-04・E08-02)

## ステータス
完了 (2026-09-26)
