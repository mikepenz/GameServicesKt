plugins { id("com.mikepenz.convention.kotlin-multiplatform") }
kotlin {
    macosArm64().binaries.sharedLib { baseName = "gs_gamecenter" }
    sourceSets.commonMain.dependencies {
        implementation(project(":game-services-game-center-core"))
        implementation(project(":game-services-game-center-achievements"))
        implementation(project(":game-services-game-center-leaderboards"))
        implementation(project(":game-services-game-center-saved-games"))
        implementation(project(":game-services-game-center-social"))
    }
}
