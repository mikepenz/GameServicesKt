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
    ":local-maven-consumer",
)
