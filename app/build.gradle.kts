import java.util.Properties

// ============================================================================
//  Axiom Calc - :app module
// ----------------------------------------------------------------------------
//  Offline-first Android mathematics platform.
//
//  Hard constraints enforced here:
//    * No `INTERNET` permission is ever declared (see AndroidManifest.xml).
//    * No analytics, advertising, crash-reporting or remote-config SDK is a
//      dependency. The dependency list below is the complete runtime surface.
//    * Release signing credentials are read from an UNTRACKED
//      `keystore.properties` file. Nothing secret is committed.
// ============================================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Optional, untracked release-signing configuration.
 *
 * Expected format of `keystore.properties` at the repository root:
 * ```
 * storeFile=/absolute/path/to/axiom-calc-release.jks
 * storePassword=...
 * keyAlias=...
 * keyPassword=...
 * ```
 * When the file is absent the `release` build type is left UNSIGNED and AGP
 * emits `app-release-unsigned.apk`. That is intentional: the owner controls the
 * production signing identity and it is never committed to Git.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists() &&
    keystoreProperties.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.shohankhan.axiomcalc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shohankhan.axiomcalc"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // When no owner keystore is configured the release variant stays
            // unsigned. AGP then produces `app-release-unsigned.apk`.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
            )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = false
            all {
                it.testLogging {
                    events("passed", "skipped", "failed")
                }
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        xmlReport = true
        htmlReport = true
        sarifReport = true
    }

    dependenciesInfo {
        // Disables the Play-encrypted dependency metadata block; Axiom Calc is
        // not distributed with a proprietary signing dependency.
        includeInApk = false
        includeInBundle = false
    }
}

// Room schema history is exported so migrations are reviewable and testable.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // ---- AndroidX core ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ---- Jetpack Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- Navigation ----
    implementation(libs.androidx.navigation.compose)

    // ---- Persistence ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // ---- Background retention cleanup ----
    implementation(libs.androidx.work.runtime.ktx)

    // ---- Coroutines ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- Unit tests (local JVM) ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // ---- Instrumented tests (device / emulator) ----
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
