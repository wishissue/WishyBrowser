pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mozilla's Maven repo: the official source of the GeckoView engine.
        // Restricted to Mozilla's own groups so nothing else can ever be
        // resolved from it.
        maven("https://maven.mozilla.org/maven2/") {
            content { includeGroupAndSubgroups("org.mozilla") }
        }
    }
}

rootProject.name = "WishyBrowser"
include(":app")
