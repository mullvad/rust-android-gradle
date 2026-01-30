package net.mullvad.androidrust

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

val systemDefaultAndroidSdkHome = run {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")

    if (os.contains("win")) {
        val localappdata = System.getenv("LOCALAPPDATA")
        File("$localappdata\\Android\\Sdk")
    } else if (os.contains("osx")) {
        File("$home/Library/Android/sdk")
    } else {
        File("$home/Android/sdk")
    }
}

class RunGradleTask(
    val gradleVersion: GradleVersion,
    val projectDir: File,
    val taskName: String,
    val arguments: List<String> = listOf("--info", "--stacktrace"),
) {
    private val environment = run {
        if (System.getenv("ANDROID_HOME").isNullOrBlank()) {
            val sdk = systemDefaultAndroidSdkHome.absolutePath.replace('\\', '/')
            System.getenv() + mapOf("ANDROID_HOME" to sdk)
        } else {
            System.getenv()
        }
    }

    fun build(): BuildResult =
        GradleRunner.create()
            .withEnvironment(environment)
            .withGradleVersion(gradleVersion.version)
            .forwardOutput()
            .withProjectDir(projectDir)
            .withArguments(listOf(taskName) + arguments)
            .build()
}
