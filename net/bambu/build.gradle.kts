plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bambuprinterlan.net.bambu"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Public API surface (Flow in DeviceTransport, OkHttpClient in client ctors)
    // is exposed to consumers, so these are `api`, not `implementation`.
    api(libs.coroutines.android)
    api(libs.okhttp)
    // LAN transport: Paho MQTT (TLS 8883) + Commons Net FTPS (990). jmDNS next.
    implementation(libs.paho.mqtt)
    implementation(libs.commons.net)
    // implementation(libs.jmdns)
}
