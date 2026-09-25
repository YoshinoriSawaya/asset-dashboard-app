# E06-02: Driveからのローカルキャッシュ再構築フロー
親: [E06: セキュリティ・バックアップ復元](../epics/E06-security-and-restore.md)

## 概要
アプリを削除・再インストールした際、Driveのbackup/corrections/settingsから
ローカルDBを再構築する初期化フローを実装する(settingsはE02-01で追加)。

## 完了条件
再インストール後、Driveと同期するだけで元の状態(数値・補正内容含む)
が復元される。

## ステータス
未着手
