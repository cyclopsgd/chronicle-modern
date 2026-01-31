pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Chronicle Epilogue - Audiobook Player for Plex"
include(":app")
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:media")
include(":core:sync")
include(":feature:library")
include(":feature:player")
include(":feature:downloads")
include(":feature:settings")
