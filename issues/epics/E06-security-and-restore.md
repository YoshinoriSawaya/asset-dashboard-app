# E06: セキュリティ・バックアップ復元

## 概要
資産額が見えるアプリという性質上、起動時のロックを設ける。また
Driveをバックアップの正として、機種変更やアプリ再インストール時に
そこから復元できる状態にする。

## 子イシュー
- [x] [E06-01: アプリロック(生体認証/PIN)](../tasks/E06-01-app-lock.md)
- [x] [E06-02: Driveからのローカルキャッシュ再構築フロー](../tasks/E06-02-restore-from-drive.md)
- [x] [E06-03: 復元時のデータ整合性確認(元データ+corrections+backupの突合)](../tasks/E06-03-restore-integrity-check.md)
- [x] [E06-04: グラフ・金額表示のプライバシーモード](../tasks/E06-04-privacy-display-mode.md)

## 完了条件
アプリ起動時に生体認証/PINを要求する。アプリを削除して再インストール
しても、Driveのbackup/correctionsから元の状態を復元できる。

## 依存関係
E02・E01完了後に着手
