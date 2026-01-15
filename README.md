# Rust Android Gradle Plugin

Cross compile Rust Cargo projects for Android targets.

This is a fork of the original [mozilla/rust-android-gradle](https://github.com/mozilla/rust-android-gradle)
with a focus on up-to-date Gradle plugin authoring practices. Support for older AGP versions is sacrificed
in favor of focusing on Gradle 9.0 and later, which removes several APIs that were relied upon by the upstream plugin.

<p align="left">
    <a alt="Version badge" href="https://plugins.gradle.org/plugin/net.mullvad.rust-android">
        <img src="https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/net/mullvad/rust-android/net.mullvad.rust-android.gradle.plugin/maven-metadata.xml.svg?label=rust-android-gradle&colorB=brightgreen" /></a>
</p>

# Usage

## Setup

Ensure that your `settings.gradle.kts` includes the `gradlePluginPortal()` repository:

```kotlin
pluginManagement {
   repositories {
      /* other repos */
      gradlePluginPortal()
   }
}
```

In your project's `build.gradle.kts`, declare the `rust-android-gradle` plugin in your `plugins` block and include the `cargo` block:

```kotlin
plugins {
    id("net.mullvad.rust-android") version("0.10.0")
}

cargo {
    module = "../rust"
    libname = "rust"
    targets = listOf("x86_64", "arm64")
}
```

Install the Rust targets corresponding to your `cargo.targets`, e.g. in this case:
```sh
rustup target add x86_64-linux-android
rustup target add aarch64-linux-android
```

Now you need to make sure that the `cargoBuild` task is a dependency of your `merge*JniLibFolders` tasks through the following segment in your `build.gradle.kts`:

```kotlin
val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()
tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    inputs.dir(rustJniLibsDir)
    dependsOn("cargoBuild")
}
```

## Supported targets

There are two kinds of targets, **desktop** targets and **Android** targets. Android targets are designed for inclusion in an Android app at runtime,
whereas desktop targets are useful for running unit tests on a local machine. Better support for desktop targets in unit tests is [planned](https://github.com/ncalexan/rust-android-gradle/issues/13).
In the meantime, see [the unittest example](examples/unittest/build.gradle.kts) for more guidance on using a desktop target to run Java unit tests with Rust code.

<table><thead>
  <tr>
    <th>OS</th>
    <th>Arch</th>
    <th>Rust target</th>
    <th><code>build.gradle</code> target</th>
  </tr></thead>
<tbody>
  <tr>
    <td rowspan="4">Android</td>
    <td>x86_64</td>
    <td><code>x86_64-linux-android</code></td>
    <td><code>x86_64</code></td>
  </tr>
  <tr>
    <td>arm64</td>
    <td><code>aarch64-linux-android</code></td>
    <td><code>arm64</code></td>
  </tr>
  <tr>
    <td>x86</td>
    <td><code>i686-linux-android</code></td>
    <td><code>x86</code></td>
  </tr>
  <tr>
    <td>armv7</td>
    <td><code>armv7-linux-androideabi</code></td>
    <td><code>arm</code></td>
  </tr>
  <tr>
    <td>Linux</td>
    <td>x86_64</td>
    <td><code>x86_64-unknown-linux-gnu</code></td>
    <td><code>linux-x86-64</code></td>
  </tr>
  <tr>
    <td rowspan="2">MacOS</td>
    <td>arm64</td>
    <td><code>aarch64-apple-darwin</code></td>
    <td><code>darwin-aarch64</code></td>
  </tr>
  <tr>
    <td>x86_64</td>
    <td><code>x86_64-apple-darwin</code></td>
    <td><code>darwin-x86-64</code></td>
  </tr>
  <tr>
    <td rowspan="2">Windows</td>
    <td rowspan="2">x86_64</td>
    <td><code>x86_64-pc-windows-msvc</code></td>
    <td><code>win32-x86-64-msvc</code></td>
  </tr>
  <tr>
    <td><code>x86_64-pc-windows-gnu</code></td>
    <td><code>win32-x86-64-gnu</code></td>
  </tr>
</tbody></table>

## Configuration

The `cargo` Gradle configuration accepts many options.

### Linking Java code to native libraries

Generated libraries will be added to the Android `jniLibs` source-sets, when correctly referenced in
the `cargo` configuration through the `libname` and/or `targetIncludes` options.  The latter
defaults to `["lib${libname}.so", "lib${libname}.dylib", "{$libname}.dll"]`, so the following configuration will
include all `libbackend` libraries generated in the Rust project in `../rust`:

```
cargo {
    module = "../rust"
    libname = "backend"
}
```

Now, Java code can reference the native library using, e.g.,

```java
static {
    System.loadLibrary("backend");
}
```

### Native `apiLevel`

The [Android NDK](https://developer.android.com/ndk/guides/stable_apis) also fixes an API level,
which can be specified using the `apiLevel` option.  This option defaults to the minimum SDK API
level.  As of API level 21, 64-bit builds are possible; and conversely, the `arm64` and `x86_64`
targets require `apiLevel >= 21`.

### Cargo release profile

The `profile` option selects between the `--debug` and `--release` profiles in `cargo`.  *Defaults
to `debug`!*

### Extension reference

### module

The path to the Rust library to build with Cargo; required.  `module` can be absolute; if it is not,
it is interpreted as a path relative to the Gradle `projectDir`.

```kotlin
cargo {
    // Note: path is either absolute, or relative to the gradle project's `projectDir`.
    module = "../rust"
}
```

### libname

The library name produced by Cargo; required.

`libname` is used to determine which native libraries to include in the produced AARs and/or APKs.
See also [`targetIncludes`](#targetincludes).

`libname` is also used to determine the ELF SONAME to declare in the Android libraries produced by
Cargo.  Different versions of the Android system linker
[depend on the ELF SONAME](https://android-developers.googleblog.com/2016/06/android-changes-for-ndk-developers.html).

In `Cargo.toml`:

```toml
[lib]
name = "test"
```

In `build.gradle`:

```kotlin
cargo {
    libname = "test"
}
```

### targets

A list of Android targets to build with Cargo; required.
See [Supported Targets](#supported-targets) for a list of supported values.

```kotlin
cargo {
    /* kotlin */
    targets = listOf("arm", "x86", "linux-x86-64")
}
```

### verbose

When set, execute `cargo build` with or without the `--verbose` flag.  When unset, respect the
Gradle log level: execute `cargo build` with or without the `--verbose` flag according to whether
the log level is at least `INFO`.  In practice, this makes `./gradlew ... --info` (and `./gradlew
... --debug`) execute `cargo build --verbose ...`.

Defaults to `null`.

```kotlin
cargo {
    verbose = true
}
```

### profile

The Cargo [release profile](https://doc.rust-lang.org/book/second-edition/ch14-01-release-profiles.html#customizing-builds-with-release-profiles) to build (custom profiles are also supported).

Defaults to `"debug"`.

```kotlin
cargo {
    profile = 'release'
}
```

### features

Set the Cargo [features](https://doc.rust-lang.org/cargo/reference/manifest.html#the-features-section).

Defaults to passing no flags to `cargo`.

To pass `--all-features`, use
```kotlin
cargo {
    features {
        all()
    }
}
```

To pass an optional list of `--features`, use
```kotlin
cargo {
    features {
        defaultAnd("x")
        defaultAnd("x", "y")
    }
}
```

To pass `--no-default-features`, and an optional list of replacement `--features`, use
```kotlin
cargo {
    features {
        noDefaultBut()
        noDefaultBut("x")
        noDefaultBut("x", "y")
    }
}
```

### targetDirectory

The target directory into which Cargo writes built outputs. You will likely need to specify this
if you are using a [cargo virtual workspace](https://doc.rust-lang.org/book/ch14-03-cargo-workspaces.html),
as our default will likely fail to locate the correct target directory.

Defaults to `${module}/target`.  `targetDirectory` can be absolute; if it is not, it is interpreted
as a path relative to the Gradle `projectDir`.

Note that if `CARGO_TARGET_DIR` (see https://doc.rust-lang.org/cargo/reference/environment-variables.html)
is specified in the environment, it takes precedence over `targetDirectory`, as cargo will output
all build artifacts to it, regardless of what is being built, or where it was invoked.

You may also override `CARGO_TARGET_DIR` variable by setting `rust.cargoTargetDir` in
`local.properties`, however it seems very unlikely that this will be useful, as we don't pass this
information to cargo itself. That said, it can be used to control where we search for the built
library on a per-machine basis.

```kotlin
cargo {
    // Note: path is either absolute, or relative to the gradle project's `projectDir`.
    targetDirectory = "path/to/workspace/root/target"
}
```

### targetIncludes

Which Cargo outputs to consider JNI libraries.

Defaults to `["lib${libname}.so", "lib${libname}.dylib", "{$libname}.dll"]`.

```kotlin
cargo {
    targetIncludes = arrayOf("libnotlibname.so")
}
```

### apiLevel

The Android NDK API level to target.  NDK API levels are not the same as SDK API versions; they are
updated less frequently.  For example, SDK API versions 18, 19, and 20 all target NDK API level 18.

Defaults to the minimum SDK version of the Android project's default configuration.

```kotlin
cargo {
    apiLevel = 21
}
```

You may specify the API level per target in `targets` using the `apiLevels` option. At most one of
`apiLevel` and `apiLevels` may be specified. `apiLevels` must have an entry for each target in
`targets`.

```kotlin
cargo {
    targets = listOf("arm", "x86_64")
    apiLevels = mapOf(
        "arm" to 16,
        "x86_64" to 21
    )
}
```

### extraCargoBuildArguments

Sometimes, you need to do things that the plugin doesn't anticipate.  Use `extraCargoBuildArguments`
to append a list of additional arguments to each `cargo build` invocation.

```kotlin
cargo {
    extraCargoBuildArguments = listOf("--locked")
}
```

### environmentalOverrides

You can set environment variables for the Cargo invocation by setting values in the 
`environmentalOverrides` map (or setting the property to a new map object).


```kotlin
cargo {
    environmentalOverrides["RUSTFLAGS"] = "-Z sanitizer=address"
}
```

Note that environment variables set as described in the [Passing arguments to cargo](#passing-arguments-to-cargo) section
will overwrite variables set with `environmentalOverrides`.

### generateBuildId

Generate a build-id for the shared library during the link phase.

## Specifying local targets

When developing a project that consumes `rust-android-gradle` locally, it's often convenient to
temporarily change the set of Rust target architectures.  In order of preference, the plugin
determines the per-project targets by:

1. `rust.targets.${project.Name}` for each project in `${rootDir}/local.properties`
1. `rust.targets` in `${rootDir}/local.properties`
1. the `cargo { targets ... }` block in the per-project `build.gradle`

The targets are split on `','`.  For example:

```
rust.targets.library=linux-x86-64
rust.targets=arm,linux-x86-64,darwin
```

## Specifying paths to sub-commands (Python, Cargo, and Rustc)

The plugin invokes Python, Cargo and Rustc.  In order of preference, the plugin determines what command to invoke for Python by:

1. the value of `cargo { pythonCommand = "..." }`, if non-empty
1. `rust.pythonCommand` in `${rootDir}/local.properties`
1. the environment variable `RUST_ANDROID_GRADLE_PYTHON_COMMAND`
1. the default, `python`

In order of preference, the plugin determines what command to invoke for Cargo by:

1. the value of `cargo { cargoCommand = "..." }`, if non-empty
1. `rust.cargoCommand` in `${rootDir}/local.properties`
1. the environment variable `RUST_ANDROID_GRADLE_CARGO_COMMAND`
1. the default, `cargo`

In order of preference, the plugin determines what command to invoke for `rustc` by:

1. the value of `cargo { rustcCommand = "..." }`, if non-empty
1. `rust.rustcCommand` in `${rootDir}/local.properties`
1. the environment variable `RUST_ANDROID_GRADLE_RUSTC_COMMAND`
1. the default, `rustc`

(Note that failure to locate `rustc` is not fatal, however it may result in rebuilding the code more often than is necessary).

Paths must be host operating system specific.  For example, on Windows:

```properties
rust.pythonCommand=c:\Python27\bin\python
```

On Linux,
```shell
env RUST_ANDROID_GRADLE_CARGO_COMMAND=$HOME/.cargo/bin/cargo ./gradlew ...
```

## Specifying Rust channel

Rust is released to three different "channels": stable, beta, and nightly (see
https://rust-lang.github.io/rustup/concepts/channels.html).  The `rustup` tool, which is how most
people install Rust, allows multiple channels to be installed simultaneously and to specify which
channel to use by invoking `cargo +channel ...`.

In order of preference, the plugin determines what channel to invoke `cargo` with by:

1. the value of `cargo { rustupChannel = "..." }`, if non-empty
1. `rust.rustupChannel` in `${rootDir}/local.properties`
1. the environment variable `RUST_ANDROID_GRADLE_RUSTUP_CHANNEL`
1. the default, no channel specified (which `cargo` installed via `rustup` generally defaults to the
   `stable` channel)

The channel should be recognized by `cargo` installed via `rustup`, i.e.:
- `"stable"`
- `"beta"`
- `"nightly"`

A single leading `'+'` will be stripped, if present.

(Note that Cargo installed by a method other than `rustup` will generally not understand `+channel`
and builds will likely fail.)

## Passing arguments to cargo

The plugin passes project properties named like `RUST_ANDROID_GRADLE_target_..._KEY=VALUE` through
to the Cargo invocation for the given Rust `target` as `KEY=VALUE`.  Target should be upper-case
with "-" replaced by "_".  (See [the links from this Cargo issue](https://github.com/rust-lang/cargo/issues/5690).) So, for example,

```groovy
project.RUST_ANDROID_GRADLE_I686_LINUX_ANDROID_FOO=BAR
```
and
```shell
./gradlew -PRUST_ANDROID_GRADLE_ARMV7_LINUX_ANDROIDEABI_FOO=BAR ...
```
and
```
env ORG_GRADLE_PROJECT_RUST_ANDROID_GRADLE_ARMV7_LINUX_ANDROIDEABI_FOO=BAR ./gradlew ...
```
all set `FOO=BAR` in the `cargo` execution environment (for the "armv7-linux-androideabi` Rust
target, corresponding to the "x86" target in the plugin).

# Development

At top-level, the `publish` Gradle task updates the Maven repository
under `build/local-repo`:

```
$ ./gradlew publish
...
$ ls -al build/local-repo/org/mozilla/rust-android-gradle/org.mozilla.rust-android-gradle.gradle.plugin/0.4.0/org.mozilla.rust-android-gradle.gradle.plugin-0.4.0.pom
-rw-r--r--  1 nalexander  staff  670 18 Sep 10:09
build/local-repo/org/mozilla/rust-android-gradle/org.mozilla.rust-android-gradle.gradle.plugin/0.4.0/org.mozilla.rust-android-gradle.gradle.plugin-0.4.0.pom
```

## Sample projects

The easiest way to get started is to run the sample projects.  The sample projects have dependency
substitutions configured so that changes made to `plugin/` are reflected in the sample projects
immediately.

```
$ ./gradlew -p samples/library :assembleDebug
...
$ file samples/library/build/outputs/aar/library-debug.aar
samples/library/build/outputs/aar/library-debug.aar: Zip archive data, at least v1.0 to extract
```

```
$ ./gradlew -p samples/app :assembleDebug
...
$ file samples/app/build/outputs/apk/debug/app-debug.apk
samples/app/build/outputs/apk/debug/app-debug.apk: Zip archive data, at least v?[0] to extract
```

## Testing Local changes

An easy way to locally test changes made in this plugin is to simply add this to your project's `settings.gradle`:

```gradle
// Switch this to point to your local plugin dir
includeBuild('../rust-android-gradle') {
    dependencySubstitution {
        // As required.
        substitute module('gradle.plugin.org.mozilla.rust-android-gradle:plugin') with project(':plugin')
    }
}
```

# Publishing

You will need credentials to publish to the [Gradle plugin portal](https://plugins.gradle.org/) in
the appropriate place for the [`plugin-publish`](https://plugins.gradle.org/docs/publish-plugin) to
find them.  Usually, that's in `~/.gradle/gradle.properties`.

At top-level, the `publishPlugins` Gradle task publishes the plugin for consumption:

```
$ ./gradlew publishPlugins
...
Publishing plugin org.mozilla.rust-android-gradle.rust-android version 0.8.1
Publishing artifact build/libs/plugin-0.8.1.jar
Publishing artifact build/libs/plugin-0.8.1-sources.jar
Publishing artifact build/libs/plugin-0.8.1-javadoc.jar
Publishing artifact build/publish-generated-resources/pom.xml
Activating plugin org.mozilla.rust-android-gradle.rust-android version 0.8.1
```

Furthermore, all published artifacts to the Gradle plugin portal must be signed. This is done
through the [`signing`](https://docs.gradle.org/current/userguide/signing_plugin.html#signing_plugin) plugin,
and the following values must be set in e.g. `/.gradle/gradle.properties`:

```
mullvad.rust-android-gradle.codeSigningEnabled=true
signing.gnupg.keyName=[signing key fingerprint]
```

## Real projects

To test in a real project, use the local Maven repository in your `build.gradle`, like:

```gradle
buildscript {
    repositories {
        maven {
            url "file:///Users/nalexander/Mozilla/rust-android-gradle/build/local-repo"
        }
    }

    dependencies {
        classpath 'net.mullvad.rust-android:plugin:0.10.0'
    }
}
```
