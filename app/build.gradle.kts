plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

/**
 * Release signing is driven by environment variables so no keystore or password
 * is ever committed. The encrypted-public-builder generates a self-signed
 * keystore on the runner and exports these vars before `assembleRelease`.
 * Locally, absence of RELEASE_STORE_FILE just skips the release signing config.
 */
val storeFilePath = System.getenv("RELEASE_STORE_FILE")

android {
    namespace = "com.bambuprinterlan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bambuprinterlan.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "310").toInt()
        versionName = "0.3.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                // Sign with v1+v2+v3 for the widest device compatibility.
                // v2-only caused "App not installed" on some installers/OEMs.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (storeFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        // registerForActivityResult lives in an Activity (not a Fragment); this
        // check is a false positive that fails lintVitalRelease.
        disable += "InvalidFragmentVersionForActivityResult"
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":net:bambu"))
    implementation(project(":engine:jni"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    // QR quick-add: Google Code Scanner (no camera-permission UI; uses Play Services)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
