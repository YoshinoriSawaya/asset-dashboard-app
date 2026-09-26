# E00-04: 実機デバッグ環境のセットアップ
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
自分のAndroidスマホでUSBデバッグを有効化し、PCと接続してAndroid Studio
から認識される状態にする。

## 完了条件
PCとスマホをUSB接続し、Android Studioのデバイス一覧にスマホが表示される。

## 手順(スマホ側の操作が必要)
1. 設定 → デバイス情報 → ビルド番号 を7回タップして開発者モードを有効化
2. 設定 → システム → 開発者向けオプション → **USBデバッグ** をON
3. USBケーブルでPCに接続(充電のみモードではなく、ファイル転送/USBデバッグを許可)
4. 初回接続時にスマホ側で出る「USBデバッグを許可しますか?」を
   「このパソコンから常に許可」にチェックして許可

## PC側の確認コマンド
`adb` はPATHに入っていないためフルパスで叩く。

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

`device` として1行出れば成功。`unauthorized` ならスマホ側の許可ダイアログが
未承認、何も出ないならケーブル/USBモードを見直す。

## 検証 (2026-09-26)
本人がスマホでUSBデバッグを有効にしてつないだ。`adb devices -l` でスマホが `device` として出た
(エミュレータと並んで2台)。Androidは17(SDK 37)で、アプリのtargetSdk 37・minSdk 26の範囲。
このアプリはまだ入っていない(デバッグ版を消す手間は無い)。

完了条件の「Android Studioのデバイス一覧」は見ていない。Android Studioも同じadbで端末を見るので、
`adb devices` で見えれば足りるとした。

## ステータス
完了 (2026-09-26)
