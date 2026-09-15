import java.util.zip.ZipFile

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

tasks.register("verifyPublishedAndroidMinSdk") {
    dependsOn(
        ":game-services-core:publishToMavenLocal",
        ":game-services-achievements:publishToMavenLocal",
        ":game-services-leaderboards:publishToMavenLocal",
        ":game-services-saved-games:publishToMavenLocal",
        ":game-services-social:publishToMavenLocal",
    )
    doLast {
        val version = providers.gradleProperty("VERSION_NAME").get()
        val localRepository = providers.systemProperty("user.home").get() + "/.m2/repository/com/mikepenz"
        listOf(
            "game-services-core",
            "game-services-achievements",
            "game-services-leaderboards",
            "game-services-saved-games",
            "game-services-social",
        ).forEach { artifact ->
            val androidArtifact = "$artifact-android"
            val archive = file("$localRepository/$androidArtifact/$version/$androidArtifact-$version.aar")
            check(archive.isFile) { "Missing published Android artifact: $archive" }
            val manifest = ZipFile(archive).use { zip ->
                zip.getInputStream(requireNotNull(zip.getEntry("AndroidManifest.xml"))).use { input ->
                    String(input.readBytes())
                }
            }
            check("android:minSdkVersion=\"30\"" in manifest) {
                "$artifact Android manifest must declare minSdkVersion=30."
            }
        }
    }
}
