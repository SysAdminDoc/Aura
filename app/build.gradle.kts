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
}

val requestedGradleTasks = gradle.startParameter.taskNames
val neutralGradleTaskNames = setOf(
    "buildEnvironment",
    "clean",
    "components",
    "dependencies",
    "dependencyInsight",
    "help",
    "outgoingVariants",
    "projects",
    "properties",
    "resolvableConfigurations",
    "tasks",
)
fun String.gradleTaskLeaf(): String = substringAfterLast(":")

val requestedVariantTasks = requestedGradleTasks.filterNot { task ->
    task.gradleTaskLeaf() in neutralGradleTaskNames
}
val buildingFossOnly = requestedVariantTasks.isNotEmpty() &&
    requestedVariantTasks.all { task -> task.contains("Foss", ignoreCase = true) }
val reproducibleFossBuild = buildingFossOnly &&
    providers.gradleProperty("aura.reproducibleFossBuild")
        .orNull
        ?.toBooleanStrictOrNull() == true

if (!buildingFossOnly) {
    apply(plugin = "com.google.gms." + "google-services")
    apply(plugin = "com.google.android.gms." + "oss-licenses-plugin")
    tasks.configureEach {
        val generatedFossOssTask =
            name.startsWith("foss", ignoreCase = true) && name.contains("Oss", ignoreCase = true)
        val generatedFossGoogleTask =
            name.contains("Foss", ignoreCase = true) && name.contains("GoogleServices", ignoreCase = true)
        if (generatedFossOssTask || generatedFossGoogleTask) {
            enabled = false
        }
    }
}

