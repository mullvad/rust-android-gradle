plugins {
    alias(libs.plugins.android.application)
    id("net.mullvad.rust-android")
}

version = "1.0"

android {
    namespace = "net.mullvad.library"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

cargo {
    module = "../rust"
    targets = listOf("x86_64", "arm64")
    libname = "rust"

    features {
        defaultAnd("foo", "bar")
        noDefaultBut("foo", "bar")
        all()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    androidTestImplementation(libs.espresso.core)
    testImplementation(libs.junit)
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()!!
tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    inputs.dir(rustJniLibsDir)
    dependsOn("cargoBuild")
}
