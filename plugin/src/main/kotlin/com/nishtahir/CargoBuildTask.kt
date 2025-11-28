package com.nishtahir

import com.android.build.gradle.*
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class CargoBuildTask : DefaultTask() {
    //    @Input
    //    val plugins = property<Array<Plugin<Any>>>()

    @Input val toolchain = property<Toolchain>()

    @Input val ndk = property<Ndk>()

    @Input val rootBuildDirectory = property<File>()

    @Input val projectProjectDir = property<File>()

    @Input val target = property<String>()

    @Input val rustcCommand = property<String>()

    @Input val cargoCommand = property<String>()

    @Input val profile = property<String>()

    @Input val targetIncludes = listProperty<String>()

    @Input val libname = property<String>()

    @Input val apiLevel = property<Int>()

    @Input val module = property<String>()

    @Input val rustupChannel = property<String>()

    @Input @Optional val verbose = property<Boolean>()

    @Input val featureSpec = property<FeatureSpec>()

    @Input val toolchainDirectory = property<File>()

    @Input val generateBuildId = property<Boolean>()

    @Input val pythonCommand = property<String>()

    @Input val extraCargoBuildArguments = listProperty<String>()

    @get:Inject abstract val projectLayout: ProjectLayout

    @get:Inject abstract val execOperations: ExecOperations
    @get:Inject abstract val fs: FileSystemOperations

    @Suppress("unused")
    @TaskAction
    fun build() {
        val toolchain by toolchain
        val ndk by ndk

        buildProjectForTarget<LibraryExtension>(toolchain, ndk)
        //        plugins.get().all {
        //            when (it) {
        //                is AppPlugin -> buildProjectForTarget<AppExtension>(
        //                    toolchain,
        //                    ndk,
        //                )
        //
        //                is LibraryPlugin -> buildProjectForTarget<LibraryExtension>(
        //                    toolchain,
        //                    ndk,
        //                )
        //            }
        //        }

        val defaultTargetTriple =
            getDefaultTargetTriple(this@CargoBuildTask, execOperations, rustcCommand.get())

        var cargoOutputDir =
            File(
                if (toolchain.target == defaultTargetTriple) {
                    "${target.get()}/${profile.get()}"
                } else {
                    "${target.get()}/${toolchain.target}/${profile.get()}"
                }
            )
        if (!cargoOutputDir.isAbsolute) {
            cargoOutputDir = File(projectProjectDir.get(), cargoOutputDir.path)
        }
        cargoOutputDir = cargoOutputDir.canonicalFile

        val buildDir by projectLayout.buildDirectory.asFile
        val intoDir = File(buildDir, "rustJniLibs/${toolchain.folder}")
        intoDir.mkdirs()

        fs.copy { spec ->
            spec.from(cargoOutputDir)
            spec.into(intoDir)

            // Need to capture the value to dereference smoothly.
            val targetIncludes = targetIncludes.get()
            if (targetIncludes != null) {
                spec.include(targetIncludes.asIterable())
            } else {
                // It's safe to unwrap, since we bailed at configuration time if this is unset.
                val libname = libname.get()!!
                spec.include("lib${libname}.so")
                spec.include("lib${libname}.dylib")
                spec.include("${libname}.dll")
            }
        }
    }

    private inline fun <reified T : BaseExtension> CargoBuildTask.buildProjectForTarget(
        toolchain: Toolchain,
        ndk: Ndk,
    ) {
        val defaultTargetTriple =
            getDefaultTargetTriple(this@CargoBuildTask, execOperations, rustcCommand.get())

        execOperations
            .exec { spec ->
                with(spec) {
                    standardOutput = System.out
                    val module = File(module.get()!!)
                    workingDir =
                        if (module.isAbsolute) {
                            module
                        } else {
                            File(project.project.projectDir, module.path)
                        }
                    workingDir = workingDir.canonicalFile

                    val theCommandLine = mutableListOf(cargoCommand.get())

                    if (rustupChannel.get().isNotEmpty()) {
                        val hasPlusSign = rustupChannel.get().startsWith("+")
                        val maybePlusSign = if (!hasPlusSign) "+" else ""

                        theCommandLine.add(maybePlusSign + rustupChannel.get())
                    }

                    theCommandLine.add("build")

                    // Respect `verbose` if it is set; otherwise, log if asked to
                    // with `--info` or `--debug` from the command line.
                    if (verbose.getOrElse(false) ?: logger.isEnabled(LogLevel.INFO)) {
                        theCommandLine.add("--verbose")
                    }

                    // We just pass this along to cargo as something space separated... AFAICT
                    // you're allowed to have featureSpec with spaces in them, but I don't think
                    // there's a way to specify them in the cargo command line -- rustc accepts
                    // them if passed in directly with `--cfg`, and cargo will pass them to rustc
                    // if you use them as default featureSpec.
                    when (val features = featureSpec.get().features) {
                        is Features.All -> {
                            theCommandLine.add("--all-features")
                        }

                        is Features.DefaultAnd -> {
                            if (features.featureSet.isNotEmpty()) {
                                theCommandLine.add("--features")
                                theCommandLine.add(features.featureSet.joinToString(" "))
                            }
                        }

                        is Features.NoDefaultBut -> {
                            theCommandLine.add("--no-default-features")
                            if (features.featureSet.isNotEmpty()) {
                                theCommandLine.add("--features")
                                theCommandLine.add(features.featureSet.joinToString(" "))
                            }
                        }

                        null -> {}
                    }

                    when (profile.get()) {
                        "debug" -> {} // debug is the default
                        "release" -> theCommandLine.add("--release")
                        else -> theCommandLine.add("--profile=${profile.get()}")
                    }

                    if (toolchain.target != defaultTargetTriple) {
                        // Only providing --target for the non-default targets means desktop builds
                        // can share the build cache with `cargo build`/`cargo test`/etc
                        // invocations,
                        // instead of requiring a large amount of redundant work.
                        theCommandLine.add("--target=${toolchain.target}")
                    }

                    // Target-specific environment configuration, passed through to
                    // the underlying `cargo build` invocation.
                    val toolchainTarget = toolchain.target.uppercase().replace('-', '_')

                    // TODO REMOVE PROJECT USAGE
                    //                val prefix = "RUST_ANDROID_GRADLE_TARGET_${toolchainTarget}_"
                    //
                    //                // For
                    // ORG_GRADLE_PROJECT_RUST_ANDROID_GRADLE_TARGET_x_KEY=VALUE, set
                    // KEY=VALUE.
                    //                task.logger.info("Passing through project properties with
                    // prefix
                    // '${prefix}' (environment variables with prefix
                    // 'ORG_GRADLE_PROJECT_${prefix}'")
                    //                project.properties.forEach { (key, value) ->
                    //                    if (key.startsWith(prefix)) {
                    //                        val realKey = key.substring(prefix.length)
                    //                        project.logger.debug(
                    //                            "Passing through environment variable '{}' as
                    // '{}={}'",
                    //                            key,
                    //                            realKey,
                    //                            value,
                    //                        )
                    //                        environment(realKey, value)
                    //                    }
                    //                }

                    // Cross-compiling to Android requires toolchain massaging.
                    if (toolchain.type != ToolchainType.DESKTOP) {
                        val ndkPath = ndk.path
                        val ndkVersionMajor = ndk.versionMajor
                        val buildDir = rootBuildDirectory.get()

                        val toolchainDirectory =
                            if (toolchain.type == ToolchainType.ANDROID_PREBUILT) {
                                environment("CARGO_NDK_MAJOR_VERSION", ndkVersionMajor)

                                val hostTag =
                                    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                                        if (Os.isArch("x86_64") || Os.isArch("amd64")) {
                                            "windows-x86_64"
                                        } else {
                                            "windows"
                                        }
                                    } else if (Os.isFamily(Os.FAMILY_MAC)) {
                                        "darwin-x86_64"
                                    } else {
                                        "linux-x86_64"
                                    }
                                File("$ndkPath/toolchains/llvm/prebuilt", hostTag)
                            } else {
                                toolchainDirectory.get()
                            }

                        val linkerWrapper =
                            if (System.getProperty("os.name").startsWith("Windows")) {
                                File(buildDir, "linker-wrapper/linker-wrapper.bat")
                            } else {
                                File(buildDir, "linker-wrapper/linker-wrapper.sh")
                            }
                        environment("CARGO_TARGET_${toolchainTarget}_LINKER", linkerWrapper.path)

                        val cc = File(toolchainDirectory, "${toolchain.cc(apiLevel.get())}").path
                        val cxx = File(toolchainDirectory, "${toolchain.cxx(apiLevel.get())}").path
                        val ar =
                            File(
                                    toolchainDirectory,
                                    "${toolchain.ar(apiLevel.get(), ndkVersionMajor)}",
                                )
                                .path

                        // For build.rs in `cc` consumers: like "CC_i686-linux-android".  See
                        // https://github.com/alexcrichton/cc-rs#external-configuration-via-environment-variables.
                        environment("CC_${toolchain.target}", cc)
                        environment("CXX_${toolchain.target}", cxx)
                        environment("AR_${toolchain.target}", ar)

                        // TODO Not supported for now
                        // Set CLANG_PATH in the environment, so that bindgen (or anything
                        // else using clang-sys in a build.rs) works properly, and doesn't
                        // use host headers and such.
                        //                    val shouldConfigure = cargoExtension.getFlagProperty(
                        //                        "rust.autoConfigureClangSys",
                        //                        "RUST_ANDROID_GRADLE_AUTO_CONFIGURE_CLANG_SYS",
                        //                        // By default, only do this for non-desktop
                        // platforms. If
                        // we're
                        //                        // building for desktop, things should work out of
                        // the box.
                        //                        true,
                        //                    )
                        //                    if (shouldConfigure) {
                        environment("CLANG_PATH", cc)
                        //                    }

                        // Configure our linker wrapper.
                        environment("RUST_ANDROID_GRADLE_PYTHON_COMMAND", pythonCommand.get())
                        environment(
                            "RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY",
                            File(buildDir, "linker-wrapper/linker-wrapper.py").path,
                        )
                        environment("RUST_ANDROID_GRADLE_CC", cc)
                        environment(
                            "RUST_ANDROID_GRADLE_CC_LINK_ARG",
                            buildString {
                                append(
                                    "-Wl,-z,max-page-size=16384,-soname,lib${libname.get()!!}.so"
                                )
                                if (generateBuildId.get()) append(",--build-id")
                            },
                        )
                    }

                    extraCargoBuildArguments.get().let { theCommandLine.addAll(it) }

                    commandLine = theCommandLine
                }
                //            if (cargoExtension.exec != null) {
                //                (cargoExtension.exec!!)(spec, toolchain)
                //            }
            }
            .assertNormalExitValue()
    }
}

// This can't be private/internal as it's called from `buildProjectForTarget`.
fun getDefaultTargetTriple(task: Task, execOperations: ExecOperations, rustc: String): String? {
    val stdout = ByteArrayOutputStream()
    val result =
        execOperations.exec { spec ->
            spec.standardOutput = stdout
            spec.commandLine = listOf(rustc, "--version", "--verbose")
        }
    if (result.exitValue != 0) {
        task.logger.warn(
            "Failed to get default target triple from rustc (exit code: ${result.exitValue})"
        )
        return null
    }
    val output = stdout.toString()

    // The `rustc --version --verbose` output contains a number of lines like `key: value`.
    // We're only interested in `host: `, which corresponds to the default target triple.
    val triplePrefix = "host: "

    val triple =
        output
            .split("\n")
            .find { it.startsWith(triplePrefix) }
            ?.substring(triplePrefix.length)
            ?.trim()

    if (triple == null) {
        task.logger.warn(
            "Failed to parse `rustc -Vv` output! (Please report a rust-android-gradle bug)"
        )
    } else {
        task.logger.info("Default rust target triple: $triple")
    }
    return triple
}
