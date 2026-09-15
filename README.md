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
`GameServicesException.UnsupportedTarget`.

Wire the platform client at the app edge, then pass `GameServices` and feature clients into shared
code. Do not treat `PlayerId` as an in-game account ID; it is scoped to the provider.

```kotlin
suspend fun signIn(services: GameServices): String = services.authenticate()
    .fold(onSuccess = { it.displayName }, onFailure = { "Unavailable: ${it.message}" })
```

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
