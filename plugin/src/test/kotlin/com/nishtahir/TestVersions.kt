package com.nishtahir

import org.gradle.util.GradleVersion

object TestVersions {
    fun allCandidateTestVersions(): Map<VersionNumber, Set<GradleVersion>> {
        val testedVersion = System.getProperty("org.gradle.android.testVersion")
        return if (!testedVersion.isNullOrEmpty()) {
            val parsedVersion = VersionNumber.parse(testedVersion)
            Versions.SUPPORTED_VERSIONS_MATRIX.filter { it.key == parsedVersion }
        } else {
            Versions.SUPPORTED_VERSIONS_MATRIX
        }
    }

    fun latestAndroidVersionForCurrentJDK(): VersionNumber {
        val current = System.getProperty("java.version")
        println("Current JDK: $current")
        val version7 = VersionNumber.parse("7.0.0")
        println("Version 7: $version7")
        return if (current.startsWith("1.")) {
            allCandidateTestVersions().keys.filter { it < version7 }.maxOrNull()!!
        } else {
            allCandidateTestVersions().keys.maxOrNull().let {
                if (it == null) {
                    println()
                }
                it!!
            }
        }
    }

    fun latestGradleVersion() = allCandidateTestVersions().values.flatten().maxOrNull()!!

    fun latestAndroidVersions() =
        allCandidateTestVersions().keys.map { getLatestVersionForAndroid("${it.major}.${it.minor}") }

    fun latestSupportedGradleVersionFor(androidVersion: VersionNumber) =
        allCandidateTestVersions().entries
            .find { it.key.major == androidVersion.major && it.key.minor == androidVersion.minor }
            ?.value
            ?.maxOrNull()!!

    val latestKotlinVersion = VersionNumber.parse("2.3.0")

    private fun getLatestVersionForAndroid(version: String): VersionNumber {
        val number = VersionNumber.parse(version)
        return allCandidateTestVersions().keys
            .filter { it.major == number.major && it.minor == number.minor }
            .maxOrNull()!!
    }
}
