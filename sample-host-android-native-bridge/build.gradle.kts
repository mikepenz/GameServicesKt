plugins { id("com.mikepenz.convention.kotlin-multiplatform") }

kotlin {
    listOf(androidNativeArm64(), androidNativeX64(), androidNativeArm32(), androidNativeX86()).forEach {
        it.binaries.sharedLib { baseName = "gs_play_validation" }
    }
    sourceSets.commonMain.dependencies {
        implementation(project(":game-services-play-games-native"))
    }
}
