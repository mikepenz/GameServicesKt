plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.compose")
}
kotlin { sourceSets { jvmMain {
    dependencies {
        implementation(project(":sample"))
        implementation(project(":game-services-game-center-core"))
        implementation(compose.desktop.currentOs)
    }
}
} }
compose.desktop.application { mainClass = "com.mikepenz.gameservices.sample.desktop.MainKt" }
