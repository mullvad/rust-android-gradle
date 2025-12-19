package com.nishtahir

import com.android.build.gradle.*
import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync

const val RUST_TASK_GROUP = "rust"

enum class ToolchainType {
    ANDROID_PREBUILT,
    DESKTOP,
}

// See https://forge.rust-lang.org/platform-support.html.
val toolchains =
    listOf(
        Toolchain(
            "linux-x86-64",
            ToolchainType.DESKTOP,
            "x86_64-unknown-linux-gnu",
            "<compilerTriple>",
            "<binutilsTriple>",
            "desktop/linux-x86-64",
        ),
        // This should eventually go away: the darwin-x86-64 target will supersede it.
        // https://github.com/mozilla/rust-android-gradle/issues/77
        Toolchain(
            "darwin",
            ToolchainType.DESKTOP,
            "x86_64-apple-darwin",
            "<compilerTriple>",
            "<binutilsTriple>",
            "desktop/darwin",
        ),
        Toolchain(
            "darwin-x86-64",
            ToolchainType.DESKTOP,
            "x86_64-apple-darwin",
            "<compilerTriple>",
            "<binutilsTriple>",
            "desktop/darwin-x86-64",
        ),
        Toolchain(
            "darwin-aarch64",
            ToolchainType.DESKTOP,
            "aarch64-apple-darwin",
            "<compilerTriple>",
            "<binutilsTriple>",
            "desktop/darwin-aarch64",
        ),
        Toolchain(
            "win32-x86-64-msvc",
            ToolchainType.DESKTOP,
            "x86_64-pc-windows-msvc",
            "<compilerTriple>",
            "<binutilsTriple>",
            "desktop/win32-x86-64",
        ),
        Toolchain(
            "win32-x86-64-gnu",
            ToolchainType.DESKTOP,
            "x86_64-pc-windows-gnu",
            "<compilerTriple>",
            "<binutilsTriple>",
            "desktop/win32-x86-64",
        ),
        Toolchain(
            "arm",
            ToolchainType.ANDROID_PREBUILT,
            "armv7-linux-androideabi", // This is correct.  "Note: For 32-bit ARM, the compiler is
            // prefixed with
            "armv7a-linux-androideabi", // armv7a-linux-androideabi, but the binutils tools are
            // prefixed with
            "arm-linux-androideabi", // arm-linux-androideabi. For other architectures, the prefixes
            // are the same
            "android/armeabi-v7a",
        ), // for all tools."  (Ref:
        // https://developer.android.com/ndk/guides/other_build_systems#overview )
        Toolchain(
            "arm64",
            ToolchainType.ANDROID_PREBUILT,
            "aarch64-linux-android",
            "aarch64-linux-android",
            "aarch64-linux-android",
            "android/arm64-v8a",
        ),
        Toolchain(
            "x86",
            ToolchainType.ANDROID_PREBUILT,
            "i686-linux-android",
            "i686-linux-android",
            "i686-linux-android",
            "android/x86",
        ),
        Toolchain(
            "x86_64",
            ToolchainType.ANDROID_PREBUILT,
            "x86_64-linux-android",
            "x86_64-linux-android",
            "x86_64-linux-android",
            "android/x86_64",
        ),
    )

data class Ndk(val path: File, val version: String) {
    val versionMajor: Int
        get() = version.split(".").first().toInt()
}

