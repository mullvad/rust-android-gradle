plugins {
  id("com.android.application") version ("9.0.0")
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
//      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
  }

    ndkPath =
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
  androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2") {
    exclude("com.android.support:support-annotations")
  }
  implementation("com.android.support:appcompat-v7:28.0.0")
  implementation("com.android.support.constraint:constraint-layout:2.0.4")
  testImplementation("junit:junit:4.13.2")
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android").get()
tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    inputs.dir(rustJniLibsDir)
    dependsOn("cargoBuild")
}
