plugins {
    // Plugins are declared here (not applied) so every module resolves the same versions.
    alias(libs.plugins.android.application) apply false
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

apply(from = "gradle/theme-generator.gradle.kts")

// Style checks: ./gradlew ktlintCheck and detekt; ktlintFormat fixes what it can
val ktlintVersion = libs.versions.ktlint.get()

listOf(":androidApp", ":shared").map { project(it) }.forEach { module ->
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

// Screens take colours from VibeyFinTokens; raw ones live in app/theme only. Runs with detekt, which has no rule for a property like Color.White.
val rawColorPattern = """Color\.(White|Black)\b|\bColor\(\s*(0x|red\s*=|green\s*=|blue\s*=|alpha\s*=)"""

val checkRawColors = project(":androidApp").tasks.register("checkRawColors") {
    group = "verification"
    description = "Fails when a screen types a raw colour instead of a VibeyFinTokens one"

    val sources = project(":androidApp").fileTree("src") {
        include("**/*.kt")
        exclude("**/app/theme/**")
    }

    val rootPath: String = rootDir.path
    val pattern: String = rawColorPattern

    doLast {
        val matcher = Regex(pattern)
        val offences = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (matcher.containsMatchIn(line)) "${file.path.removePrefix(rootPath)}:${index + 1}: ${line.trim()}" else null
            }
        }

        if (offences.isNotEmpty()) {
            throw GradleException("Raw colours outside app/theme, use VibeyFinTokens:\n" + offences.joinToString("\n"))
        }
    }
}

project(":androidApp").tasks.withType<dev.detekt.gradle.Detekt>().configureEach { dependsOn(checkRawColors) }

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
