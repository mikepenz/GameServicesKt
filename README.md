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

Snapshot builds require the Central Portal snapshot repository:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        content { includeGroup("com.mikepenz") }
    }
    mavenCentral()
}
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

Every service operation returns `Result`; handle its failure instead of assuming authentication. Value constructors validate inputs and may throw `IllegalArgumentException`. A
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

## Authentication

Call `refreshAuthentication()` once at app startup after creating the client. On Android it checks
the Play Games automatic-authentication result without starting the explicit sign-in flow. On iOS it
initializes Game Center and may receive a system view controller that the library presents.

Call `authenticate()` only from a player action. It starts the explicit Play Games sign-in flow on
Android. Game Center has no separate retry API. The iOS client installs its handler once, keeps observing account changes, and rechecks the current player on later calls. Create one iOS core client for the app.
Derive a boolean from `authenticationState.value is AuthenticationState.Authenticated`; the state
flow remains the single source of truth.

## Shared service IDs

Google Play Games uses opaque achievement and leaderboard IDs while Game Center IDs are app-defined.
Pass immutable mappings to the feature factories when shared game code should use one ID on both
platforms. Calls translate the shared ID to the provider ID; loaded achievement and leaderboard
metadata translates back to the shared ID.

```kotlin
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementIdMapping
import com.mikepenz.gameservices.achievements.AchievementIdMappings

val achievementIds = AchievementIdMappings.of(
    AchievementIdMapping(
        id = AchievementId("dragon_slayer"),
        googlePlayGamesId = AchievementId("CgkI..."),
        gameCenterId = AchievementId("dragon_slayer"),
    ),
)

// Android
val achievements = createAchievementsClient(this, achievementIds)

// iOS
val achievements = createAchievementsClient({ currentViewController }, achievementIds)
```

`LeaderboardIdMappings` works the same way. IDs absent from a mapping pass through unchanged.

Provider UI can be launched only from a user action. Handle every `Result`; cancellation stays a
cancelled coroutine, while expected service failures remain typed `GameServicesException` values.

## Operation contracts

Keep Android clients with the `ComponentActivity` that created them. Recreate them during the new
activity's `onCreate`; do not store an old activity client in an application singleton. Register every
feature factory before the activity starts. Coroutine cancellation does not dismiss an already opened
provider screen. A second selection or consent request fails while that screen is still outstanding;
its late result is discarded for the cancelled caller. Destroying the activity cancels the outstanding
wait. Provider UI methods switch to the main thread internally.

Score submissions and achievement updates wait for provider acknowledgement. A successful result
does not guarantee immediate visibility in a later leaderboard query or on another device. Handle
offline failures and choose an application-owned retry policy. Save commits acknowledge local commit
and requested background synchronization on Android; they do not confirm remote synchronization.
Cancelling a save cannot undo a commit that has already started. Native save commit and cleanup finish
before releasing the operation's resources.

`AuthenticationRequired`, `PermissionRequired`, `ConfigurationMissing`, and `UserCancelled` represent
recognized provider failures. Other failures carry a provider and code. JVM provider exceptions retain
the original cause for diagnostics. `InternalGameServicesApi` marks implementation helpers shared by
the published modules; applications should use the service interfaces instead.

## Leaderboard queries

`LeaderboardScore.player` is nullable for an anonymous or privacy-restricted player. Use
`displayName` for presentation. `rank` is a nullable `Long`; null means the provider did not return a
positive rank. Never manufacture a player ID for anonymous scores.

Queries start at ranks 1 through 1000 and return at most 25 entries. The limit counts entries, including
ties. Android reads successive SDK pages and scans at most 41 pages, then returns a failure instead
of silently returning an incomplete result. Use the native leaderboard screen for deeper browsing or
boards with enough ties to exhaust this limit. These bounds avoid unbounded sequential provider calls.

## Saved games and friends setup

