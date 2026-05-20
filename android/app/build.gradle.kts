plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.daniel.ege100"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daniel.ege100"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Stage 2 debug: включаем экспорт Room-схемы в build/generated/ksp/.../schemas/
        // для сверки с фактической DDL parser/corpus.db.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Stage 1: corpus.db лежит в src/main/assets/. AAPT по умолчанию НЕ сжимает .db,
    // но явно фиксируем — на больших sqlite-файлах сжатие ломает createFromAsset на
    // некоторых устройствах + увеличивает время первого старта.
    androidResources {
        // Stage 3 fix: corpus.db (192 MB) — содержит HTML текст и JSON paths,
        // отлично сжимается AAPT (~60% ratio). Раньше стоял `noCompress += "db"`
        // как страховка от старых багов SQLiteOpenHelper на сжатом asset, но
        // Room 2.4+ использует AssetManager.open() → InputStream, разжимает
        // прозрачно. Снимаем noCompress — экономит ~100 MB на APK.
        //
        // Также: AAPT2 по умолчанию ignoreAssetsPattern содержит "_*", и у нас
        // вся библиотека SVG-формул лежит в `parser/assets/_formulas/...` —
        // переопределяем pattern без `_*` и `<dir>_*`.
        ignoreAssetsPattern =
            "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    // Stage 3 fix: подключаем parser/assets/ как второй asset-source.
    // Там лежат 52 697 SVG-формул (_formulas/XX/HASH.svg, ~352 MB) и 3 128
    // иллюстраций ({sdamgia_id}/img_N.svg). Они нужны HtmlRenderer'у для
    // отображения формул и чертежей. Не копируем в src/main/assets — один
    // источник истины (parser/build_db.py пишет туда + corpus.db ссылается).
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../../parser/assets")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.jsoup)
    implementation(libs.androidsvg)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)

    // Ktor — пустое подключение под Фазу 4 (AI). В Stage 1 не используется.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Phase 5 Stage E1 — JUnit для юнит-тестов SrsAlgorithm (pure JVM,
    // без эмулятора). Гонится через `./gradlew test`.
    testImplementation(libs.junit)
}
