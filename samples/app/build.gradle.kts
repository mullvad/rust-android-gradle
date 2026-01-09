plugins {
    id("com.android.application") version("8.12.3")
    id("net.mullvad.rust-android")
}

android {
    namespace = "net.mullvad.androidrust"
    compileSdk = 35
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "net.mullvad.androidrust"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
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
    implementation("com.android.support.constraint:constraint-layout:2.0.4")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    fun CharSequence.capitalized() =
        toString().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    android.applicationVariants.forEach { variant ->
        val productFlavor = variant.productFlavors.joinToString("") { it.name.capitalized() }
        val buildType = variant.buildType.name.capitalized()
        tasks["generate${productFlavor}${buildType}Assets"].dependsOn(tasks["cargoBuild"])
    }
}
