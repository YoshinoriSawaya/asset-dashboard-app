# E01-02: Driveフォルダ構成の作成
親: [E01: Google Drive連携基盤](../epics/E01-drive-integration.md)

## 概要
/資産アプリ/ 配下に inbox, processed, backup, corrections, logs の
5フォルダを作成する。初回起動時にフォルダが存在しなければ自動作成する
ロジックも含む。

## 完了条件
Drive上に5フォルダが存在し、アプリがそれぞれのfolder IDを取得できる。

## 設計の決定

### folder IDはキャッシュしない
毎回Driveに問い合わせて解決する。

ローカルにIDを持つと、Drive側でフォルダを移動・削除・作り直されたときに
古いIDを掴み続けて分かりにくい壊れ方をする。毎回引き直せば自己修復するし、
「Driveが正のデータソース」という原則にも素直。
コストは起動ごとに数回のAPI呼び出しが増えるだけなので、
実際に遅いと感じたらそのときキャッシュを足せばよい。

この判断のおかげで、E01-02の時点では保存層(DataStore/Room)の導入が要らない。

### 「探して、無ければ作る」を1メソッドにまとめる
`DriveApi.ensureFolder(name, parentId)` が検索と作成を兼ねる。
返り値に `created` を持たせて、新規作成したかどうかを呼び出し側に伝える
(初回起動時だけ「作成しました」と出したい、程度の用途)。

## 実装
- `drive/DriveApi.kt`
  - `findFolder(name, parentId)` ... Driveのクエリで検索。
    クエリはシングルクォート括りなので、名前に `'` や `\` が入っても
    壊れないようエスケープする。
  - `createFolder(name, parentId)` ... `mimeType` にフォルダを指定してPOST。
  - `ensureFolder(name, parentId)` ... 上2つを組み合わせる。
  - 失敗は `DriveException` で投げる。`httpCode` を持たせてあり、
    401(トークン失効)だけは `isUnauthorized` で区別できる。E01-13で使う。
- `drive/AppFolders.kt`
  - `AppFolders` ... 6つのIDを持つdata class。
  - `DriveFolderSetup.ensure(api)` ... ルートと5つの子を順に用意し、
    今回新規作成した名前の一覧も一緒に返す。

## 検証結果 (2026-09-25)
エミュレータ AssetDash_API37 で「Driveに接続」を押した結果。

1回目:
```
接続OK: (自分のGoogleアカウント)
作成: 資産アプリ, inbox, processed, backup, corrections, logs
```

2回目(冪等性の確認):
```
接続OK: (自分のGoogleアカウント)
フォルダは作成済み
```

2回目が「作成済み」を返したことは、5つが `資産アプリ` の**子として**
正しく作られたことの証拠でもある。もしルート直下に散らばっていたら、
親IDを指定した検索が失敗して重複作成されるはず。

## ステータス
完了 (2026-09-25)