data class Toolchain(
    val platform: String,
    val type: ToolchainType,
    val target: String,
    val compilerTriple: String,
    val binutilsTriple: String,
    val folder: String,
) {
    fun cc(apiLevel: Int): File =
        if (System.getProperty("os.name").startsWith("Windows")) {
            if (type == ToolchainType.ANDROID_PREBUILT) {
                File("bin", "$compilerTriple$apiLevel-clang.cmd")
            } else {
                File("$platform-$apiLevel/bin", "$compilerTriple-clang.cmd")
            }
        } else {
            if (type == ToolchainType.ANDROID_PREBUILT) {
                File("bin", "$compilerTriple$apiLevel-clang")
            } else {
                File("$platform-$apiLevel/bin", "$compilerTriple-clang")
            }
        }

    fun cxx(apiLevel: Int): File =
        if (System.getProperty("os.name").startsWith("Windows")) {
            if (type == ToolchainType.ANDROID_PREBUILT) {
                File("bin", "$compilerTriple$apiLevel-clang++.cmd")
            } else {
                File("$platform-$apiLevel/bin", "$compilerTriple-clang++.cmd")
            }
        } else {
            if (type == ToolchainType.ANDROID_PREBUILT) {
                File("bin", "$compilerTriple$apiLevel-clang++")
            } else {
                File("$platform-$apiLevel/bin", "$compilerTriple-clang++")
            }
        }

    fun ar(apiLevel: Int, ndkVersionMajor: Int): File =
        if (ndkVersionMajor >= 23) {
            File("bin", "llvm-ar")
        } else if (type == ToolchainType.ANDROID_PREBUILT) {
            File("bin", "$binutilsTriple-ar")
        } else {
            File("$platform-$apiLevel/bin", "$binutilsTriple-ar")
        }
}

@Suppress("unused")
open class RustAndroidPlugin : Plugin<Project> {
    internal lateinit var cargoExtension: CargoExtension

    override fun apply(project: Project) {
        with(project) {
            cargoExtension = extensions.create("cargo", CargoExtension::class.java)

            afterEvaluate {
                plugins.all {
                    when (it) {
                        is AppPlugin -> configurePlugin<AppExtension>(this)
                        is LibraryPlugin -> configurePlugin<LibraryExtension>(this)
                    }
                }
            }
        }
    }

    private inline fun <reified T : BaseExtension> configurePlugin(project: Project) =
        with(project) {
            cargoExtension.localProperties = Properties()

            val localPropertiesFile = File(project.rootDir, "local.properties")
            if (localPropertiesFile.exists()) {
                cargoExtension.localProperties.load(localPropertiesFile.inputStream())
            }

            if (cargoExtension.module == null) {
                throw GradleException("module cannot be null")
            }

            if (cargoExtension.libname == null) {
                throw GradleException("libname cannot be null")
            }

            // Allow to set targets, including per-project, in local.properties.
            val localTargets: String? =
                cargoExtension.localProperties.getProperty("rust.targets.${project.name}")
                    ?: cargoExtension.localProperties.getProperty("rust.targets")
            if (localTargets != null) {
                cargoExtension.targets = localTargets.split(',').map { it.trim() }
            }

            if (cargoExtension.targets == null) {
                throw GradleException("targets cannot be null")
            }

            // Ensure that an API level is specified for all targets
            val apiLevel = cargoExtension.apiLevel
            if (cargoExtension.apiLevels.isNotEmpty()) {
                if (apiLevel != null) {
                    throw GradleException("Cannot set both `apiLevel` and `apiLevels`")
                }
            } else {
                val default =
                    apiLevel ?: extensions[T::class].defaultConfig.minSdkVersion!!.apiLevel
                cargoExtension.apiLevels = cargoExtension.targets!!.associateWith { default }
            }
            val missingApiLevelTargets =
                cargoExtension.targets!!.toSet().minus(cargoExtension.apiLevels.keys)
            if (missingApiLevelTargets.isNotEmpty()) {
                throw GradleException("`apiLevels` missing entries for: $missingApiLevelTargets")
            }

            extensions[T::class].apply {
                val buildDir by layout.buildDirectory
                sourceSets.getByName("main").jniLibs.srcDir(File("$buildDir/rustJniLibs/android"))
                sourceSets.getByName("test").resources.srcDir(File("$buildDir/rustJniLibs/desktop"))
            }

            // Determine the NDK version, if present
            val ndk =
                extensions[T::class].ndkDirectory.let {
                    val ndkSourceProperties = Properties()
                    val ndkSourcePropertiesFile = File(it, "source.properties")
                    if (ndkSourcePropertiesFile.exists()) {
                        ndkSourceProperties.load(ndkSourcePropertiesFile.inputStream())
                    }
                    val ndkVersion = ndkSourceProperties.getProperty("Pkg.Revision", "0.0")
                    Ndk(path = it, version = ndkVersion)
                }

            // Fish linker wrapper scripts from our Java resources.
            val generateLinkerWrapper = rootProject.tasks.maybeCreate("generateLinkerWrapper", Sync::class.java).apply {
                group = RUST_TASK_GROUP
                description = "Generate shared linker wrapper script"
            }

            val rootBuildDir by rootBuildDirectory()
            generateLinkerWrapper.apply {
                // From https://stackoverflow.com/a/320595.
                from(rootProject.zipTree(File(RustAndroidPlugin::class.java.protectionDomain.codeSource.location.toURI()).path))
                include("**/linker-wrapper*")
                into(File(rootBuildDir, "linker-wrapper"))
                eachFile {
                    it.path = it.path.replaceFirst("com/nishtahir", "")
                }
                filePermissions {
                    it.unix("755")
                }
                includeEmptyDirs = false
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }

            val buildTask =
                tasks.maybeCreate("cargoBuild", DefaultTask::class.java).apply {
                    group = RUST_TASK_GROUP
                    description = "Build library (all targets)"
                }

            cargoExtension.targets!!.forEach { target ->
                val theToolchain =
                    toolchains
                        .find { it.platform == target }
                if (theToolchain == null) {
                    throw GradleException(
                        "Target $target is not recognized (recognized targets: ${
                            toolchains.map { it.platform }.sorted()
                        }).  Check `local.properties` and `build.gradle`.",
                    )
                }

