import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.security.MessageDigest

plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
}
val sdk = configurations.create("sdk")
dependencies { add(sdk.name, "com.google.android.gms:play-services-games-v2-native-c:21.0.0-beta1@aar") }
val sdkDirectory = layout.buildDirectory.dir("pgs-sdk")
val prepareSdk = tasks.register<Sync>("preparePlayGamesSdk") {
    from({ zipTree(sdk.singleFile) }) { include("prefab/modules/games_static/**") }
    into(sdkDirectory)
    doFirst {
        val digest = MessageDigest.getInstance("SHA-256").digest(sdk.singleFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == "9c5fa026867bfbfe9773a82fdd308ed80eb9e067872b0adcc15543914f445c39") { "Unexpected PGS C SDK archive" }
    }
    doLast {
        // The exported ABI is C; only these two includes incorrectly require C++.
        sdkDirectory.get().dir("prefab/modules/games_static/include").asFile.listFiles()!!.forEach { header ->
            header.writeText("#include <stdbool.h>\n" + header.readText().replace("<cstdint>", "<stdint.h>").replace("<cstddef>", "<stddef.h>"))
        }
    }
}
kotlin {
    explicitApi()
    @OptIn(ExperimentalAbiValidation::class) abiValidation()
    val targets = listOf(androidNativeArm64() to "arm64-v8a", androidNativeX64() to "x86_64",
        androidNativeArm32() to "armeabi-v7a", androidNativeX86() to "x86")
    targets.forEach { (target, abi) ->
        val definition = layout.buildDirectory.file("interop/${target.name}.def")
        val prepare = tasks.register("prepare${target.name}Interop") {
            dependsOn(prepareSdk)
            outputs.file(definition)
            doLast {
                val root = sdkDirectory.get().dir("prefab/modules/games_static").asFile
                definition.get().asFile.apply { parentFile.mkdirs(); writeText("""
                    headers = pgs_games_sign_in_client.h pgs_achievements_client.h pgs_recall_client.h
                    package = com.mikepenz.gameservices.playgames.nativeinterop
                    staticLibraries = libgames_static.a
                    libraryPaths = ${root.resolve("libs/android.$abi")}
                    compilerOpts = -I${root.resolve("include")}
                    linkerOpts = -llog -landroid -lc++_static -lc++abi
                """.trimIndent()) }
            }
        }
        target.compilations.getByName("main").cinterops.create("pgs") {
            definitionFile.set(definition)
            tasks.named(interopProcessingTaskName) { dependsOn(prepare) }
        }
        target.binaries.sharedLib { baseName = "gs_play_native" }
    }
    sourceSets.commonMain.dependencies {
        api(project(":game-services-achievements"))
        api(project(":game-services-recall"))
    }
}
