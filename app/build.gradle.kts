import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * アプリのバージョン(E08-03)。ここだけを書き換える。
 * versionCode はここから決まる(1.2.3 → 10203)ので、上げ忘れて
 * 「更新なのにインストールできない」が起きない。
 */
val appVersion = "2.2.0"

fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split(".").map { it.toInt() }
    require(minor < 100 && patch < 100) { "minor/patchは99まで: $version" }
    return major * 10_000 + minor * 100 + patch
}

/**
 * リリース署名の設定(E08-01)。リポジトリ直下の keystore.properties から読む。
 * このファイルと鍵はgitに入れない(.gitignore済み)。作り方はE00-06。
 */
val releaseSigning: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.yswy.assetdashboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yswy.assetdashboard"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 鍵が無ければnull。そのときは下のチェックでビルドを止める
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // android.util.Log などをテストから呼んでも落とさない
            isReturnDefaultValues = true
        }
    }
}

// スキーマのjsonを出力させておく。マイグレーションを書くとき(E02)に必要になる。
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Drive連携(E01)。認証はAuthorizationClient、HTTPはOkHttpで直接叩く。
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)

    // ローカルキャッシュ(E01-03で取り込み済みファイルの記録から使い始める)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // アプリのロック(E06-01)。生体認証か端末の画面ロック(PIN等)。安定版は1.1.0が最新
    implementation(libs.androidx.biometric)

    // ウィジェット(E04)で使う。E00時点では依存を通すだけ。
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    // android.jarのorg.jsonはスタブで、putがnullを返す。
    // テストでは本物の実装を使う。
    testImplementation(libs.org.json)

    // Roomは本物のSQLiteが要るので、DBのテストはエミュレータで回す(E02-02)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)

    constraints {
        // room-testing(MigrationTestHelper)は1.8系を要求するが、アプリ側は間接依存で
        // 1.7.3に解決され、AGPがテストもアプリと同じ版に固定するため
        // AbstractMethodErrorで落ちる。アプリ側を揃えて上げる。
        implementation(libs.kotlinx.serialization.core)
    }
}

// 鍵が無いままリリースをビルドすると、署名されていないAPKが黙ってできる。
// それをスマホに入れようとして失敗するより、ここで止めて理由を出す(E08-01)。
val signingMissing = releaseSigning == null
tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") {
        // ローカルに写してから使う。スクリプトの変数を直接つかむと、
        // configuration cache が保存できずにビルドが落ちる
        val missing = signingMissing
        doFirst {
            if (missing) {
                throw GradleException(
                    "keystore.properties が無いので、リリース用に署名できない。" +
                        "作り方は issues/tasks/E00-06-keystore-creation.md",
                )
            }
        }
    }
}
