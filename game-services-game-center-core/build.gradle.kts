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

// Both architectures are shipped so one JVM artifact works on Apple Silicon and Intel.
val nativeResources = layout.buildDirectory.dir("generated/nativeResources")
val packageNativeBridge = tasks.register("packageNativeBridge") {
    outputs.dir(nativeResources)
}
listOf("Arm64" to "arm64", "X64" to "x64").forEach { (target, directory) ->
    val bridgeProject = project(":game-services-game-center-bridge")
    val nativeLibrary = bridgeProject.layout.buildDirectory.file("bin/macos$target/releaseShared/libgs_gamecenter.dylib")
    val output = nativeResources.map { it.dir("native/macos-$directory") }
    val compile = tasks.register<Exec>("compile${target}Jni") {
        dependsOn(":game-services-game-center-bridge:linkReleaseSharedMacos$target")
        inputs.file(bridgeProject.file("src/jni/bridge.cpp"))
        inputs.file(nativeLibrary)
        outputs.dir(output)
        inputs.file(bridgeProject.file("src/jni/build.py"))
        doFirst {
            commandLine("python3", bridgeProject.file("src/jni/build.py"),
                if (target == "Arm64") "arm64" else "x86_64", nativeLibrary.get().asFile,
                bridgeProject.file("src/jni/bridge.cpp"), System.getProperty("java.home"), output.get().asFile)
        }
    }
    packageNativeBridge.configure { dependsOn(compile) }
}
kotlin.sourceSets.named("jvmMain") { resources.srcDir(nativeResources) }
tasks.named("jvmProcessResources") { dependsOn(packageNativeBridge) }
