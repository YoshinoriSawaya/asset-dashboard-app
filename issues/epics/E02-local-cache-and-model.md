# E02: ローカルデータモデル・キャッシュ基盤

## 概要
Metric/Reminder/Goalの汎用スキーマを設計し、Drive取り込み結果を
ローカル(Room DB)にキャッシュする基盤を作る。E03(UI)・E04(ウィジェット)
はすべてこの上に乗る。

## 子イシュー
- [x] [E02-01: 汎用スキーマ設計(Metric/Reminder/Goal)](../tasks/E02-01-schema-design.md)
- [x] [E02-02: Room DBのテーブル定義](../tasks/E02-02-room-db-schema.md)
- [x] [E02-03: Drive取り込み結果→ローカルキャッシュ反映の同期処理](../tasks/E02-03-drive-to-cache-sync.md)
- [x] [E02-04: アプリ起動時の同期トリガー判定(前回同期から一定期間経過)](../tasks/E02-04-sync-trigger.md)
- [x] [E02-05: 月次・年次集計ロジック](../tasks/E02-05-summary-aggregation.md)
- [x] [E02-06: 書いた直後のbackupを落とせないときの読み直し](../tasks/E02-06-backup-download-retry.md)

## 完了条件
E01で取り込んだCSVデータが、Metric/Reminder/Goalの汎用スキーマに
沿ってローカルDBに保存され、月次・年次の集計値がいつでも計算できる
状態になっている。

## 依存関係
E01(Drive連携基盤)完了後に着手
