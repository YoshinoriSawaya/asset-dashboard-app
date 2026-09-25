# E07: 目標(Goal)管理機能

## 概要
車購入・緊急予備資金・PC/家電買い替えなど、複数のGoal項目を
自分で追加・編集でき、口座残高やDriveデータと連動して進捗が
自動的に見える状態にする。

## 子イシュー
- [x] [E07-01: Goal項目のCRUD画面(追加・編集・削除、目標金額の入力を含む)](../tasks/E07-01-goal-crud-screen.md)
- [x] ~~[E07-02: 口座残高との自動連動](../tasks/E07-02-account-balance-linking.md)~~ → E07-10に統合
- [x] ~~[E07-03: 初期Goalデータ投入](../tasks/E07-03-initial-goal-data.md)~~ → E07-01に統合(実額をリポジトリに入れないため、画面から入れる)
- [x] ~~[E07-04: 目標金額・期日の設定](../tasks/E07-04-goal-amount-and-due-date.md)~~ → E07-01・E07-11に統合
- [ ] [E07-05: iDeCo Metricの追加](../tasks/E07-05-idoco-metric.md)
- [ ] [E07-06: 生活防衛資金Goalの目標額自動計算](../tasks/E07-06-emergency-fund-auto-target.md)
- [ ] [E07-07: 生活防衛資金の回復プラン提示](../tasks/E07-07-emergency-fund-recovery-plan.md)
- [ ] [E07-08: NISA積立の一時減額提案(生活防衛資金連動)](../tasks/E07-08-nisa-temporary-reduction.md)
- [ ] [E07-09: ふるさと納税枠の管理](../tasks/E07-09-furusato-nozei.md)
- [ ] [E07-10: 単一口座内での仮想配分管理(封筒予算方式)](../tasks/E07-10-envelope-allocation.md)
- [ ] [E07-11: Goalの目標期日とランプアップ(積み増し開始)通知](../tasks/E07-11-goal-due-date-rampup.md)
- [ ] [E07-12: ランプアップGoal未達時の生活防衛資金取り崩し許容](../tasks/E07-12-rampup-shortfall-emergency-fund-drawdown.md)
- [ ] [E07-13: 口座残高の積み上げ色分けグラフ](../tasks/E07-13-stacked-account-graph.md)

## 完了条件
「車購入用にX万円」「PC買い替え用にX万円」のようなGoalを自分で
登録でき、CSV取り込みのたびに進捗(%)が自動更新される。

## 依存関係
E02・E03完了後に着手
