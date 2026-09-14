pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// No java { toolchain {...} } block anywhere in this project, so the foojay-resolver-convention
// plugin (auto-provisions/downloads a JDK toolchain over the network at Gradle config time) was
// dead weight — and specifically the kind of build-time network access F-Droid's sandboxed build
// server disallows outright, flagged by its scanner as an "insecure-gradlew" finding.

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tcamViewer2"
include(":app")
