plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.aikeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aikeyboard"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only ship English resources; drops the dozens of translations bundled by AndroidX libraries.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            // Play App Signing upload key; supplied by CI secrets, never committed.
            val path = providers.environmentVariable("UPLOAD_KEYSTORE").orNull
            if (path != null) {
                storeFile = file(path)
                storePassword = providers.environmentVariable("UPLOAD_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("UPLOAD_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("UPLOAD_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Typosaurus (debug)")
            buildConfigField("boolean", "ALLOW_BASE_URL_OVERRIDE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Typosaurus")
            buildConfigField("boolean", "ALLOW_BASE_URL_OVERRIDE", "false")
            // CI signs with the Play upload key; locally fall back to the debug key so the small, fast
            // release build can be installed on a phone for testing (never upload that one to Play).
            signingConfig = if (providers.environmentVariable("UPLOAD_KEYSTORE").isPresent) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }
    lint { warningsAsErrors = false; abortOnError = true }
    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/*.kotlin_module",
            "DebugProbesKt.bin", "kotlin-tooling-metadata.json",
        )
    }
    // The dependency list is only useful to the Play Store; keep it out of the APK itself.
    dependenciesInfo { includeInApk = false; includeInBundle = true }
}

// CI writes app/gradle.lockfile (`--write-locks`) so the OSV scan sees the exact release dependencies.
// Lenient: a missing or stale lockfile never breaks a local build.
dependencyLocking { lockMode.set(LockMode.LENIENT) }
configurations.matching { it.name == "releaseRuntimeClasspath" }.configureEach {
    resolutionStrategy.activateDependencyLocking()
}

dependencies {
    implementation(project(":data"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coroutines.android)
    // Installs the baseline profiles that Compose and AndroidX ship, so code is precompiled at install
    // time: faster keyboard open and smoother first frames in release builds.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
