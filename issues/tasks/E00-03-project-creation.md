# E00-03: プロジェクト作成
親: [E00: 開発環境構築](../epics/E00-dev-environment-setup.md)

## 概要
Kotlin + Jetpack Compose + Glance(ウィジェット用)のテンプレートで
新規プロジェクトを作成する。保存先は `/asset-dashboard-app/` 直下
(E00-01で作成済みのフォルダ)を指定する。

## 完了条件
空のプロジェクトがビルドエラーなく作成できている。

## 実施内容
Android StudioのウィザードではなくCLIで直接スキャフォールドした
(Claude Codeから一貫して扱えるようにするため)。

```
asset-dashboard-app/
  settings.gradle.kts / build.gradle.kts / gradle.properties
  gradle/libs.versions.toml      ← 依存バージョンはここに集約
  gradle/wrapper/                ← Gradle 9.7.1
  gradlew / gradlew.bat
  local.properties               ← sdk.dir(gitignore対象)
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/yswy/assetdashboard/MainActivity.kt
    src/main/java/com/yswy/assetdashboard/ui/theme/{Color,Theme,Type}.kt
    src/main/java/com/yswy/assetdashboard/widget/   ← E04用に空で用意
    src/main/res/{values,drawable,mipmap-anydpi-v26,xml}/
```

### 主要な設定値
| 項目 | 値 |
|------|-----|
| applicationId / namespace | `com.yswy.assetdashboard` |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| versionName | 0.1.0 |
| AGP | 9.4.1 |
| Kotlin | 2.4.20 |
| Gradle | 9.7.1 |
| Compose BOM | 2026.09.00 |
| Glance | 1.2.0 |

### ハマりどころ
- **AGP 9 はKotlinサポートを内蔵した**ため、`org.jetbrains.kotlin.android`
  プラグインを併用するとビルドが失敗する(「no longer required since
  AGP 9.0」)。`com.android.application` と
  `org.jetbrains.kotlin.plugin.compose` の2つだけを適用する。
- Glanceの依存はE00時点では使っていないが、E04で使うので先に通してある。

## 検証結果
`./gradlew assembleDebug` が BUILD SUCCESSFUL。
生成APK: `app/build/outputs/apk/debug/app-debug.apk` (約13.6MB)
`aapt2 dump badging` で package=`com.yswy.assetdashboard`,
minSdk=26, targetSdk=37, label=資産ダッシュボード を確認。

## ステータス
完了 (2026-09-24)
