plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tunetag.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tunetag.player"
        minSdk = 29          // Android 10 (EMUI 12 on the P40 Lite 5G)
        targetSdk = 29       // keeps legacy file access so tags can be written directly
        versionCode = 4
        versionName = "0.4.0"
    }

    buildTypes {
        release {
            // R8 shrinks + optimises the app: faster start-up, smoother scrolling, smaller APK.
            // If a build ever fails in "minifyReleaseWithR8", set both of these to false.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the APK installs straight away.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/*.md", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Android-compatible jaudiotagger fork (reads/writes MP3, FLAC, M4A, OGG, Opus... tags + artwork)
    implementation("com.github.AdrienPoupa:jaudiotagger:2.2.3")
}
