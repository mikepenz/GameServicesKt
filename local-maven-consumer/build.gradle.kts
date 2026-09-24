import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
}

kotlin {
    explicitApi()
    androidNativeArm64()
    androidNativeX64()
    mingwX64()
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
        androidNativeMain.dependencies {
            implementation("com.mikepenz:game-services-play-games-native:$version")
        }
        mingwX64Main.dependencies {
            implementation("com.mikepenz:game-services-play-games-pc:$version")
        }
        androidMain.dependencies {
            implementation("com.mikepenz:game-services-play-games-achievements:$version")
        }
        iosMain.dependencies {
            implementation("com.mikepenz:game-services-game-center-achievements:$version")
        }
        jvmMain.dependencies {
            implementation("com.mikepenz:game-services-game-center-achievements:$version")
            implementation("com.mikepenz:game-services-play-games-pc:$version")
        }
        commonMain.dependencies {
            implementation("com.mikepenz:game-services-core:$version")
            implementation("com.mikepenz:game-services-achievements:$version")
            implementation("com.mikepenz:game-services-leaderboards:$version")
            implementation("com.mikepenz:game-services-saved-games:$version")
            implementation("com.mikepenz:game-services-social:$version")
        }
    }
}