                val targetBuildTask =
                    tasks
                        .maybeCreate(
                            "cargoBuild${target.capitalized()}",
                            CargoBuildTask::class.java,
                        )
                        .apply {
                            group = RUST_TASK_GROUP
                            description = "Build library ($target)"
                            toolchain.set(theToolchain)
                            projectProjectDir.set(project.project.projectDir)
                            rootBuildDirectory.set(rootBuildDirectory())
                            // CARGO_TARGET_DIR can be used to force the use of a global, shared
                            // target
                            // directory
                            // across all rust projects on a machine. Use it if it's set, otherwise
                            // use the
                            // configured `targetDirectory` value, and fall back to
                            // `${module}/target`.
                            //
                            // We also allow this to be specified in `local.properties`, not because
                            // this is
                            // something you should ever need to do currently, but we don't want it
                            // to ruin
                            // anyone's
                            // day if it turns out we're wrong about that.

                            this.target.set(
                                cargoExtension.getProperty(
                                    "rust.cargoTargetDir",
                                    "CARGO_TARGET_DIR",
                                )
                                    ?: cargoExtension.targetDirectory
                                    ?: "${cargoExtension.module!!}/target",
                            )

                            rustcCommand.set(cargoExtension.rustcCommand)
                            cargoCommand.set(cargoExtension.cargoCommand)
                            profile.set(cargoExtension.profile)
                            targetIncludes.set(cargoExtension.targetIncludes?.toList())
                            libname.set(cargoExtension.libname)
                            rustupChannel.set(cargoExtension.rustupChannel)
                            verbose.set(cargoExtension.verbose)
                            featureSpec.set(cargoExtension.featureSpec)
                            toolchainDirectory.set(cargoExtension.toolchainDirectory)
                            generateBuildId.set(cargoExtension.generateBuildId)
                            extraCargoBuildArguments.set(cargoExtension.extraCargoBuildArguments)
                            pythonCommand.set(cargoExtension.pythonCommand)

                            this.apiLevel.set(cargoExtension.apiLevels[theToolchain.platform]!!)
                            module.set(cargoExtension.module)

                            this.ndk.set(ndk)
                        }

                buildTask.dependsOn(targetBuildTask)

                targetBuildTask.dependsOn(generateLinkerWrapper)
            }
        }
}
