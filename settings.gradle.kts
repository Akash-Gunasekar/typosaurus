// `-PcoreOnly=true` builds just the pure-Kotlin module (no Android SDK / Google Maven needed).
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

pluginManagement {
    repositories {
        if (providers.gradleProperty("coreOnly").orNull != "true") google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("coreOnly").orNull != "true") google()
        mavenCentral()
    }
}
rootProject.name = "ai-keyboard"
include(":core")
if (!coreOnly) {
    include(":data", ":app")
}
