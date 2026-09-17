import java.util.Properties

plugins {
    alias(baseLibs.plugins.androidApplication)
    id("com.mikepenz.convention.compose")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val demoKeystorePassword = localProperties.getProperty("demoKeystorePassword") ?: providers.exec {
    commandLine("op", "read", "REDACTED_DEMO_KEYSTORE_PASSWORD")
}.standardOutput.asText.get().trim()
val gameServicesProjectId = localProperties.getProperty(
    "gameServicesProjectId",
    "REPLACE_WITH_PLAY_GAMES_PROJECT_ID",
)

android {
    namespace = "com.mikepenz.gameservices.sample.host"
    compileSdk = 37

    buildFeatures {
        resValues = true
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

dependencies {
    implementation(projects.sample)
    implementation("androidx.activity:activity-compose:1.13.0")
}
