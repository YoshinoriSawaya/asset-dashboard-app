# 資産ダッシュボード

個人用の資産形成ダッシュボードAndroidアプリ。銀行から落としたCSVを
Google Driveの `inbox` に放り込むだけで取り込み、資産の推移を1か所で見る。
ホームウィジェットは「何か対応が必要か」だけを色で知らせる。

ストア非公開・自分専用。マネーフォワード等への課金を避けるために自作している。

## いまどこまで動くか

**E02(ローカルDB・集計)まで。**
アプリを開いて「Driveと同期」を押すと:

1. Googleアカウントで認可(初回だけ同意画面)
2. `/資産アプリ/` 配下の6フォルダを用意(無ければ作る)
3. `inbox` のCSVを読んでパース
4. 正規化した結果を `backup` にJSONで保存
5. 元ファイルを `processed` へ移動
6. 問題があれば `logs` に理由を残す
7. `backup`・`corrections`・`settings` からローカルDBを作り直す

ボタンを押さなくても、開いたときに手元のデータが30日より古ければ
自動で同じことをする。

トップ画面に項目(今は資産推移CSVの系列)が最新値と今月の増減付きで並び、
タップすると詳細画面に移る。詳細の中身・グラフ・サマリー画面はこれから(E03)。

進捗と設計の経緯は [issues/README.md](issues/README.md) を見る。

## Driveのフォルダ構成

```
マイドライブ/資産アプリ/
  inbox/        ← CSVをここに放り込む(人間が触るのはここだけ)
  processed/    ← 取り込み済みCSVの移動先
  backup/       ← 正規化済みデータ(JSON)
  corrections/  ← 手動補正
  settings/     ← 項目(Goal・Reminderなど)の定義
  logs/         ← 取り込み失敗の記録
```

## ビルドと実行

このマシンでは `java` も `adb` もPATHに入っていないため、
`./gradlew` をそのまま叩いても動かない。

**手順は [docs/development.md](docs/development.md) にまとめてある。**
ビルド、エミュレータの起動、画面の確認、ログの見方、DBの覗き方まで。

## 構成

| 場所 | 中身 |
|------|------|
| `app/src/main/java/.../drive/` | Drive API、認可、同期の流れ |
| `app/src/main/java/.../csv/` | CSVのパース、バリデーション、重複検出 |
| `app/src/main/java/.../data/` | Room(ローカルキャッシュ) |
| `app/src/main/java/.../ui/` | Compose(いまはテーマだけ) |
| `issues/` | エピックとタスク。設計の経緯もここ |
| `docs/` | 全体の仕組みと開発手順 |

設計の考え方は [docs/architecture.md](docs/architecture.md)。

## 技術スタック

Kotlin / Jetpack Compose / Glance(ウィジェット) / Room / Drive REST API /
Firebase Cloud Messaging

| | |
|---|---|
| AGP | 9.4.1 |
| Kotlin | 2.4.20 |
| Gradle | 9.7.1 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |

## 個人情報の扱い

このリポジトリに**実データのCSVは置かない**。口座番号・氏名・残高が入る。
テストは実ファイルで起きた事象を最小の形に写したものを使っている。

`.gitignore` で keystore・`google-services.json`・`local.properties` も
除外してある。
