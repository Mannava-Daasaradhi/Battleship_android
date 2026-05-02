pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "battleship-fleet-command"

include(":app")

include(":feature:menu")
include(":feature:setup")
include(":feature:game")
include(":feature:lobby")
include(":feature:stats")
include(":feature:settings")

include(":core:domain")
include(":core:ai")
include(":core:data")
include(":core:multiplayer")
include(":core:ui")
// :core:ads excluded — adding in a later phase
include(":core:analytics")
include(":core:testing")

include(":benchmark")