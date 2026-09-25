import java.util.Properties

plugins {
    alias(baseLibs.plugins.androidApplication)
    id("com.mikepenz.convention.compose")
    id("com.mikepenz.convention.composable-preview-scanner.paparazzi-plugin")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val demoKeystorePassword = localProperties.getProperty("demoKeystorePassword").orEmpty()
val gameServicesProjectId = localProperties.getProperty(
    "gameServicesProjectId",
    "REPLACE_WITH_PLAY_GAMES_PROJECT_ID",
)

android {
    namespace = "com.mikepenz.gameservices.sample.host"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    buildFeatures {
        resValues = true
    }
    externalNativeBuild {
        cmake { path = file("src/nativeSdk/cpp/CMakeLists.txt") }
    }

    flavorDimensions += "backend"
    productFlavors {
        create("javaSdk") { dimension = "backend" }
        create("nativeSdk") {
            dimension = "backend"
            externalNativeBuild.cmake.arguments += "-DNATIVE_VALIDATION=ON"
        }
    }

    defaultConfig {
        applicationId = "com.mikepenz.gameservices.sample.host"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "game_services_project_id", gameServicesProjectId)
    }

    signingConfigs {
        create("demo") {
            storeFile = file("gameserviceskt-demo-release.p12")
            storePassword = demoKeystorePassword
            keyAlias = "gameserviceskt-demo"
            keyPassword = storePassword
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        getByName("debug").signingConfig = signingConfigs.getByName("demo")
        getByName("release").signingConfig = signingConfigs.getByName("demo")
    }
}

val nativeBridge = project(":sample-host-android-native-bridge")
val nativeLibraries = layout.buildDirectory.dir("generated/nativeValidationJniLibs")
val stageNativeLibraries = tasks.register<Sync>("stageNativeValidationLibraries") {
    val abis = mapOf(
        "AndroidNativeArm64" to "arm64-v8a",
        "AndroidNativeX64" to "x86_64",
        "AndroidNativeArm32" to "armeabi-v7a",
        "AndroidNativeX86" to "x86",
    )
    abis.forEach { (target, abi) ->
        dependsOn(":sample-host-android-native-bridge:linkDebugShared$target")
        from(nativeBridge.layout.buildDirectory.file("bin/${target.replaceFirstChar(Char::lowercaseChar)}/debugShared/libgs_play_validation.so")) {
            into(abi)
        }
    }
    into(nativeLibraries)
}
android.sourceSets.getByName("nativeSdk").jniLibs.directories.add(nativeLibraries.get().asFile.absolutePath)
tasks.matching { it.name.startsWith("mergeNativeSdk") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(stageNativeLibraries)
}

dependencies {
    implementation(projects.sample)
    implementation(baseLibs.jetbrains.compose.material3)
    add("nativeSdkImplementation", "com.google.android.gms:play-services-games-v2-native-c:21.0.0-beta1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(baseLibs.jetbrains.compose.ui.tooling)
    testImplementation(baseLibs.jetbrains.compose.foundation)
}

composablePreviewPaparazzi {
    enable = true
    packages = listOf("com.mikepenz.gameservices.sample.host")
}

// The preview scanner generates a test source consumed by unit-test compilation and lint.
tasks.matching { it.name.endsWith("UnitTestKotlin") || (it.name.contains("lint", ignoreCase = true) && it.name.contains("UnitTest")) }.configureEach {
    dependsOn("generateComposablePreviewPaparazziTests")
}
