# E02-01: 汎用スキーマ設計(Metric/Reminder/Goal)
親: [E02: ローカルデータモデル・キャッシュ基盤](../epics/E02-local-cache-and-model.md)

## 概要
NISA評価額・車貯金・口座残高などをMetric、点検通知やCSV催促を
Reminder、X万円目標などをGoalとして扱う共通スキーマを定義する。
item_type + 汎用value/unit/target/due_dateの形にする。

## 完了条件
Metric/Reminder/Goalそれぞれのフィールド定義がドキュメント化され、
E02-02のテーブル設計に反映できる状態。

## 設計の決定

### 「値」と「項目」を分ける
概要にある「item_type + value」を1テーブルに入れる形はやめた。
Metricの値は日付ごとに何百点もある時系列で、Goalの目標額は1つ。
性格の違うものを1行に押し込むと、どちらかが歪む。

| 層 | 中身 | 正のデータ | Roomのテーブル |
|----|------|-----------|---------------|
| 観測値 | CSVと補正から来る「いつ・いくら」 | Drive `backup/` + `corrections/` | `metric_point`, `bank_transaction` |
| 項目 | 何を表示・管理するか(名前・目標・期日) | Drive `settings/items.json` | `item` |
| 記録 | どのファイルを取り込んだか | Room(例外) | `ingested_file`(既存) |

「汎用」にするのは**項目**の側。値の側はすでにE01で
`MetricPoint`(metricKeyで横に増える)として汎用になっている。

### 項目の定義はDriveの `settings/` に置く
GoalやReminderは人がアプリで入力するもので、CSVからは作り直せない。
Roomにだけ置くと、アプリを入れ直した瞬間に消える
(CLAUDE.mdの「Driveが正のデータソース」に反する)。

置き場所を `corrections/` と同居させる案もあったが、新しいフォルダに分けた。
`backup/` はCSV由来で再取り込みすれば作り直せるが、`corrections/` と
`settings/` は人が入力したもので作り直せない。この2つも「数値の補正」と
「項目の設定」で中身が違うので、分けておけば何を消すと何が失われるかが
フォルダ名で分かる。

形式は `corrections.json` と同じ流儀にする: `formatVersion` 付きのJSONを
丸ごと書き戻す。読めない項目は落として読める分だけ使う。

### 項目は1テーブル、種類は `type` 列で分ける
Metric/Goal/Reminderごとに別テーブルにはしない。

- トップ画面(E03-01)は3種類を1つの一覧に並べる
- 詳細画面(E03-02)は共通テンプレートで、種類ごとに中身を切り替える
- 種類が増えても(CLAUDE.mdの「汎用スキーマで拡張する」)テーブルは増えない

行は汎用だが、コードでは `sealed interface Item`(`Metric` / `Goal` /
`Reminder`)に変換して扱う。種類ごとに要らない列はnullになるので、
「GoalなのにtargetYenが無い」のような不整合は変換のところで弾く
(落とさずスキップしてログ)。

### `item` の列

| 列 | 型 | METRIC | GOAL | REMINDER |
|----|----|--------|------|----------|
| `id` | String | `metric:<metricKey>` | UUID | UUID |
| `type` | enum | ○ | ○ | ○ |
| `name` | String | 表示名 | 表示名 | 表示名 |
| `metricKey` | String? | 表示する系列 | 進捗を測る系列 | − |
| `targetYen` | Long? | − | 目標額 | − |
| `dueDate` | LocalDate? | − | 期日(任意) | 次の期日 |
| `repeat` | enum? | − | − | `NONE` / `MONTHLY` / `YEARLY` |
| `sortOrder` | Int | ○ | ○ | ○ |
| `hidden` | Boolean | ○ | ○ | ○ |

`id` はDriveに書くので、入れ直しても変わらない値にする。
Metricだけ `metricKey` から決まる形にしてある理由は次節。

### Metric項目は自動で生える
資産推移CSVに列が増えたらMetricが1種類増える、というE01-04の性質を保つ。

- `metric_point` に新しい `metricKey` が現れたら、`name = metricKey` の
  METRIC項目を自動で作る
- `settings/items.json` に書くのは、**人が変えたもの**(名前を変えた、
  隠した、並べ替えた)だけ
- DBを作り直しても、「items.json + まだ載っていないmetricKeyの自動生成」で
  同じ一覧になる

`id` を `metric:<metricKey>` と決まった形にしたのは、自動で生えた項目を
後から編集してitems.jsonに書いても、同じ項目として扱えるようにするため。

`投資信託`→NISAのような**読み替えはしない**(CLAUDE.md)。
metricKeyは列名のままで、表示名を `name` で変える。

### 項目に「現在値」を持たせない
概要の「汎用value」は列にしない。Metricの現在値もGoalの進捗も、
表示するときに `metric_point` の最新値から計算する。
項目側に値を書くと、同じ数字が2か所にあって古いほうが残る。

