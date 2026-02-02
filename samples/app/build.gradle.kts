plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("net.mullvad.rust-android")
}

android {
    namespace = "net.mullvad.androidrust"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "net.mullvad.androidrust"
        minSdk = 23
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures { compose = true }
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

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()!!
tasks
    .matching { it.name.matches(Regex("merge.*JniLibFolders")) }
    .configureEach {
        inputs.dir(rustJniLibsDir)
        dependsOn("cargoBuild")
    }

