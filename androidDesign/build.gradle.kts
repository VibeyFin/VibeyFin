// What both Android apps (phone and TV) share and nothing else: the design tokens, checked against design/tokens.json by
// DesignTokensTest, the brand typeface, and the Compose stability config. No screens, no theme: each app builds its own
// Material theme (androidx.compose.material3 on the phone, androidx.tv.material3 on TV) from these values.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.vibeyfin.design"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
