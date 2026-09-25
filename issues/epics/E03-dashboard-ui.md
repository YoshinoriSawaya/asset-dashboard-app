# E03: ダッシュボードUI・詳細画面

## 概要
アプリ本体の画面群を作る。ウィジェットからタップして開いたときに、
各項目(NISA、車貯金、緊急予備資金、車メンテ、口座残高など)の
詳細・月次年次サマリー・手動補正が行える状態にする。

## 子イシュー
- [x] [E03-01: ナビゲーション構造・トップ画面](../tasks/E03-01-navigation-top-screen.md)
- [x] [E03-02: 項目詳細画面(Metric/Goal/Reminder共通テンプレート)](../tasks/E03-02-item-detail-screen.md)
- [x] [E03-03: 月次・年次サマリー画面](../tasks/E03-03-monthly-yearly-summary-screen.md)
- [x] [E03-04: 手動補正入力画面(E01-10と連動)](../tasks/E03-04-manual-correction-screen.md)
- [x] [E03-05: 前回同期結果の要約表示(成功/失敗件数)](../tasks/E03-05-sync-result-summary.md)
- [x] [E03-06: グラフ実装(チャートライブラリ選定・配色)](../tasks/E03-06-chart-implementation.md)
- [x] [E03-07: AI解析用データ出力機能](../tasks/E03-07-ai-export-prompt.md)
- ~~E03-08: 口座残高の積み上げ色分けグラフ~~ → [E07-13](../tasks/E07-13-stacked-account-graph.md)へ移動(E07-10が前提のため)

## 完了条件
アプリを開くとトップにサマリーが並び、各項目をタップすると時系列
グラフや進捗が見える詳細画面に遷移できる。手動補正もアプリ内から
行え、その内容はDriveのcorrectionsに書き戻される。

## 依存関係
E02(ローカルデータモデル・キャッシュ基盤)完了後に着手
