# E01-01: Google Cloud Console設定・OAuth認証
親: [E01: Google Drive連携基盤](../epics/E01-drive-integration.md)

## 概要
Google Cloud ConsoleでプロジェクトとDrive APIを有効化し、Androidアプリ用の
OAuth 2.0クライアントIDを発行する。アプリから自分のGoogleアカウントで
認証できる状態にする。

## 完了条件
アプリ起動時にGoogleアカウントでログインし、Drive APIへのアクセス許可が
取得できる。

## 方式の決定

### AuthorizationClientを使う(GoogleSignInClientは使わない)
旧 `GoogleSignInClient` は非推奨。現行は認証(誰か)と認可(何を許可するか)が
分離していて、今回必要なのは**認可だけ**なので
`Identity.getAuthorizationClient()` を使う。

サーバーを持たないのでrefresh tokenは扱わない。Play Servicesが端末内で
認可状態を保持していて、許可済みならユーザー操作なしでアクセストークン
(寿命1時間程度)を返す。失効したらもう一度 `authorize()` を呼ぶだけ。
`requestOfflineAccess()` とWebクライアントIDはサーバー側で使う場合のみ
必要なので、今回は不要。

**アプリにクライアントIDや秘密鍵を埋め込む必要はない。**
Console側で「パッケージ名 + 署名証明書のSHA-1」に紐づくAndroid用
クライアントIDを作れば、Play Servicesが署名を照合する。
つまりこのタスクでリポジトリに入る秘密情報はゼロ。

### スコープはフルの `drive` を使う
| 候補 | 判定 |
|------|------|
| `drive.file` | **不可**。アプリが作成したファイルしか見えない。Driveの画面から手でinboxに置いたCSVが見えないので、「雑に放り込むだけ」という大原則が壊れる |
| `drive.readonly` | 不可。processedへの移動やbackup書き込みができない |
| `drive` (フル) | **これを使う**。読み書き・移動が全部できる |

フル `drive` はGoogleの分類上「制限付きスコープ」で、一般公開アプリなら
審査とセキュリティ評価が必要になる。ただし**自分だけがテストユーザーの
個人アプリなら審査は不要**。ストア非公開・個人利用のみという前提と整合する。

## 手順(Google Cloud Consoleでの操作が必要)

### 1. プロジェクト作成とAPI有効化
1. https://console.cloud.google.com/ で新規プロジェクトを作成(名前は任意、例: `asset-dashboard`)
2. 「APIとサービス」→「ライブラリ」→ **Google Drive API** を検索して有効化

### 2. OAuth同意画面
1. 「APIとサービス」→「OAuth同意画面」
2. User Type は **外部(External)** ※個人のGmailアカウントだと内部は選べない
3. アプリ名・サポートメール・デベロッパー連絡先を埋める(個人用なので適当でよい)
4. スコープの追加で `https://www.googleapis.com/auth/drive` を追加
5. **テストユーザーに自分のGoogleアカウントを追加する**(ここを忘れると弾かれる)

### 3. Android用OAuthクライアントID
「APIとサービス」→「認証情報」→「認証情報を作成」→「OAuthクライアントID」
→ アプリケーションの種類: **Android**

| 項目 | 値 |
|------|-----|
| パッケージ名 | `com.yswy.assetdashboard` |
| SHA-1(デバッグ用) | `53:C8:05:43:56:58:FA:3D:3A:1F:AF:67:C3:BA:C3:40:A1:92:40:DD` |

デバッグ用SHA-1は `~/.android/debug.keystore` のもの。再取得するなら:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android
```

**リリース用APKでは署名が変わるので、E00-06でキーストアを作ったら
そのSHA-1でもう1つクライアントIDを登録する必要がある。**
これを忘れるとデバッグでは動くのにリリースAPKだけ認証が通らない、
という分かりにくい詰まり方をする。

### 4. 公開ステータスの注意(7日問題)
同意画面を「テスト中」のままにすると、Googleが発行するrefresh tokenは
7日で失効する。今回は端末内完結でrefresh tokenを使わないため直接の影響は
小さいはずだが、認可自体が定期的に切れて再同意を求められる可能性がある。

毎週再同意が必要で煩わしければ、同意画面の公開ステータスを
**「本番環境」に変更する**(審査は通っていないので初回に「確認されていない
アプリ」の警告が出るが、詳細→続行で進める)。

## アプリ側の実装(完了済み)
- `drive/DriveAuth.kt` ... `AuthorizationClient` でアクセストークンを取得。
  結果は `Authorized` / `ConsentRequired` / `Failed` の3つに正規化して、
  失敗しても落とさずUIに理由を出す。
- `drive/DriveApi.kt` ... Drive REST APIの薄いラッパー。
  `google-api-services-drive` は依存が重いのでOkHttpで直接叩く。
  現時点は疎通確認用の `about` のみ。
- `MainActivity.kt` ... 「Driveに接続」ボタン。押すと認可 → `about` を呼び、
  成功すると `接続OK: <メールアドレス>` と表示する。

## 検証手順(Console設定が終わってから)
エミュレータの場合、**先に設定アプリからGoogleアカウントを追加しておく**
(google_apisイメージなのでGMSは入っている)。

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$proj = "E:\Engineering\asset-dashboard-app\asset-dashboard-app"
& "$proj\gradlew.bat" -p $proj installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n "com.yswy.assetdashboard/.MainActivity"
```

「Driveに接続」を押して `接続OK: <自分のメールアドレス>` が出れば完了条件を満たす。

### 失敗したときの見分け方
| 表示 | 原因 |
|------|------|
| `失敗: 10:` (DEVELOPER_ERROR) | パッケージ名かSHA-1の登録ミス。クライアントIDの設定を見直す |
| `失敗: 16:` | テストユーザーに自分のアカウントが入っていない |
| `トークンは取れたがAPIで失敗: HTTP 403` | Drive APIが有効化されていない、またはスコープ不足 |

## 検証結果 (2026-09-25)
エミュレータ AssetDash_API37 で確認。

1. 「Driveに接続」→ 端末にGoogleアカウントが無い状態だったので、
   `AuthorizationActivity` → `PreAddAccountActivity` → `MinuteMaidActivity`
   とPlay Services側のサインインに遷移した。
   ここで `DEVELOPER_ERROR`(コード10)が出なかったので、
   パッケージ名とSHA-1の登録が正しいことが確認できた。
2. サインインと同意の後、アプリに戻って `接続OK: (自分のGoogleアカウント)` を表示。
   認可 → アクセストークン取得 → Drive APIの `about` 実呼び出しまで一周した。
3. `am force-stop` してから再度起動しボタンを押すと、**同意画面を挟まずに**
   `接続OK` が返った。Play Servicesが認可状態を保持しているため、
   ユーザー操作なしでトークンを取り直せる。
   「アプリを開いたタイミングで同期する」設計の前提が満たされている。

なお同意画面の公開ステータスは「テスト中」のまま。認可が切れて再同意を
求められる頻度は運用してみないと分からないので、煩わしくなったら
「本番環境」に切り替える(上記「7日問題」参照)。

## ステータス
完了 (2026-09-25)
