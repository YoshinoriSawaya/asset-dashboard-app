# E05: 通知基盤

## 概要
「月1でCSVを置いてね」という能動的な催促通知と、既存のNISAトレーリング
通知(日次スクレイピング、即時性が必要)を扱う。ウィジェットの色表示
(E04)とは別に、アプリを開いていなくても届く通知経路を整える。

## 子イシュー
- [ ] [E05-01: FCM(Firebase Cloud Messaging)セットアップ](../tasks/E05-01-fcm-setup.md)
- [ ] [E05-02: 月1CSV取得リマインダー通知](../tasks/E05-02-monthly-csv-reminder.md)
- [ ] [E05-03: 未対応時の再通知ロジック(催促)](../tasks/E05-03-followup-reminder.md)
- [ ] [E05-04: 既存NISAトレーリング通知との統合(PC側スクレイピング→FCM経由でスマホに転送)](../tasks/E05-04-nisa-alert-integration.md)
- [ ] [E05-05: 車メンテ年1点検リマインダー通知](../tasks/E05-05-car-maintenance-reminder.md)
- [ ] [E05-06: 保険・税金の更新リマインダー](../tasks/E05-06-insurance-tax-reminder.md)

## 通知を待っているもの
- [E07-07](../tasks/E07-07-emergency-fund-recovery-plan.md) 生活防衛資金が目標を下回ったときの通知(計算と表示はできている)

## 完了条件
CSV取得期限が来ると、アプリを開いていなくてもプッシュ通知が届く。
既存のNISAトレーリング通知(基準価額+10%到達)もスマホに届く。
未対応のまま数日経つと再通知される。

## 依存関係
E02(ローカルデータモデル・キャッシュ基盤)完了後に着手。
E05-04は既存のnisa-trailing-alertの仕組み(PC側)との接続が前提。
