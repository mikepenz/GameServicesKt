import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
    explicitApi()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    android {
        namespace = "com.mikepenz.gameservices.social"
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.gameServicesCore)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
