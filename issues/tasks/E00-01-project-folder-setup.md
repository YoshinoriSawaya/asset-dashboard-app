# E00-01: プロジェクトフォルダの作成・git init
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
資産ダッシュボードアプリ用のフォルダをローカルに作成し、Gitリポジトリを
初期化する。issues/フォルダ(epics/tasks)もこの中に置く。

## 完了条件
以下の構成でローカルフォルダが存在し、git initされている。

```
/asset-dashboard-app/
  /issues/
    /epics/
    /tasks/
```

(Androidプロジェクトはこの後E00-03でこの中に作成する)

## 補足
2026-09-24時点で `.git` が存在せず、git initが実際には未実施だった
(フォルダ構成のみ作成済み)。E00-03の作業時に `git init -b main` を
実行し、`.gitignore`(build成果物・local.properties・keystore・
google-services.json を除外)も追加した。まだコミットはしていない。

## ステータス
完了 (2026-09-24)
