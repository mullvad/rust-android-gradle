plugins {
    id("com.android.library") version("9.0.0")
    id("net.mullvad.rust-android")
}

version = "1.0"

android {
    namespace = "net.mullvad.library"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    defaultConfig {
        minSdk = 21
        testOptions.targetSdk = 35

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        aarMetadata {
            minCompileSdk = 21
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
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
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2") {
        exclude("com.android.support:support-annotations")
    }
    implementation("com.android.support:appcompat-v7:28.0.0")
    testImplementation("junit:junit:4.13.2")
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()
tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    inputs.dir(rustJniLibsDir)
    dependsOn("cargoBuild")
}
