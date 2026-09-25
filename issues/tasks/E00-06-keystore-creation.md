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

## ステータス
未着手(パスワードを決める必要があるため手作業)
