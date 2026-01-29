plugins {
    id("com.android.application") version ("9.0.0")
    id("net.mullvad.rust-android")
}

android {
    namespace = "net.mullvad.androidrust"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "net.mullvad.androidrust"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main")
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

androidComponents {
    onVariants { variant ->
        variant.sources
            .getByName("test")
            .addStaticSourceDirectory(layout.buildDirectory.get().dir("rustJniLibs/desktop").asFile.path)
    }
}

cargo {
    module = "../rust"
    /*
    For MacBook we need to change the target
    targets = listOf("darwin-aarch64")
     */
    targets = listOf("x86_64", "linux-x86-64")
    libname = "rust"
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

// There's an interaction between Gradle's resolution of dependencies with different types
// (@jar, @aar) for `implementation` and `testImplementation` and with Android Studio's built-in
// JUnit test runner.  The runtime classpath in the built-in JUnit test runner gets the
// dependency from the `implementation`, which is type @aar, and therefore the JNA dependency
// doesn't provide the JNI dispatch libraries in the correct Java resource directories.  I think
// what's happening is that @aar type in `implementation` resolves to the @jar type in
// `testImplementation`, and that it wins the dependency resolution battle.
//
// A workaround is to add a new configuration which depends on the @jar type and to reference
// the underlying JAR file directly in `testImplementation`.  This JAR file doesn't resolve to
// the @aar type in `implementation`.  This works when invoked via `gradle`, but also sets the
// correct runtime classpath when invoked with Android Studio's built-in JUnit test runner.
// Success!
val jnaForTest by configurations.creating

dependencies {
    jnaForTest("net.java.dev.jna:jna:5.18.1@jar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2") {
        exclude("com.android.support:support-annotations")
    }
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation("junit:junit:4.13.2")

    // For reasons unknown, resolving the jnaForTest configuration directly
    // trips a nasty issue with the Android-Gradle plugin 3.2.1, like `Cannot
    // change attributes of configuration ':PROJECT:kapt' after it has been
    // resolved`.  I think that the configuration is being made a
    // super-configuration of the testImplementation and then the `.files` is
    // causing it to be resolved.  Cloning first dissociates the configuration,
    // avoiding other configurations from being resolved.  Tricky!
    testImplementation(files(jnaForTest.copyRecursive().files))
    // testImplementation("androidx.test.ext:junit:$versions.androidx_junit")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()

// Don't merge the jni lib folders until after the Rust libraries have been built.
tasks
    .matching { it.name.matches(Regex("merge.*JniLibFolders")) }
    .configureEach {
        inputs.dir(rustJniLibsDir)
        dependsOn("cargoBuild")
    }

// For unit tests.
val rustJniLibsDesktopDir = layout.buildDirectory.dir("rustJniLibs/desktop").get()

tasks
    .matching { it.name.matches(Regex("process.*UnitTestJavaRes")) }
    .configureEach {
        inputs.dir(rustJniLibsDesktopDir)
        dependsOn("cargoBuild")
    }
