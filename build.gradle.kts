plugins {
    alias(baseLibs.plugins.conventionPlugin)
    alias(baseLibs.plugins.androidLibrary) apply false
    alias(baseLibs.plugins.kotlinMultiplatform) apply false
    alias(baseLibs.plugins.dokka)
    alias(baseLibs.plugins.mavenPublish) apply false
    alias(baseLibs.plugins.versionCatalogUpdate) apply false
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

tasks.register("verifyAndroidMinSdk") {
    doLast {
        check(providers.gradleProperty("com.mikepenz.android.minSdk").orNull == "30") {
            "Android libraries must declare com.mikepenz.android.minSdk=30."
        }
    }
}
