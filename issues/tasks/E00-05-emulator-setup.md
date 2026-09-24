# E00-05: エミュレータのセットアップ
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
実機が使えない場面(外出先での作業など)用に、AVD(Android Virtual Device)
を1つ作成しておく。

## 完了条件
エミュレータが起動し、空のプロジェクトを実行できる。

## 実施内容
既存のAVDは `Pixel_2_API_30`(API 30 / 32bit x86 / Play Store image)
だった。32bit x86イメージは現行エミュレータでは扱いが悪く、
またこのアプリは compileSdk 37 なので新しく作り直した。

- 追加したsystem image: `system-images/android-37.2/google_apis_ps16k/x86_64`
- 作成したAVD: **AssetDash_API37**(Pixel 9プロファイル, API 37.2)

`google_apis_playstore` ではなく **`google_apis`** を選んだ理由:
Play Store入りイメージは `adb root` できない。google_apisイメージなら
rootを取ってRoom DBの中身を直接 `adb pull` で確認できるので、
E02以降のデバッグが楽になる。Drive API / FCM に必要なGMSは
google_apisイメージにも入っている。

### 作成コマンド
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$bin = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin"
& "$bin\android.exe" sdk install "system-images/android-37.2/google_apis_ps16k/x86_64"
& "$bin\avdmanager.bat" create avd -n AssetDash_API37 `
  -k "system-images;android-37.2;google_apis_ps16k;x86_64" -d pixel_9
```

`android.exe sdk` のパッケージIDは `/` 区切り、`avdmanager` は `;` 区切りと
ツールによって違うので注意。

### config.iniの調整
`~/.android/avd/AssetDash_API37.avd/config.ini` を以下に変更した。

| キー | 値 | 理由 |
|------|-----|------|
| hw.ramSize | 4096 | 既定値だとCompose+GMSで重い |
| vm.heapSize | 512 | 同上 |
| disk.dataPartition.size | 8G | CSV取り込みテストでファイルを置くため |
| hw.keyboard | yes | PCのキーボードで直接入力できるようにする |
| hw.gpu.enabled / mode | yes / auto | 描画をGPUに任せる |

### 起動コマンド
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\android.exe" emulator start AssetDash_API37
```
ただし `android.exe emulator start` はエミュレータを**子プロセスとして起動し、
ブート完了を報告した時点で終了する**。つまりコマンドが終わるとエミュレータも
一緒に落ちる。スクリプトから「起動 → ビルド → 確認」を一周回す用途向け。

起動しっぱなしにしたい場合は Android Studio のデバイスマネージャーか、
CLIなら切り離して起動する。

```powershell
Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" `
  -ArgumentList "-avd","AssetDash_API37"
```

起動待ちは `adb -e shell getprop sys.boot_completed` が `1` になるのを見る。

ハードウェアアクセラレーションは WHPX が利用可能(`emulator -accel-check` で確認)。

## ステータス
完了 (2026-09-24)
