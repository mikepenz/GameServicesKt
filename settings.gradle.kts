rootProject.name = "GameServicesKt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
        mavenLocal()
    }

    versionCatalogs {
        create("baseLibs") {
            from("com.mikepenz:version-catalog:0.21.0")
        }
    }
}

include(
    ":game-services-core",
    ":game-services-achievements",
    ":game-services-leaderboards",
    ":game-services-saved-games",
    ":game-services-social",
    ":sample",
    ":sample-host-android",
    ":local-maven-consumer",
)

include(
    ":game-services-play-games-core",
    ":game-services-play-games-achievements",
    ":game-services-play-games-leaderboards",
    ":game-services-play-games-saved-games",
    ":game-services-play-games-social",
    ":game-services-game-center-core",
    ":game-services-game-center-achievements",
    ":game-services-game-center-leaderboards",
    ":game-services-game-center-saved-games",
    ":game-services-game-center-social",
)

include(":game-services-game-center-bridge")

include(":sample-host-desktop")

include(":game-services-recall", ":game-services-play-games-native")

