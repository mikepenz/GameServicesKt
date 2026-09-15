import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
}

kotlin {
    explicitApi()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    android {
        namespace = "com.mikepenz.gameservices.localconsumer"
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.mikepenz:game-services-core:$version")
            implementation("com.mikepenz:game-services-achievements:$version")
            implementation("com.mikepenz:game-services-leaderboards:$version")
            implementation("com.mikepenz:game-services-saved-games:$version")
            implementation("com.mikepenz:game-services-social:$version")
        }
    }
}
