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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.gkonovalov.android-vad") }
        }
    }
}

rootProject.name = "Smoke App"
include(":app")
include(":core:designsystem")
include(":feature:home:impl")
include(":feature:home:api")
include(":feature:tools:api")
include(":feature:tools:impl")
include(":core:voice")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":feature:chatbot:api")
include(":feature:chatbot:impl")
include(":feature:login:impl")
include(":feature:login:api")
include(":feature:metalearning:api")
include(":feature:metalearning:impl")
