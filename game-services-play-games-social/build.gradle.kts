import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
    explicitApi()

    android {
        namespace = "com.mikepenz.gameservices.playgames.social"
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":game-services-social"))
            api(project(":game-services-play-games-core"))
        }
        androidMain.dependencies {
            api("androidx.activity:activity:1.13.0")
            implementation("com.google.android.gms:play-services-games-v2:22.0.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(baseLibs.kotlinx.coroutines.test)
        }
    }
}
