import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
}

kotlin {
    explicitApi()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "GameServicesSample"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    android {
        namespace = "com.mikepenz.gameservices.sample"
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.gameServicesAchievements)
            implementation(projects.gameServicesLeaderboards)
            implementation(projects.gameServicesSavedGames)
            implementation(projects.gameServicesSocial)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
