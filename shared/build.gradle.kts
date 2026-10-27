import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    compilerOptions {
        // Room's generated `expect object` database constructor triggers the beta warning.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "app.vibeyfin.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Runs commonTest on the JVM (`:shared:testAndroidHostTest`).
        withHostTest {}

        // On-device tests for what the JVM can't fake, e.g. the Android Keystore. They run at the
        // app's targetSdk: from 37, Android blocks local network traffic (discovery, LAN
        // connections) until the user grants ACCESS_LOCAL_NETWORK, so tests at 37 would not
        // behave like the app does.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            targetSdk { version = release(libs.versions.android.targetSdk.get().toInt()) }
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.lifecycle.viewmodel)

            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences.core)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.network)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

room3 {
    // Exported schemas are checked in so migrations can be tested against them.
    schemaDirectory("$projectDir/schemas")
}

// The Android build of the bundled SQLite driver only ships Android binaries, so JVM host tests
// can't load it. Unpack the desktop library from the JVM build and put it on the test's
// java.library.path. Nothing here ends up in the app.
val sqliteHostNatives = configurations.create("sqliteHostNatives") {
    isCanBeConsumed = false
    isTransitive = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}

abstract class UnpackNatives : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun unpack() {
        fileSystem.sync {
            from(archives.zipTree(jar.singleFile)) { include("natives/**") }
            into(outputDir)
        }
    }
}

val unpackSqliteHostNatives = tasks.register<UnpackNatives>("unpackSqliteHostNatives") {
    jar.from(sqliteHostNatives)
    outputDir.set(layout.buildDirectory.dir("sqlite-host-natives"))
}

val sqliteHostNativesDir = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osName = if ("mac" in os) "osx" else if ("win" in os) "windows" else "linux"
    val archName = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"

    layout.buildDirectory.dir("sqlite-host-natives/natives/${osName}_$archName")
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        dependsOn(unpackSqliteHostNatives)

        val nativesDir = sqliteHostNativesDir

        doFirst {
            val existing = systemProperties["java.library.path"]?.toString()
            val path = listOfNotNull(existing, nativesDir.get().asFile.path).joinToString(File.pathSeparator)
            systemProperty("java.library.path", path)
        }
    }
}

dependencies {
    // Room's compiler runs once per target; the generated database code is per platform.
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)

    sqliteHostNatives(libs.sqlite.bundled.jvm)
}
