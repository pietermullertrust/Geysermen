pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://maven-other.tuya.com/repository/maven-releases/")
        maven(url = "https://maven-other.tuya.com/repository/maven-snapshots/")
        maven(url = "https://maven-other.tuya.com/repository/maven-commercial-releases/")
    }
}

rootProject.name = "GeyserMen"
include(":app")