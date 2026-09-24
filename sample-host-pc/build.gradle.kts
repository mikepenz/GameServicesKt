plugins { id("com.mikepenz.convention.kotlin-multiplatform") }
kotlin.sourceSets.named("jvmMain") { dependencies { implementation(project(":game-services-play-games-pc")) } }
val mainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
tasks.register<JavaExec>("run") {
    mainClass.set("com.mikepenz.gameservices.sample.pc.MainKt")
    classpath(mainCompilation.output.allOutputs, mainCompilation.runtimeDependencyFiles)
    providers.gradleProperty("playPcLibrary").orNull?.let { args(it) }
}
