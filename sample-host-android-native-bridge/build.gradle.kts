plugins { id("com.mikepenz.convention.kotlin-multiplatform") }

val ndkHost = if (System.getProperty("os.name").startsWith("Mac")) "darwin-x86_64" else "linux-x86_64"
val ndkToolchain = file("${System.getenv("ANDROID_HOME")}/ndk/28.2.13676358/toolchains/llvm/prebuilt/$ndkHost")

kotlin {
    listOf(androidNativeArm64(), androidNativeX64(), androidNativeArm32(), androidNativeX86()).forEach {
        it.binaries.sharedLib {
            baseName = "gs_play_validation"
            linkerOpts("-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384", "-Wl,--no-undefined")
            if (it.name == "androidNativeArm64") {
                linkerOpts(ndkToolchain.resolve("lib/clang/19/lib/linux/aarch64/libunwind.a").absolutePath)
            }
        }
    }
    sourceSets.commonMain.dependencies {
        implementation(project(":game-services-play-games-native"))
    }
}
