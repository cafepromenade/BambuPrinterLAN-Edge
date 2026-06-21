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
    }
}

rootProject.name = "BambuPrinterLan"

// --- Buildable now (Phase 0/1 scaffold) ---
include(":app")
include(":core:design")
include(":core:model")
include(":core:data")
include(":net:bambu")
include(":engine:jni")

// --- Reserved for later phases; uncomment as each module lands with a build file ---
// include(":core:common")
// include(":net:http")
// include(":feature:workspace")
// include(":feature:viewer3d")
// include(":feature:gcodeviewer")
// include(":feature:presets")
// include(":feature:calibration")
// include(":feature:device")
// include(":feature:account")
// include(":feature:printhosts")
// include(":feature:filehub")
// include(":feature:batch")
// include(":feature:modeledit")
// include(":feature:integrations")
// include(":feature:assistant")
// include(":feature:fidgetlab")