Use 1–100 ASCII letters, digits, or `-._~` for portable saved game IDs. Android enforces this filename subset; iOS can still read existing saves with other nonempty names.
Android validates bytes against the SDK's maximum data size before opening a save. Keep payloads small
and handle provider-specific limits and quota failures. The library copies bytes at public boundaries;
it does not own your serialization or compression format.

Handle `Conflict` on both reads and writes. Inspect every version, then call `resolve(conflict.id, data)`
with chosen or merged bytes on the same client. Android reopens the save before resolving and returns
a fresh conflict if it changed. After process/activity recreation or an unknown-conflict error, read
again. A failed resolution can be retried, but cancellation or an ambiguous network failure should be
followed by readback before another write.

For iOS saves, enable iCloud Documents, configure the ubiquity container and matching entitlements,
and test with iCloud Drive enabled. Game Center authentication alone does not configure cloud saves.
For friends, add a localized `NSGKFriendListUsageDescription` to the app's `Info.plist`, then call
`requestFriendsAccess()` from a user action. `loadFriends()` does not silently request consent.
Android loads all available friend pages. iOS avatar/profile lookup resolves the supplied Game Center
player identifier directly instead of restricting it to the current friend list; provider privacy
restrictions still apply.

## Provider-validation host

`:sample-host-android` and `sample-host-ios` are private, non-published validation apps.
Both hosts render the same Compose Multiplatform screen from `:sample`. Feature-client capability
flags disable unavailable actions, including provider-owned save selection on iOS.

Android setup:

1. Create a private Play Games Services project linked to `com.mikepenz.gameservices.sample.host`.
   Add every testing account under PGS **Testers**; no production release is needed.
2. Put the numeric PGS Project ID (not the OAuth client ID) in the ignored root `local.properties`:
   `gameServicesProjectId=123456789012`. If using the local demo keystore, add its password as
   `demoKeystorePassword=...`. This is local, so forks supply their own project and keystore.
3. Create a PGS credential for the certificate that signs `installDebug`. Also add the Google Play
   app-signing SHA-1 only when testing an internal-track build. Run
   `./gradlew :sample-host-android:installDebug` on an API 30+ device; individual PGS testers can
   use the local build, while an internal track validates the Google Play-signed path.

Open `sample-host-ios/GameServicesSampleHost.xcodeproj` in Xcode, copy `Config.xcconfig` to the
ignored `Local.xcconfig`, and set its development team, bundle ID, and iCloud container before
running on an iOS 16+ device. Select a sandbox Game Center account; App Store Connect
configuration remains app-owned.

The validation screen refreshes authentication on startup, observes account changes, prevents
concurrent operations, and preserves editable fields up to 16,384 characters each across activity recreation.
Larger fields are omitted from restored state with a warning; read the save again before writing.
The full payload remains available while the screen is open. It can query scores,
read the current player's score, and inspect or edit UTF-8 save/conflict payloads. Re-read conflicts
after recreating a client; session conflict handles are deliberately not restored.

## Validation limits

Automated regressions cover shared contracts and pagination, Android achievement loading and update
acknowledgements, Android sign-in and GameKit callback sequences, provider error mappings, and snapshot
commit/cancellation/descriptor cleanup. Saved-game conflict tests run the real Android and iOS adapters
against controlled native responses: choosing either version, supplying merged/empty/binary data,
changing or repeated conflicts, failed resolutions and retries, stale handles, cancellation, and cleanup.
Google's pair-and-token contract and GameKit's same-name version lists have separate fixtures.
The library preserves opaque bytes; games provide their own merge policy.
The sample integration test drives startup, failure recovery, conflict editing/resolution, and account changes through the app.

Live provider acceptance still requires configured Android and iOS devices: sign-in and account changes, consent cancellation,
score visibility, offline writes, and actual cross-device synchronization. Deterministic adapter tests
validate conflict handling without creating live backend conflicts; device tests check that the provider
and account configuration behave as expected. Device frame time, allocation,
and network latency measurements are separate from these tests.
