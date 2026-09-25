plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.yswy.assetdashboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yswy.assetdashboard"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