### `unit` は持たない
今出てくる値は全部円で、コードも `valueYen: Long` で通している。
円以外(走行距離など)が要るタスクは今のところ無い
(E09-04の車の買い替え時期は「予測日」で、Metricではない)。

必要になってから足しても安い。Roomはキャッシュなので列を足すだけで
済み(`fallbackToDestructiveMigration`)、互換を気にするのは
`settings/items.json` だけで、JSONはキーが増えても古い読み手が壊れない。

### Goalの進捗は「系列の最新値 ÷ 目標額」だけ用意する
`metricKey` の最新値を `targetYen` で割る。これがE07-01・E07-03の
「車購入用にX万円」を満たす最小の形。

E07のv2タスクが足すものは、ここでは列を用意しない
(上の「後から足しても安い」が効く)。

| タスク | 足すもの |
|--------|---------|
| E07-06 | 目標額を支出実績から自動計算する(`targetYen` の代わりのルール) |
| E07-10 | 1つの口座を複数Goalで分け合う配分額 |
| E07-11 | 積み増しを始める時期 |

### 観測値のテーブル

**`metric_point`**: いつ・いくら**だった**か

| 列 | 型 | 説明 |
|----|----|------|
| `metricKey` | String | PK(1) |
| `date` | LocalDate | PK(2) |
| `valueYen` | Long | |
| `origin` | enum | `CSV` / `OVERRIDE` / `MANUAL` |

主キーを「項目 + 日付」にしたのは、重複検出(E01-11)と補正(E01-10)の
キーに揃えるため。同じ日・同じ項目の値は1つしかない。

`origin` は、補正を重ねた後でも「この点は手で直した」と画面(E03-04)で
示せるように残す。

**`bank_transaction`**: いつ・いくら**動いた**か

| 列 | 型 | 説明 |
|----|----|------|
| `dedupKey` | String | PK。`Deduplication.keyOf` の値 |
| `date` | LocalDate | |
| `description` | String | 摘要 |
| `withdrawal` | Long? | nullと0を区別する(E01-04) |
| `deposit` | Long? | |
| `balance` | Long? | |
| `memo` / `label` | String? | |
| `sourceFileId` | String | どのCSV(backup)から来たか |

主キーを重複検出のキーにすれば、同じ取引を2回入れようとしても
DBの制約で1件になる。明細を持つのは、月次の支出(E02-05)や、
生活防衛資金の目標額の自動計算(E07-06)に出金の履歴が要るため。

## 決めずに先へ回したこと

| 何を | なぜ今決めないか | どこで |
|------|-----------------|--------|
| 明細がどの口座のものか(`accountKey`) | CSVに口座を示す列が無い。ANSER系は合計行に口座番号があるが個人情報。口座が1つの今は要らない | 口座が増えたとき / E07-10 |
| 明細の残高をMetricにするか(E01-04の積み残し) | 口座の識別が前提になる | E02-03 |
| 「合計」列の扱い | 資産推移の `合計` は他の列の和。E10-01の「全Metric合算」をそのままやると二重計上になる | E10-01 |
| Reminderの「済み」の判定 | CSV催促は同期で自動的に済み、点検は手で済みにする、など種類で違う | E05 |
| 同期のたびにRoomを作り直すか、差分で入れるか | 取り込みの流れの設計 | E02-03 |

## 検証
設計のみで、コードは変えていない。

後続タスクの要求を1つずつ、どの列で満たすか突き合わせた。

| タスク | 使うもの | 足りるか |
|--------|---------|---------|
| E03-01 トップ画面 | `item` 一覧(`sortOrder`, `hidden`) | ○ |
| E03-02 詳細画面 | `type` で分岐、Metricは `metric_point` | ○ |
| E03-03 月次・年次 | `metric_point` | ○ |
| E03-04 手動補正 | `metric_point.origin` + corrections | ○ |
| E04-02 ウィジェットの色 | 項目ではなく同期状態(E02-04) | 対象外 |
| E05-02/05/06 各リマインダー | REMINDERの `dueDate` + `repeat` | ○ |
| E07-01/03 Goal登録 | GOALの `name` `metricKey` `targetYen` | ○ |
| E07-06/10/11 | 上の表のとおり後から足す | 列が足りない(想定内) |
| E10-01 純資産 | `metric_point` | ○(「合計」の扱いは要検討) |

実データで出ているmetricKeyは `合計` `預金・現金` `投資信託` `年金` の4つ
(E01-04の資産推移CSV)。どれも自動生成のMETRIC項目として扱える。

## 未実装のまま残したこと
- テーブル・Entity・DAOはE02-02
- `settings/` フォルダの作成(`AppFolders` とREADMEのフォルダ構成)は、
  items.jsonを読み書きする実装と一緒に入れる(E02-02か03)。
  作るだけ作って空のフォルダを置くことはしない

## ステータス
完了 (2026-09-25)
