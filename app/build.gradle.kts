import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing: credentials are read from local.properties (gitignored)
// or JAVA env vars. The keystore file lives in the gitignored keystore/ dir.
// The owner MUST replace the development keystore with a production one
// before Play Store submission — see docs/reports/RELEASE-CHECKLIST.md.
val releaseSigning = Properties().apply {
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        localProps.inputStream().use { load(it) }
    }
}

android {
    signingConfigs {
        create("release") {
            val storeFilePath = releaseSigning.getProperty("ayah.release.storeFile")
            if (storeFilePath != null && rootProject.file(storeFilePath).exists()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = releaseSigning.getProperty("ayah.release.storePassword")
                keyAlias = releaseSigning.getProperty("ayah.release.keyAlias")
                keyPassword = releaseSigning.getProperty("ayah.release.keyPassword")
            }
            // When no keystore is configured the release build falls back to
            // unsigned; the AAB can still be generated but not installed.
        }
    }
    namespace = "com.ayah.quran"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ayah.quran"
        minSdk = 29
        targetSdk = 36
        // AGP 8 removed BuildConfig.VERSION_CODE; expose it explicitly so the
        // in-app updater can compare against release metadata.
        val code = 12
        versionCode = code
        buildConfigField("int", "VERSION_CODE", "$code")
        versionName = "1.0.0-beta.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // R8 is enabled from the start so release builds keep working;
            // keep rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME is shown on the Settings > About screen.
        buildConfig = true
    }

    androidResources {
        // Bundled adhan audio in res/raw must stay UNCOMPRESSED — ExoPlayer's
        // RawResourceDataSource needs a real file descriptor (openRawResourceFd),
        // which fails with "probably compressed" when AAPT deflates the asset.
        noCompress += "oga"
        noCompress += "ogg"
        noCompress += "wav"
        noCompress += "mp3"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room schema history is exported for migration testing (Phase 2+).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // ---- Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Full icon set: BottomBar destinations (Wallpaper, Book, MusicNote...) live here.
    // R8 strips unused icons from release builds.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // ---- AndroidX core ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    // ---- Dependency injection (Hilt) ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ---- Persistence (Room + DataStore) ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // ---- Background work (WallpaperWorker + scheduling) ----
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ---- Audio foundation (Media3/ExoPlayer, Phase 5) ----
    implementation(libs.androidx.media3.exoplayer)
    // Phase 17 slice 2: MediaSessionService — background playback, media
    // notification, lock-screen controls.
    implementation(libs.androidx.media3.session)

    // ---- Prayer times (Phase 12) — adhan-java, MIT, fully offline ----
    implementation("com.batoulapps.adhan:adhan:1.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ---- Coroutines ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- Unit tests ----
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // ---- Instrumentation tests (executed when an emulator is available) ----
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)

    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    }

    // ---- Debug tooling ----
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
