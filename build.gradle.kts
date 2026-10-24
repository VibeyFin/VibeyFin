plugins {
    // Plugins are declared here (not applied) so every module resolves the same versions.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.versions)
    alias(libs.plugins.version.catalog.update)
}

// Style checks: ./gradlew ktlintCheck and detekt; ktlintFormat fixes what it can
val ktlintVersion = libs.versions.ktlint.get()

listOf(":androidMobileApp", ":shared").map { project(it) }.forEach { module ->
    module.run {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "dev.detekt")

        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            // Every source set, which a Kotlin Multiplatform module spreads over several folders
            source.setFrom(file("src").listFiles { dir -> dir.isDirectory }.orEmpty().map { File(it, "kotlin") })
        }

        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            // detekt's Kotlin front end stops short of the JVM the build runs on
            jvmTarget = "21"
        }

        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(ktlintVersion)
            android.set(true)
            filter {
                // Room and other generators write into build/
                exclude { element -> element.file.invariantSeparatorsPath.contains("/build/") }
            }
        }
    }
}

// Only stable releases count as updates (see CLAUDE.md). For local checks: ./gradlew dependencyUpdates, versionCatalogUpdate.
fun isUnstable(version: String): Boolean {
    val stable = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().endsWith(it) }
    return !stable && !Regex("^[0-9,.v-]+(-r)?$").matches(version)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isUnstable(candidate.version) && !isUnstable(currentVersion) }
}


versionCatalogUpdate {
    // Bump versions only; keep the catalog's order, comments and the SDK-level entries as written.
    sortByKey.set(false)
    keep {
        keepUnusedVersions.set(true)
    }
}
