# E01: Google Drive連携基盤

## 概要
inboxフォルダに置かれたCSVを検知・取得し、パースしてローカルキャッシュに
反映するまでの一連の基盤を作る。他の全機能(ウィジェット表示、サマリー等)
の土台になるエピック。

## 子イシュー
- [x] [E01-01: Google Cloud Console設定・OAuth認証](../tasks/E01-01-oauth-setup.md)
- [x] [E01-02: Driveフォルダ構成の作成(inbox/processed/backup/corrections/logs)](../tasks/E01-02-folder-structure.md)
- [x] [E01-03: inboxファイル一覧取得・新規判定ロジック](../tasks/E01-03-inbox-detection.md)
- [x] [E01-04: 既知ヘッダー判定パーサー(銀行ごとのアダプター)](../tasks/E01-04-known-header-parser.md)
- [x] [E01-05: 汎用金額抽出パーサー(未知フォーマットのフォールバック)](../tasks/E01-05-fallback-parser.md)
- [x] [E01-06: バリデーション(文字コード/区切り文字/異常値チェック)](../tasks/E01-06-validation.md)
- [x] [E01-07: inbox→processed移動処理](../tasks/E01-07-move-to-processed.md)
- [x] [E01-08: エラー時のlogs書き込み処理](../tasks/E01-08-error-logging.md)
- [x] [E01-09: 整形済みデータのbackup書き込み処理](../tasks/E01-09-backup-write.md)
- [x] [E01-10: 手動補正データの書き戻し(corrections)](../tasks/E01-10-corrections-write-back.md)
- [x] [E01-11: 重複トランザクション検出](../tasks/E01-11-duplicate-detection.md)
- [ ] [E01-12: 既存データ(マネーフォワード等)の初回移行](../tasks/E01-12-initial-data-migration.md)
- [x] [E01-13: 認証切れ・オフライン時の挙動](../tasks/E01-13-auth-and-offline-handling.md)
- [x] [E01-14: クレジットカード利用明細CSVの対応](../tasks/E01-14-credit-card-csv.md)
- [x] [E01-15: 証券口座の保有商品一覧CSVの対応](../tasks/E01-15-securities-holdings-csv.md)

## 完了条件
CSVをinboxに置いてアプリを起動すると、自動でパース・バリデーションが走り、
成功したファイルはprocessedに移動、失敗したファイルはinboxに残ってlogsに
理由が記録される状態。整形済みデータはbackupにも保存されている。

## 現状 (2026-09-25)
**完了条件の一周は通っている。** 実データ3ファイル(銀行明細2種・資産推移1種)で
取り込み→processedへ移動まで確認済み。

14/15完了。残るE01-12は実装ではなく作業(本人のCSVの投入)。
E01-14(カードの利用明細)・E01-15(証券口座の保有商品一覧)は2026-09-26に実物で確認した。

以下はE01では完結せず、後のエピックに引き継ぐ:

| 項目 | 引き継ぎ先 |
|------|-----------|
| パース結果をDBに保存する | E02 |
| 重複検出を取り込み経路に繋ぐ | E02 |
| 手動補正のUI | E03-04 |
| corrections込みでの再構築 | E06-02 |
| 過去データの一括投入 | E01-12(E02・E03の後) |

## 依存関係
E00(開発環境構築)完了後に着手
