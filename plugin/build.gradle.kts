import groovy.json.JsonBuilder
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.gradle.test.retry)
    alias(libs.plugins.ktfmt)
    signing
}

gradlePlugin {
    website = "https://github.com/mullvad/rust-android-gradle"
    vcsUrl = "https://github.com/mullvad/rust-android-gradle.git"
    plugins {
        register("rustAndroidGradlePlugin") {
            id = "net.mullvad.rust-android"
            displayName = "Plugin for building Rust with Cargo in Android projects"
            description =
                "A plugin that helps build Rust JNI libraries with Cargo for use in Android projects."
            tags = listOf("rust", "cargo", "android")
            implementationClass = "net.mullvad.androidrust.RustAndroidPlugin"
        }
    }
}

signing { useGpgCmd() }

ktfmt {
    kotlinLangStyle()
    maxWidth.set(100)
    removeUnusedImports.set(true)
}

val codeSigningEnabledProvider =
    providers
        .gradleProperty("mullvad.rust-android-gradle.codeSigningEnabled")
        .map { it.toBoolean() }
        .orElse(false)

tasks.withType<Sign>().configureEach {
    val enabled = codeSigningEnabledProvider
    onlyIf("signing is enabled") { enabled.get() }
}

val versionProperties =
    Properties().apply { load(FileInputStream("${rootProject.projectDir}/version.properties")) }

group = "net.mullvad"

version = versionProperties["version"]!!

val isCI = (System.getenv("CI") ?: "false").toBoolean()

// Maps supported Android plugin versions to the versions of Gradle that support it
val supportedVersions = mapOf("9.0" to listOf("9.2.1"), "8.13" to listOf("8.13"), "8.12" to listOf("8.13"))

val localRepo = file("${layout.buildDirectory.get()}/local-repo")

publishing { repositories { maven(localRepo) } }

dependencies {
    implementation(gradleApi())
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.android.gradlePlugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.android.gradlePlugin)
    testImplementation(libs.guava)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.framework.datatest)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin { jvmToolchain(17) }

val generatedResources = layout.buildDirectory.dir("generated-resources/main")
val generatedBuildResources = layout.buildDirectory.dir("build-resources")

tasks {
    val genVersionsTask =
        register("generateVersions") {
            inputs.property("version", version)
            inputs.property("supportedVersions", supportedVersions)
            outputs.dir(generatedResources)
            val outputFile =
                generatedResources.map {
                    it.asFile.mkdirs()
                    it.file("versions.json").asFile
                }
            outputFile
                .get()
                .writeText(
                    JsonBuilder(
                            mapOf("version" to version, "supportedVersions" to supportedVersions)
                        )
                        .toPrettyString()
                )
        }

    sourceSets { main { output.dir(mapOf("builtBy" to genVersionsTask), generatedResources) } }

    register("generateTestTasksJson") {
        inputs.property("supportedVersions", supportedVersions)
        outputs.dir(generatedBuildResources)
        val outputFile =
            generatedBuildResources.map {
                it.asFile.mkdirs()
                it.file("androidTestTasks.json").asFile
            }
        outputFile
            .get()
            .writeText(
                JsonBuilder(supportedVersions.keys.map { androidTestTaskName(it) }.toList())
                    .toString()
            )
    }

    withType<Test>().configureEach {
        dependsOn(publish)
        systemProperty("local.repo", localRepo.toURI())
        useJUnitPlatform()
        retry {
            maxRetries =
                if (isCI) {
                    1
                } else {
                    0
                }
            maxFailures = 20
        }

        javaToolchains {
            javaLauncher = launcherFor { languageVersion = JavaLanguageVersion.of(21) }
        }
    }

    supportedVersions.keys.forEach { agpVersion ->
        val testTaskName = androidTestTaskName(agpVersion)
        val jdkVersion = jdkVersionFor(agpVersion)
        val versionSpecificTest =
            register<Test>(testTaskName) {
                description =
                    "Runs the multi-version tests for AGP $agpVersion (JDK version $jdkVersion)"
                group = "verification"

                testClassesDirs = files(test.map { it.testClassesDirs })
                classpath = files(test.map { it.classpath })

                javaToolchains { javaLauncher = launcherFor { languageVersion = jdkVersion } }

                systemProperty("org.gradle.android.testVersion", agpVersion)
            }

        check { dependsOn(versionSpecificTest) }
    }
}

sourceSets { main { java { srcDirs("src/main/kotlin") } } }

fun androidTestTaskName(agpVersion: String) = "testAgp${normalizeVersion(agpVersion)}"

fun normalizeVersion(version: String) = version.replace("[.\\-]".toRegex(), "_")

@Suppress("UseRequire")
fun jdkVersionFor(agpVersion: String) =
    JavaLanguageVersion.of(
        when {
            agpVersion.split('.')[0].toInt() >= 9 -> 21
            agpVersion.split('.')[0].toInt() >= 8 -> 17
            else -> throw IllegalArgumentException("AGP version must be >=8")
        }
    )
