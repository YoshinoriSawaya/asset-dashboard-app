# E02-02: Room DBのテーブル定義
親: [E02: ローカルデータモデル・キャッシュ基盤](../epics/E02-local-cache-and-model.md)

## 概要
E02-01のスキーマをRoom DBのEntity/DAOとして実装する。

## 完了条件
Metric/Reminder/Goalのテーブルが作成され、CRUD操作が動作する。

## 作ったもの

| テーブル | Entity / DAO | 中身 |
|---------|-------------|------|
| `item` | `ItemEntity` / `ItemDao` | Metric/Goal/Reminderの項目(1テーブル、`type`で分ける) |
| `metric_point` | `MetricPointEntity` / `MetricPointDao` | いつ・いくらだったか |
| `bank_transaction` | `BankTransactionEntity` / `BankTransactionDao` | いつ・いくら動いたか |

DBのバージョンは1→2。スキーマは `app/schemas/.../2.json` に出力される。

## 設計の決定

### 行は汎用、型はKotlin側で守る
`ItemEntity` は全種類の列を持つ汎用の行で、コードからは
`toItem()` で `sealed interface Item`(`Metric` / `Goal` / `Reminder`)に
変換して使う。種類に必要な列が欠けた行は例外ではなく`null`を返し、
呼ぶ側でスキップさせる(items.jsonを手で壊しても一覧全体は死なない)。

`Goal.metricKey` はnullを許した。Goalを先に作って、進捗を測る系列は後で
紐づける順番がありうるため(その間は進捗不明として表示する)。

### 重複の扱いをDBの制約に任せる
E01-11の判断(取引は先勝ち、Metricは後勝ち)を、そのままDBの挿入戦略にした。

| テーブル | 主キー | 衝突したら |
|---------|-------|-----------|
| `bank_transaction` | `Deduplication.keyOf` の値 | `IGNORE`(先勝ち) |
| `metric_point` | `metricKey` + `date` | `REPLACE`(後勝ち) |

取り込み側で既存のキーを全部読んで突き合わせる必要が無くなる。
E02-03はパース結果を変換して流し込むだけでよい。

### 日付はISO文字列で持つ
`2026-09-25` の形。epoch日数の整数より、sqlite3で覗いたときに読める。
文字列のままでも大小比較が日付順になるので、`BETWEEN` がそのまま使える。

### マイグレーションはAutoMigrationで書いた
`fallbackToDestructiveMigration` だけだと、バージョンを上げた瞬間に
`ingested_file` も消える。Driveから作り直せない唯一のテーブルなので、
テーブルを足すだけの今回は `AutoMigration(from = 1, to = 2)` で残す。

`fallbackToDestructiveMigration` は残してある。マイグレーションを
書くのが割に合わない変更(列の型を変えるなど)では、これまでどおり
作り直してDriveから入れ直す。

### Flowは用意していない
画面から監視する形(`Flow<List<...>>`)は、使う画面(E03)を作るときに足す。
今はCRUDとE02-03が使う読み書きだけ。

## 検証 (2026-09-25)

**JVMユニットテスト(79件、うち追加6件)**: `ItemTest`
- 3種類とも行⇄型を往復して同じものに戻る
- 種類に必要な列が欠けた行はnullになる
- 日付の変換

**エミュレータでのインストルメントテスト(6件)**: 本物のSQLiteで確認

| テスト | 確かめたこと |
|--------|-------------|
| `AppDatabaseTest` 4件 | 3テーブルのCRUD。取引の先勝ち、Metricの後勝ち、期間指定が両端を含む |
| `MigrationTest.v1からv2で...` | v1のDBにある`ingested_file`がv2でも残り、新テーブルが空でできる |
| `MigrationTest.アプリの設定で...` | 本番の`AppDatabase.get()`で開いても残る |

最後の1件は、テストが本当に効いているかを確かめるため、
`AutoMigration` を外して走らせた(対照実験)。
`expected:<[meisai.csv]> but was:<[]>` で落ち、
**外すと記録が黙って消える**ことと、テストがそれを検出できることを確認した。

## 踏んだこと
- `room-testing` の `MigrationTestHelper` が `AbstractMethodError`
  (`kotlinx.serialization`)で落ちた。アプリ側が間接依存で1.7.3に解決され、
  AGPがテストもアプリと同じ版に固定するため。`constraints` で
  アプリ側を1.8.1に上げて揃えた。
- `connectedDebugAndroidTest` は**終わるとアプリをアンインストールする**。
  E01の実データ確認で溜まっていたエミュレータの`ingested_file`の記録は
  これで消えた。processedのファイルはDriveに残っているので実害は無い
  (E02-03で読み直す予定だったもの)。手順は
  [docs/development.md](../../docs/development.md) に書いた。

## 未検証・未実装のまま残したこと
- **実際のアプリをv1からv2に上げる操作**はしていない。DBが開くのは
  Drive同期のときだけで、エミュレータのアプリは上記のとおり消えていた。
  代わりに本番のビルダーで開くテストで確かめている。
- パース結果をこれらのテーブルへ書く処理はE02-03。
- `settings/items.json` の読み書きとフォルダの作成もE02-03以降
  (E02-01の「未実装のまま残したこと」)。

## ステータス
完了 (2026-09-25)
