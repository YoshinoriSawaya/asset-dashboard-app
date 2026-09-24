# E01-09: 整形済みデータのbackup書き込み処理
親: [E01: Google Drive連携基盤](../epics/E01-drive-integration.md)

## 概要
正常に取り込み・整形されたデータをbackupフォルダにJSON等で保存し、
ローカルキャッシュが失われても復元できる状態にする。

## 完了条件
取り込みのたびにbackupフォルダの内容が最新化される。

## 設計の決定

### なぜprocessedだけでは足りないか
processedにあるのは**元のCSV**で、銀行ごとにバラバラの形をしている。
DBを失ったときにそこから作り直すには全部パースし直すことになるし、
**パーサーを直したら過去の結果が変わる**。

backupには**パース済みの正規化された形**を置く。これがあって初めて
「Driveから再構築できる」が本当に成立する(CLAUDE.mdの大原則)。

### 元ファイルごとに1つのJSON
全部を1ファイルにまとめると、1回の書き込み失敗で全履歴を失う。
元ファイル単位(`<元のファイル名>.json`)なら、壊れても壊れたぶんだけ。

同名があれば中身を差し替える。同じCSVを取り込み直したときに
ファイルが増えていかないようにするため(`DriveApi.putTextFile`)。

### formatVersionを入れておく
JSONの形を変えたときに、読む側(E06-02の復元)が気づけるようにする。
後から足すのは難しいので最初から入れる。

### nullの項目はキーごと出さない
`withdrawal: null` と `withdrawal: 0` は意味が違う(「出金なし」と
「出金0円」)。キーを落としておけば、読む側でnullと0を取り違えない。

### backupに書けなくても取り込みは失敗にしない
取り込み自体は成立しているので、画面には `backupに書けず` と付けて
先へ進む。次回の取り込みで書き直される。

## JSONの形
```json
{
  "formatVersion": 1,
  "sourceFileName": "meisai.csv",
  "sourceFileId": "1abc...",
  "adapterId": "年月日・お引出し・お預入れ形式",
  "kind": "transactions",
  "transactions": [
    { "date": "2026-09-01", "description": "給与", "deposit": 300000, "balance": 500000 }
  ]
}
```

`kind` は `transactions` か `metrics`。E01-04で分けた`ParsedData`の
2種類に対応する。

## 実装
- `drive/DriveApi` に `uploadTextFile` / `updateTextFile` / `putTextFile` /
  `findFile` を追加。アップロードはエンドポイントが別ホスト
  (`https://www.googleapis.com/upload/drive/v3`)なので注意。
- `drive/BackupWriter.kt` ... JSONの組み立て(`render`)と書き込み(`write`)。
  `render`を分けてあるのでテストから中身を直接確かめられる。

## 引っかかった点: テストでorg.jsonが動かない
`android.jar` に入っている `org.json` はスタブで、`JSONObject.put()` が
**nullを返す**。`testOptions.unitTests.isReturnDefaultValues = true`
(E01-04でLogのために入れた)と組み合わさって、例外ではなく
NullPointerExceptionになって分かりにくかった。

`testImplementation("org.json:json")` で本物の実装を入れて解決。

## 検証結果 (2026-09-25)
ユニットテスト5件PASS(JSONの形、nullの扱い、日本語、空データ)。

### Driveへの実書き込み
processedにあった3ファイルをinboxに戻して再同期し、確認した。

```
同期完了: 再取り込み3件
・資産推移月次 (10).csv   再取り込み: 資産推移形式
・meisai.csv              再取り込み: 年月日・お引出し・お預入れ形式
・20260925005806.csv      再取り込み: 取引名・取扱日付・金額形式 / 読めない行 1
```

3件とも`backupに書けず`が付かなかった(付くのは書き込みに失敗したとき)。
`BackupWriter`は失敗時のみ`Log.w`を出すが、警告も出ていない。

同じアップロード経路を使う`SyncLog`のほうは成功ログを出すようにしてあり、
`logsに 20260924-165649.log を書いた` が実際に記録された。
**Driveへのアップロードが実際に通ることはこれで確認できている。**

この同期はE01-11で入れた「inboxにあるものは記録があっても読み直す」
挙動(`再取り込み`)の確認も兼ねている。

## ステータス
完了 (2026-09-25)
