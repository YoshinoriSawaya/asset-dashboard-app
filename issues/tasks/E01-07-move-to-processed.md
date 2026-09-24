# E01-07: inbox→processed移動処理
親: [E01: Google Drive連携基盤](../epics/E01-drive-integration.md)

## 概要
パース・バリデーションが成功したファイルをinboxからprocessedへ
Drive API経由で移動する。ファイル名の重複時は自動リネームする。

## 完了条件
処理成功後、inboxからファイルが消え、processedに移動している。

## ステータス
未着手
