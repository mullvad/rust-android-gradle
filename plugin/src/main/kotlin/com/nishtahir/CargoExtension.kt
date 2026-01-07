package com.nishtahir

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

sealed class Features {
    class All() : Features()

    data class DefaultAnd(val featureSet: Set<String>) : Features()

    data class NoDefaultBut(val featureSet: Set<String>) : Features()
}

@Suppress("unused")
data class FeatureSpec(val features: Features? = null) {
    fun all(): FeatureSpec = FeatureSpec(Features.All())

    fun defaultAnd(vararg featureSet: String): FeatureSpec =
        FeatureSpec(Features.DefaultAnd(featureSet.toSet()))

    fun noDefaultBut(vararg featureSet: String): FeatureSpec =
        FeatureSpec(Features.NoDefaultBut(featureSet.toSet()))
}

// `CargoExtension` is documented in README.md.
open class CargoExtension {
    lateinit var localProperties: Properties

    var module: String? = null
    var libname: String? = null
    var targets: List<String>? = null
    var profile: String = "debug"
    var verbose: Boolean? = null
    var targetDirectory: String? = null
    var targetIncludes: Array<String>? = null
    var apiLevel: Int? = null
    var apiLevels: Map<String, Int> = mapOf()
    var extraCargoBuildArguments: List<String>? = null
    var generateBuildId: Boolean = false
    var pythonCommand: String = ""
        get() {
            return field.ifEmpty {
                getProperty("rust.pythonCommand", "RUST_ANDROID_GRADLE_PYTHON_COMMAND") ?: "python"
            }
        }

    var featureSpec: FeatureSpec = FeatureSpec()

    @Suppress("unused")
    fun features(action: FeatureSpec.() -> FeatureSpec) {
        featureSpec = action(FeatureSpec())
    }

    val toolchainDirectory: File
        get() {
            // Share a single toolchain directory, if one is configured.  Prefer "local.properties"
            // to "ANDROID_NDK_TOOLCHAIN_DIR" to "$TMP/rust-android-ndk-toolchains".
            val local: String? = localProperties.getProperty("rust.androidNdkToolchainDir")
            if (local != null) {
                return File(local).absoluteFile
            }

            val globalDir: String? = System.getenv("ANDROID_NDK_TOOLCHAIN_DIR")
            if (globalDir != null) {
                return File(globalDir).absoluteFile
            }

            val defaultDir =
                File(System.getProperty("java.io.tmpdir"), "rust-android-ndk-toolchains")
            return defaultDir.absoluteFile
        }

    var cargoCommand: String = ""
        get() {
            return field.ifEmpty {
                getProperty("rust.cargoCommand", "RUST_ANDROID_GRADLE_CARGO_COMMAND") ?: "cargo"
            }
        }

    var rustupChannel: String? = null
        get() {
            return if (field == null)
                getProperty("rust.rustupChannel", "RUST_ANDROID_GRADLE_RUSTUP_CHANNEL")
            else field
        }

    val autoConfigureClangSys: Boolean
        get() =
            getFlagProperty(
                "rust.autoConfigureClangSys",
                "RUST_ANDROID_GRADLE_AUTO_CONFIGURE_CLANG_SYS",
                // By default, only do this for non-desktop platforms. If we're
                // building for desktop, things should work out of the box.
                true,
            )

    // Required so that we can parse the default triple out of `rustc --version --verbose`. Sadly,
    // there seems to be no way to get this information out of cargo directly. Failure to locate
    // this isn't fatal, however.
    var rustcCommand: String = ""
        get() {
            return field.ifEmpty {
                getProperty("rust.rustcCommand", "RUST_ANDROID_GRADLE_RUSTC_COMMAND") ?: "rustc"
            }
        }

    fun getFlagProperty(camelCaseName: String, snakeCaseName: String, ifUnset: Boolean): Boolean {
        val propVal = getProperty(camelCaseName, snakeCaseName)
        if (propVal == "1" || propVal == "true") {
            return true
        }
        if (propVal == "0" || propVal == "false") {
            return false
        }
        if (propVal == null || propVal == "") {
            return ifUnset
        }
        throw GradleException(
            "Illegal value for property \"$camelCaseName\" / \"$snakeCaseName\". Must be 0/1/true/false if set"
        )
    }

    internal fun getProperty(camelCaseName: String, snakeCaseName: String): String? {
        val local: String? = localProperties.getProperty(camelCaseName)
        if (local != null) {
            return local
        }
        val global: String? = System.getenv(snakeCaseName)
        if (global != null) {
            return global
        }
        return null
    }
}
