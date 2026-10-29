plugins {
    // Plugins are declared here (not applied) so every module resolves the same versions.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.versions)
    alias(libs.plugins.version.catalog.update)
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
