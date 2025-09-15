plugins {
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.version.catalog.update)
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }

