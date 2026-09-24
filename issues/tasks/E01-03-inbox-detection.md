# E01-03: inboxファイル一覧取得・新規判定ロジック
親: [E01: Google Drive連携基盤](../epics/E01-drive-integration.md)

## 概要
Drive APIでinbox内のファイル一覧(id, modifiedTime)を取得し、前回取り込んだ
ファイルid一覧と比較して新規/未処理ファイルを判定する。

## 完了条件
inboxに新しいCSVを置くと、アプリがそれを「未処理」として検知できる。

## 設計の決定

### ここで初めて保存層(Room)を入れた
E01-02のfolder IDはDriveから引き直せるので永続化しなかったが、
「どのファイルを取り込み済みか」はDriveを見ても分からないので保存が要る。

本来E02でRoomのスキーマを設計する順序だが、これを後回しにすると
E01の完了条件(CSVを置いて起動すると取り込まれる)が閉じない。
そこで**取り込み済みファイルの記録テーブルだけ先に作り**、
Metric/Reminder/GoalはE02で足す。

- KSPは2.3.0から採番がKotlin版数と切り離され独立semverになっている。
  Kotlin 2.4.20に対して `2.3.12` を使う。
- Roomのスキーマ出力はGradleプラグイン(`androidx.room`)で設定した。
  ksp引数 `room.schemaLocation` で渡す方式はconfiguration cacheと
  相性が悪く、このプロジェクトは `org.gradle.configuration-cache=true`
  を有効にしているため。
- AGP 9 + KSP + Roomプラグインの組み合わせはビルドが通ることを確認済み。

### 判定は id と modifiedTime の2本立て
| 状況 | 判定 |
|------|------|
| 記録に無いid | 未取り込み |
| 記録はあるがmodifiedTimeが違う | 未取り込み(Drive上で上書き更新された) |
| 記録があってmodifiedTimeも同じ | 取り込み済み |

**ファイル名は判定に使わない。**「ファイル名も気にせず放り込む」のが
前提なので名前は当てにならない。

### 失敗したファイルは記録しない
取り込みに失敗したファイルを記録すると、次回から「処理済み」として
スキップしてしまう。失敗時はinboxに残してlogsに理由を書き、
次回もう一度試す(CLAUDE.mdの「落ちるよりスキップしてログ」)。

### 一覧は全ページ取る
Driveの一覧APIはページングする。inboxに何百件も溜まる想定はないが、
打ち切ると「なぜか取り込まれないファイルがある」という分かりにくい
不具合になるので、nextPageTokenが尽きるまで回す。

## 実装
- `data/IngestedFile.kt` ... エンティティとDAO。
- `data/AppDatabase.kt` ... Room本体。キャッシュなので
  `fallbackToDestructiveMigration` を有効にしてある
  (マイグレーションを書くより作り直してDriveから入れ直すほうが安全で速い)。
- `drive/DriveApi.listFiles(parentId)` ... ページング対応の一覧取得。
- `drive/InboxScanner.kt` ... 未取り込み/取り込み済みに選り分ける。
  `alreadyIngested` は「取り込み済みなのにinboxに残っている」ファイルで、
  E01-07のprocessedへの移動が失敗した形跡を表す。

## 検証結果 (2026-09-25)
inboxに実ファイル `資産推移月次 (10).csv` を置いて、判定の3分岐すべてを確認した。
スペース・括弧・日本語を含む名前なので、クエリのエスケープの確認にもなった。

| # | 状況 | 期待 | 結果 |
|---|------|------|------|
| 0 | inboxが空 | 0件 | `未取り込み 0件` |
| 1 | 記録に無いid | 未取り込み | `未取り込み 1件` + ファイル名表示 |
| 2 | 記録あり・modifiedTime一致 | 取り込み済み | `未取り込み 0件 / 取り込み済みが残留 1件` |
| 3 | 記録あり・modifiedTime不一致 | 未取り込み | `未取り込み 1件` |

2と3は、`adb root` + `sqlite3` でDBに直接レコードを入れて確認した。
3は記録側のmodifiedTimeを古い値に書き換えることで「Drive上で上書き
更新された」状況を作っている。つまり**比較ロジックが正しいことは
確認できたが、Driveが実際に上書き時にmodifiedTimeを更新するかは
この方法では確認していない**(Drive APIの仕様上は更新される)。

確認に使ったレコードは削除済み。

inboxが空のときRoomのDBファイルは作られない。ファイルが1件でもあると
DAOクエリが走って初めて作られる。

### デバッグの足がかり
「置いたのに拾われない」を追えるよう、`InboxScanner` が判定に使った値を
そのままログに出す。

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d -s InboxScanner
```

```
InboxScanner: inbox=1件 未取り込み=1件 残留=0件
InboxScanner: 未取り込み: id=1UD06... modified=2026-09-24T15:44:59.422Z name=資産推移月次 (10).csv
```

DBを直接覗きたいときは(google_apisイメージなのでrootが取れる):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb root
& $adb shell sqlite3 /data/data/com.yswy.assetdashboard/databases/asset-dashboard.db '"SELECT * FROM ingested_file;"'
```

## ステータス
完了 (2026-09-25)
