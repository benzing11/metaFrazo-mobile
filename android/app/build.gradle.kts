plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.metafrazo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.metafrazo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.0"
        vectorDrawables { useSupportLibrary = true }
        // 127.0.0.1 works on a USB-connected phone via `adb reverse tcp:3000 tcp:3000` (and on emulators with the same reverse).
        // Override at runtime in Settings for LAN testing.
        buildConfigField("String", "BACKEND_URL", "\"http://127.0.0.1:3000\"")
        buildConfigField("String", "AI_SERVICE_URL", "\"http://127.0.0.1:8000\"")
    }

    signingConfigs {
        create("release") {
            storeFile = (project.findProperty("KEYSTORE_PATH") ?: System.getenv("KEYSTORE_PATH"))?.let { File(it.toString()) }
            storePassword = (project.findProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD"))?.toString()
            keyAlias = (project.findProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS"))?.toString()
            keyPassword = (project.findProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD"))?.toString()
        }
    }
    buildTypes {
        debug {
            // `adb reverse tcp:3000 tcp:3000` (and 8000/7880/7881) makes the dev machine's
            // services reachable on the phone's own localhost, even on a different network.
            // For phones without USB: `./gradlew assembleDebug -PLAN_HOST=<computer LAN IP>` points the
            // build at the computer directly (same Wi-Fi required).
            val devHost = (project.findProperty("LAN_HOST") as String?)?.takeIf { it.matches(Regex("[A-Za-z0-9.\\-]+")) } ?: "localhost"
            buildConfigField("String", "BACKEND_URL", "\"http://$devHost:3000\"")
            buildConfigField("String", "AI_SERVICE_URL", "\"http://$devHost:8000\"")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation("io.livekit:livekit-android:2.29.2")
    implementation("io.livekit:livekit-android-compose-components:2.4.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")


    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}