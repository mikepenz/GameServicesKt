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
    macosArm64()
    @Suppress("DEPRECATION")
    macosX64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()

    sourceSets {
        val appleUiMain = create("appleUiMain") { dependsOn(appleMain.get()) }
        iosMain.get().dependsOn(appleUiMain)
        tvosMain.get().dependsOn(appleUiMain)
        macosMain.get().dependsOn(appleUiMain)
        commonMain.dependencies {
            api(project(":game-services-achievements"))
            api(project(":game-services-game-center-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(baseLibs.kotlinx.coroutines.test)
        }
    }
}
