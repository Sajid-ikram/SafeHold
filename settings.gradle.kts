pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SafeHold"

include(":app")
include(":core:model")
include(":core:data")
include(":core:designsystem")
include(":core:filetype")
include(":sandbox")
