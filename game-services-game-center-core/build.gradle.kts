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
    tvosArm64()
    tvosSimulatorArm64()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":game-services-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(baseLibs.kotlinx.coroutines.test)
        }
    }
}

val nativeResources = layout.buildDirectory.dir("generated/gameCenterArm64Resources")
val bridgeProject = project(":game-services-game-center-bridge")
val nativeLibrary = bridgeProject.layout.buildDirectory.file("bin/macosArm64/releaseShared/libgs_gamecenter.dylib")
val compileArm64Jni = tasks.register<Exec>("compileArm64Jni") {
    dependsOn(":game-services-game-center-bridge:linkReleaseSharedMacosArm64")
    inputs.file(bridgeProject.file("src/jni/bridge.cpp"))
    inputs.file(bridgeProject.file("src/jni/build.py"))
    inputs.file(nativeLibrary)
    outputs.dir(nativeResources)
    doFirst {
        commandLine("python3", bridgeProject.file("src/jni/build.py"), "arm64", nativeLibrary.get().asFile,
            bridgeProject.file("src/jni/bridge.cpp"), System.getProperty("java.home"),
            nativeResources.get().dir("native/macos-arm64").asFile)
    }
}
kotlin.sourceSets.named("jvmMain") { resources.srcDir(nativeResources) }
tasks.named("jvmProcessResources") { dependsOn(compileArm64Jni) }
