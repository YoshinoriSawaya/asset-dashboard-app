# E02-01: 汎用スキーマ設計(Metric/Reminder/Goal)
親: [E02: ローカルデータモデル・キャッシュ基盤](../epics/E02-local-cache-and-model.md)

## 概要
NISA評価額・車貯金・口座残高などをMetric、点検通知やCSV催促を
Reminder、X万円目標などをGoalとして扱う共通スキーマを定義する。
item_type + 汎用value/unit/target/due_dateの形にする。

## 完了条件
Metric/Reminder/Goalそれぞれのフィールド定義がドキュメント化され、
E02-02のテーブル設計に反映できる状態。

## ステータス
未着手
