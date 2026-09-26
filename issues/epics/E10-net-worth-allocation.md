# E10: 純資産・資金配分の統合

## 概要
個別のMetric(NISA、口座残高、iDeCo等)を合算した純資産推移を出し、
余剰資金が出た際の配分優先順位をルール化する。「増える様子」だけ
でなく「今どんな配分になっているか」を一望できるようにする。

## 子イシュー
- [x] [E10-01: 純資産推移(全Metric合算)の計算・表示](../tasks/E10-01-net-worth-aggregation.md)
- [x] [E10-02: 余剰資金の配分優先順位ルール設定](../tasks/E10-02-surplus-allocation-rules.md)
- [ ] [E10-03: 資産配分の可視化(NISA/iDeCo/現金/車貯金等の比率)](../tasks/E10-03-asset-allocation-visualization.md)
- [ ] [E10-04: NISA積立額の総合調整提案](../tasks/E10-04-nisa-adjustment-recommendation.md)

## 完了条件
すべてのMetricを合算した純資産推移が1本のグラフで見え、余剰資金が
出た際にどのGoalへ優先的に回すべきか、ルールに沿った提案が出る。
NISA積立額についても、複数要因を踏まえた調整提案が出る。

## 依存関係
E02(集計基盤)・E07(Goal管理)完了後に着手
