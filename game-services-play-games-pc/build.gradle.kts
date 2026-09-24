import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
plugins {
    id("com.mikepenz.convention.kotlin-multiplatform")
    id("com.mikepenz.convention.publishing")
}
kotlin {
    explicitApi()
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    @OptIn(ExperimentalAbiValidation::class) abiValidation()
    mingwX64()
    sourceSets.commonMain.dependencies { api(project(":game-services-recall")) }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
