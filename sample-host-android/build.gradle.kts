plugins {
    alias(baseLibs.plugins.androidApplication)
}

android {
    namespace = "com.mikepenz.gameservices.sample.host"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mikepenz.gameservices.sample.host"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.sample)
    implementation(projects.gameServicesCore)
    implementation(projects.gameServicesAchievements)
    implementation(projects.gameServicesLeaderboards)
    implementation(projects.gameServicesSavedGames)
    implementation(projects.gameServicesSocial)
    implementation("androidx.activity:activity:1.13.0")
    implementation(baseLibs.bundles.coroutines.android)
}
