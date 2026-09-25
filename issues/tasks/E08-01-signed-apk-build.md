# E08-01: 署名付きAPKビルド手順の確立
親: [E08: リリース・配布](../epics/E08-release-and-distribution.md)

## 概要
E00-06で作成したkeystoreを使い、署名付きAPKをビルドする手順を
確立する。

## 完了条件
コマンド一つ、または決まった手順でAPKが生成できる。

## 作ったもの
`app/build.gradle.kts` に、リポジトリ直下の `keystore.properties` を読んで
リリース版に署名する設定を入れた。

```powershell
& "$proj\gradlew.bat" -p $proj assembleRelease
# → app\build\outputs\apk\release\app-release.apk
```

手順全体(鍵の登録・スマホへの入れ方)は [docs/development.md](../../docs/development.md)
の「リリース」。

## 設計の決定

### 鍵が無ければビルドを止める
Android Gradle Pluginは、署名の設定が無いと `app-release-unsigned.apk` を
黙って作る。これはスマホに入らない。入れようとして初めて気づくより、
ビルドの時点で「keystore.properties が無い。作り方はE00-06」と言って止める
(CLAUDE.mdの「壊れるなら見える側に」)。

### 鍵と設定はリポジトリの外(か、gitが無視する場所)
`keystore.properties` はリポジトリ直下だが `.gitignore` 済み。鍵そのものは
リポジトリの外(E00-06の例では `E:\Engineering\_secrets\`)。リポジトリは公開なので、
うっかり入れると取り返しがつかない。

### 難読化(minify)はしない
一人用でストアにも出さないので、サイズや解析対策の利点が無い。
R8で壊れる箇所(リフレクション、JSON)を探す手間のほうが大きい。

## 検証 (2026-09-25)
本物の鍵はまだ無い(E00-06はパスワードを決める手作業)ので、**使い捨ての鍵**で
仕組みを確かめ、確認後に鍵・`keystore.properties`・できたAPKを消した。

- `keystore.properties` が無いとき: 上の理由を出してビルドが止まる
- 使い捨ての鍵を置いたとき: `app-release.apk` ができ、`apksigner verify` で
  署名(v2)が通る。`versionName=1.0.0` / `versionCode=10000`
- `keystore.properties` がgitに無視されること(`git check-ignore`)
- デバッグ版が入ったエミュレータに上書きすると `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
  (E08-02の手順に書いた)

途中で、鍵の有無のチェックがスクリプトの変数を直接つかんでいて、
configuration cacheが保存できずにビルドが2つ目のエラーで落ちた。ローカルに
写してから使う形に直した。

## 未検証のまま残したこと
- **本物の鍵での署名**(E00-06を待つ)
- **リリース版での「Driveと同期」**。リリース鍵のSHA-1をCloud Consoleに
  登録する必要がある(手順はdevelopment.md)

## ステータス
完了(本物の鍵での確認はE00-06のあと) (2026-09-25)