configurations.configureEach {
    if (name.contains("Foss", ignoreCase = true)) {
        exclude(group = "com.google.firebase", module = "firebase-appcheck-debug")
    }
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
val instrumentationBuildType = providers.gradleProperty("auraInstrumentationBuildType")
    .orElse("debug")
    .get()

android {
    namespace = "com.freevibe"
    // Compile against 36, keep targetSdk at 35. Compiling against a newer platform
    // only widens the API surface available behind version guards; it triggers none
    // of the Android 16 behavior changes, which are keyed to targetSdk. Splitting
    // the two is what lets Media3 1.10+, Coil 3.5+, and okhttp-android 5.4 resolve
    // without also taking the predictive-back / edge-to-edge / orientation trio.
    compileSdk = 36

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
        versionCode = 148
        versionName = "6.45.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API keys — defaults baked in, user can override via settings
        buildConfigField("String", "PEXELS_API_KEY", "\"${localProps.getProperty("pexels.api.key", "")}\"")
        buildConfigField("String", "PIXABAY_API_KEY", "\"${localProps.getProperty("pixabay.api.key", "")}\"")
        buildConfigField("String", "FREESOUND_API_KEY", "\"${localProps.getProperty("freesound.api.key", "")}\"")
        buildConfigField("String", "SOUNDCLOUD_CLIENT_ID", "\"${localProps.getProperty("soundcloud.client.id", "")}\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("String", "STABILITY_AI_KEY", "\"${localProps.getProperty("stability.ai.key", "")}\"")
            buildConfigField("Boolean", "FOSS_BUILD", "false")
        }
        create("foss") {
            dimension = "distribution"
            buildConfigField("Boolean", "FOSS_BUILD", "true")
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            testProguardFiles("android-test-proguard-rules.pro")
            // Verification builders compare an unsigned FOSS artifact with the
            // owner-signed release modulo its signature. Keeping this opt-in avoids
            // local keystore inputs while preserving the normal signed release lane.
            signingConfig = if (reproducibleFossBuild) null else signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (instrumentationBuildType == "release") {
                proguardFiles("android-instrumentation-target-rules.pro")
            }
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

    composeCompiler {
        // Without these, how often a cell recomposes is invisible: the compiler
        // already knows which models it considers unstable and simply never says.
        // The reports land in build/ and are read by hand or by
        // tools/compose_stability_check.py after a build.
        metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
        // Third-party types the compiler cannot see into. Without this file every
        // composable taking one is treated as unstable, which buries Aura's own
        // models in noise.
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("compose-stability.conf")
        )
    }

    lint {
        // No detector disables. AGP 8.7.3 could not run lint at all — three Compose
        // detectors threw IncompatibleClassChangeError against its lint API and took
        // the whole run down with them, which is why NullSafeMutableLiveData was
        // disabled here. AGP 8.9.3 ships lint artifacts that match, so the run
        // completes and every detector reports. Re-add a disable only with the
        // stack trace that justifies it.
        warningsAsErrors = false
        abortOnError = true
    }

    // Per-ABI release APKs alongside the universal one.
    //
    // The universal artifact is ~199 MB because FFmpeg and Python ship for four
    // ABIs and a phone uses exactly one of them. IzzyOnDroid caps a single APK at
    // 30 MB and Accrescent at 128 MiB, so the universal build is a direct-download
    // artifact and nothing else.
    //
    // armeabi-v7a and x86 stay in the set deliberately: minSdk 26 still admits
    // 32-bit-only Android 8 and 9 devices, and splitting cuts the download without
    // cutting those users. Dropping 32-bit is a separate, user-facing decision and
    // is not what this does.
    //
    // Every split keeps the same versionCode. Only one ever installs on a given
    // device, and Obtainium's autoApkFilterByArch picks it by asset name.
    //
    // Splits must switch OFF whenever a bundle task is requested: since AGP 8.9.0,
    // PerModuleBundleTask.getResourcesFile calls single() over the shrunk-resources
    // artifact, which holds one converted file per enabled ABI split, so
    // :app:bundleFullRelease dies with "Sequence contains more than one matching
    // element" (issuetracker.google.com/402800800, won't-fix — multi-APK output is
    // unsupported while bundling). AABs ignore splits anyway; Play serves per-ABI
    // from the bundle. Consequence: run assemble* and bundle* as SEPARATE Gradle
    // invocations — a combined invocation would build universal-only APKs.
    val requestedBundleBuild = requestedGradleTasks.any { task ->
        task.gradleTaskLeaf().contains("bundle", ignoreCase = true)
    }
    splits {
        abi {
            isEnable = !requestedBundleBuild
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            // youtubedl-android extracts its zipped FFmpeg and Python payloads at runtime.
            // This cannot move to modern packaging until that extractor runtime is removed.
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

    testBuildType = instrumentationBuildType
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
}

dependencies {
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-databind:2.18.9") {
            because("youtubedl-android 0.18.1 pulls jackson-databind 2.11.1; 2.18.9 is the security floor for the transitive runtime")
        }
        implementation("commons-io:commons-io:2.16.1") {
            because("youtubedl-android 0.18.1 pulls commons-io 2.5, which has published CVEs")
        }
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because(
                "youtubedl-android 0.18.1 resolves commons-compress 1.12, which has published " +
                    "archive-expansion DoS advisories; 1.28.0 is the reviewed floor and keeps the " +
                    "ZipFile/ZipArchiveInputStream API youtubedl-common's ZipUtils binds to"
            )
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
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // Media Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.muxer)
    implementation(libs.media3.session)
    implementation(libs.media3.transformer)
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
    implementation(libs.zxing.core)

    // Glance Widgets
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Testing — unit
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    // Testing — instrumented
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
    baselineProfile(project(":baselineprofile"))

    // Firebase - full flavor only. The foss flavor compiles against local no-op
    // adapters so F-Droid-style dependency checks can verify that no Firebase or
    // Play Services artifacts are present in the FOSS dependency graph.
    // BoM 34.x removes the deprecated *-ktx artifacts and updates the transitive
    // protobuf-javalite past CVE-2024-7254 (N-2). Kotlin extensions (await, etc.)
    // are still available via kotlinx-coroutines-play-services (pulled in via
    // coroutines-android).
    add("fullImplementation", platform("com.google.firebase:firebase-bom:34.17.0"))
    add("fullImplementation", "com.google.firebase:firebase-auth")
    add("fullImplementation", "com.google.firebase:firebase-database")
    add("fullImplementation", "com.google.firebase:firebase-storage")
    add("fullImplementation", libs.firebase.functions)
    add("fullImplementation", libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // NewPipe Extractor (YouTube search without API key)
    // PIN: NewPipe ships YouTube-extractor patches monthly. Bumping versions can
    // introduce subtle stream-handling regressions (DownloaderImpl InputStream leak
    // historically, fixed in v5.8). Re-verify YouTubeRepository + DownloaderImpl
    // stream lifecycle on every bump (re-verified clean for v0.26.3).
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")

    // yt-dlp for Android (YouTube stream URL extraction)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // ML Kit Subject Segmentation — API 24+, multi-subject, unbundled (the model
    // is downloaded on first use via Google Play services). Roadmap N-3.
    // NOTE: this is the Play-services (`com.google.android.gms:play-services-mlkit-*`)
    // channel — there is NO bundled `com.google.mlkit:segmentation-subject` artifact
    // on Google Maven (only segmentation-selfie ships bundled). Same
    // `com.google.mlkit.vision.segmentation.subject.*` API surface either way.
    add("fullImplementation", "com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    // ModuleInstallClient lets us proactively download the unbundled segmenter
    // model so parallax wallpapers don't fail silently on first apply.
    add("fullImplementation", "com.google.android.gms:play-services-base:18.5.0")
}
