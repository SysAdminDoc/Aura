import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    id("com.google.gms.google-services")
    id("com.google.android.gms.oss-licenses-plugin")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.freevibe"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("signing.keystore.path", "../freevibe.jks"))
            storePassword = localProps.getProperty("signing.keystore.password", "")
            keyAlias = localProps.getProperty("signing.key.alias", "")
            keyPassword = localProps.getProperty("signing.key.password", "")
        }
    }

    defaultConfig {
        applicationId = "com.freevibe"
        minSdk = 26
        targetSdk = 35
        versionCode = 121
        versionName = "6.32.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API keys — defaults baked in, user can override via settings
        buildConfigField("String", "PEXELS_API_KEY", "\"${localProps.getProperty("pexels.api.key", "")}\"")
        buildConfigField("String", "PIXABAY_API_KEY", "\"${localProps.getProperty("pixabay.api.key", "")}\"")
        buildConfigField("String", "FREESOUND_API_KEY", "\"${localProps.getProperty("freesound.api.key", "")}\"")
        buildConfigField("String", "SOUNDCLOUD_CLIENT_ID", "\"${localProps.getProperty("soundcloud.client.id", "")}\"")
        buildConfigField("String", "STABILITY_AI_KEY", "\"${localProps.getProperty("stability.ai.key", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Backport java.* APIs (e.g. URLEncoder.encode(String, Charset), added in
        // API 33) to our minSdk 26 floor. NewPipeExtractor's Utils.encodeUrlUtf8
        // calls that overload on every search, which crashed with NoSuchMethodError
        // on Android < 13 (issue #2: Sounds tab tap → crash on Android 10).
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        // Firebase BoM 34.13.0 ships firebase-auth 24.1.0 compiled with a newer
        // Kotlin (metadata 2.3.0) than this project's pinned compiler (2.1.0), which
        // makes kspDebugKotlin reject the .kotlin_module under strict checking. The
        // bytecode + public API are stable; only the metadata stamp is ahead, so we
        // read it best-effort rather than dragging the whole toolchain forward.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
}

dependencies {
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-databind:2.17.3") {
            because("youtubedl-android 0.18.1 pulls jackson-databind 2.11.1, which has published CVEs")
        }
        implementation("commons-io:commons-io:2.16.1") {
            because("youtubedl-android 0.18.1 pulls commons-io 2.5, which has published CVEs")
        }
    }

    // Core library desugaring — required by NewPipeExtractor on API < 33 so
    // URLEncoder.encode(String, Charset) and friends resolve at runtime (issue #2).
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.profileinstaller)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Image Loading
    implementation(libs.coil.compose)

    // Media Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // WorkManager
    implementation(libs.work.runtime)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // DataStore
    implementation(libs.datastore)

    // Palette (Material You color extraction)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.serialization.json)

    // QR code generation/decoding for shareable collection links
    implementation("com.google.zxing:core:3.5.3")

    // Glance Widgets
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Testing — unit
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)

    // Testing — instrumented
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
    baselineProfile(project(":baselineprofile"))

    // Firebase
    // BoM 34.x removes the deprecated *-ktx artifacts and updates the transitive
    // protobuf-javalite past CVE-2024-7254 (N-2). Kotlin extensions (await, etc.)
    // are still available via kotlinx-coroutines-play-services (pulled in via
    // coroutines-android).
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // NewPipe Extractor (YouTube search without API key)
    // PIN: NewPipe ships YouTube-extractor patches monthly. Bumping versions can
    // introduce subtle stream-handling regressions (DownloaderImpl InputStream leak
    // historically, fixed in v5.8). Re-verify YouTubeRepository + DownloaderImpl
    // stream lifecycle on every bump (re-verified clean for v0.26.3).
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")

    // yt-dlp for Android (YouTube stream URL extraction)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // ML Kit Subject Segmentation — API 24+, multi-subject, unbundled (the model
    // is downloaded on first use via Google Play services). Roadmap N-3.
    // NOTE: this is the Play-services (`com.google.android.gms:play-services-mlkit-*`)
    // channel — there is NO bundled `com.google.mlkit:segmentation-subject` artifact
    // on Google Maven (only segmentation-selfie ships bundled). Same
    // `com.google.mlkit.vision.segmentation.subject.*` API surface either way.
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    // ModuleInstallClient lets us proactively download the unbundled segmenter
    // model so parallax wallpapers don't fail silently on first apply.
    implementation("com.google.android.gms:play-services-base:18.5.0")
}
