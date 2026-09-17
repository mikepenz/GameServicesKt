import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.compose")
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
        binaries.executable()
    }
    // ponytail: Skiko cannot run under Node; browser tests keep Wasm coverage until that changes.
    tasks.named("wasmJsNodeTest") { enabled = false }

    android {
        namespace = "com.mikepenz.gameservices.sample"
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(baseLibs.jetbrains.compose.foundation)
            implementation(baseLibs.jetbrains.compose.material3)
            implementation(baseLibs.jetbrains.compose.runtime)
            implementation(baseLibs.jetbrains.compose.ui)
            implementation("io.coil-kt.coil3:coil-compose:3.6.0")
            implementation(projects.gameServicesAchievements)
            implementation(projects.gameServicesLeaderboards)
            implementation(projects.gameServicesSavedGames)
            implementation(projects.gameServicesSocial)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(compose.desktop.currentOs)
            implementation(baseLibs.jetbrains.compose.material3)
            implementation(baseLibs.jetbrains.compose.ui.test)
        }
    }
}
