# E01-03: inboxファイル一覧取得・新規判定ロジック
親: [E01: Google Drive連携基盤](../epics/E01-drive-integration.md)

## 概要
Drive APIでinbox内のファイル一覧(id, modifiedTime)を取得し、前回取り込んだ
ファイルid一覧と比較して新規/未処理ファイルを判定する。

## 完了条件
inboxに新しいCSVを置くと、アプリがそれを「未処理」として検知できる。

## ステータス
未着手
