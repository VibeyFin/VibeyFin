plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Shared stability config: without it every poster redraws each time any row loads.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("androidDesign/compose_stability.conf"))
}

android {
    namespace = "app.vibeyfin.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Same ID as the phone app: one Play listing. Version codes must be unique across both, so the TV build needs its own range before the first upload.
        applicationId = "app.vibeyfin"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
    }

    buildTypes {
        // Release build signed with the debug key, to judge smoothness: `./gradlew :androidTvApp:installBenchmark`.
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").res.directories.add("../androidMobileApp/src/main/res")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":androidDesign"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
