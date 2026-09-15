# GameServicesKt

Kotlin Multiplatform, provider-neutral game-services APIs for Google Play Games and Game Center.

## Modules

```kotlin
implementation("com.mikepenz:game-services-core:0.1.0-SNAPSHOT")
implementation("com.mikepenz:game-services-achievements:0.1.0-SNAPSHOT")
implementation("com.mikepenz:game-services-leaderboards:0.1.0-SNAPSHOT")
implementation("com.mikepenz:game-services-saved-games:0.1.0-SNAPSHOT")
implementation("com.mikepenz:game-services-social:0.1.0-SNAPSHOT")
```

Every module compiles for Android API 30+, iOS, JVM, and Wasm. The core runtime uses Google Play
Games on Android and Game Center on iOS. JVM and Wasm factories return a client whose support
provider is `None`, whose `isSupported` is false, and whose operations fail with
`GameServicesException.UnsupportedTarget`. Check `services.support.isSupported` before showing
game-services controls.

Wire the platform client at the app edge, then pass `GameServices` and feature clients into shared
code. Do not treat `PlayerId` as an in-game account ID; it is scoped to the provider.

```kotlin
import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.achievements.AchievementsClient
import com.mikepenz.gameservices.leaderboards.LeaderboardId
import com.mikepenz.gameservices.leaderboards.LeaderboardsClient

class GameServicesScreen(
    private val services: GameServices,
    private val achievements: AchievementsClient,
    private val leaderboards: LeaderboardsClient,
) {
    suspend fun signInAndShowAchievements(): String {
        if (!services.support.isSupported) return "Game services are unavailable on this platform"
        return services.authenticate().fold(
            onSuccess = { player ->
                achievements.showAchievements()
                    .onFailure { return "Could not open achievements: ${it.message}" }
                "Signed in as ${player.displayName}"
            },
            onFailure = { "Could not sign in: ${it.message}" },
        )
    }

    suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> =
        if (services.support.isSupported) leaderboards.submitScore(id, score)
        else Result.failure(GameServicesException.UnsupportedTarget(services.support.target))
}
```

Every operation returns `Result`; handle its failure instead of assuming authentication. A
`PlayerId` identifies a player only inside its provider, so map it to your own account ID rather
than storing it as an application-wide identifier.

## Android setup

Set the consumer app's `minSdk` to 30. Configure the game in Play Console, add its Games services
project ID to app resources, and reference it from the consumer app manifest:

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.games.APP_ID"
        android:value="@string/game_services_project_id" />
</application>
```

Create the Android client from the lifecycle-owned `ComponentActivity` during `onCreate`, before
activity-result registration closes:

```kotlin
val services = createGameServices(this)
```

## iOS setup

Enable the Game Center capability in Xcode (which adds the entitlement), configure the app and
requested achievements/leaderboards in App Store Connect, and create the client with a current
presenter:

```kotlin
val services = createGameServices { currentViewController }
```

Provider UI can be launched only from a user action. Handle every `Result`; cancellation stays a
cancelled coroutine, while expected service failures remain typed `GameServicesException` values.

## Provider-validation host

`:sample-host-android` is a local, non-published Android app for Play Games validation. Replace its
placeholder project ID in `sample-host-android/src/main/res/values/game_services.xml`; do not commit
that value. It has direct controls for every first-release Android operation, including saves and
conflict resolution. Run `./gradlew :sample-host-android:installDebug` on an API 30+ device with a
configured Play Games test account. Use your iOS app's lifecycle-owned presenter with
`createGameServicesSample { currentViewController }` for Game Center validation; its entitlement,
bundle ID, and App Store Connect configuration belong to that app and are not included here.
