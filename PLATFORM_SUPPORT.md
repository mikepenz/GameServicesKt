# Platform support

Backends are selected explicitly. Contract artifacts do not install provider SDKs.

| Runtime | Backend | Available operations | Validation |
| --- | --- | --- | --- |
| Android API 30+ | PlayGamesBackend | Authentication, achievements, leaderboards, saves, social | Local adapter regression tests |
| Android on ChromeOS | PlayGamesBackend | Same Android API | Device validation pending |
| Android on Google Play Games for Windows | PlayGamesBackend | Same Android API | PC runtime validation pending |
| iOS | GameCenterBackend | Authentication, achievements, leaderboards, saves, social | Compile and simulator regression tests |
| macOS Native | GameCenterBackend | Authentication, achievements, leaderboards, saves, social | Compile and native capability tests |
| tvOS | GameCenterBackend | Authentication, achievements, leaderboards, social; no saves | Compile |
| watchOS | GameCenterBackend | Authentication, achievements, leaderboards, friends; no saves, avatars, or provider UI | Compile |

Compilation and automated adapter tests do not validate a real provider account, entitlements,
iCloud provisioning, or store distribution. A platform is ready for a consuming app only after
that app's signed provider host has passed the live checks below.

## Apple targets

Contract and Game Center artifacts provide `iosArm64`, `iosSimulatorArm64`, `macosArm64`,
`macosX64`, `tvosArm64`, `tvosSimulatorArm64`, `watchosArm64`, `watchosDeviceArm64`, and
`watchosSimulatorArm64`. Intel macOS is compatibility support for a deprecated Kotlin target.

On iOS/tvOS, construct `GameCenterBackend { currentUIViewController }`. On macOS, use
`GameCenterBackend { currentNSViewController }`. On watchOS, use `GameCenterBackend()`.
Create one session per app: GameKit has one process-wide local-player authentication handler.
All feature factories are extensions on this backend. AppKit/UIKit presenters are used only
for authentication; GameKit access points present feature screens on the main thread.

Specific-leaderboard and player-profile screens require iOS/tvOS 18 or macOS 15. Check
`supportedOperations`; older OS versions still support leaderboard data and the general dashboard.
watchOS uses GameKit's legacy `playerID` because `gamePlayerID` is unavailable there. Do not
assume its identifier matches an iPhone/macOS Game Center identifier or use it as your app's
cross-device account ID. Scores outside the native integer range are rejected on 32-bit watchOS.
GameKit saved games require an iCloud account and the app's configured iCloud container.

## Android deployment checks

ChromeOS and Google Play Games on PC run the Android artifact. They do not use the JVM backend.
Consumer games must support keyboard/mouse or appropriate controller input, window resizing,
and their destination's required native ABIs. Check that optional phone hardware is not marked
required in the consumer manifest. This library does not add hardware requirements.

Run the existing validation APK with the consuming game's PGS project, tester account, package,
and signing certificate. On both ChromeOS and the Google Play Games developer runtime, verify:

1. Startup authentication and explicit retry after declining sign-in.
2. Achievement loading, reporting, and provider UI dismissal.
3. Leaderboard discovery, score submission, paging, and native UI.
4. Save round-trip, cancellation, conflict resolution, and account changes.
5. Friends consent, avatar loading, and profile presentation.

Keep Android x86-64 packaging and actual Windows runtime validation separate. A passing
Android emulator test on macOS does not prove Google Play Games on Windows compatibility.

## Apple live checks

Use a signed host with Game Center enabled and configured achievements/leaderboards. Verify
startup authentication, refusal, account changes, achievement and score updates, and every
advertised system screen. Verify iCloud save round-trip and conflict resolution on iOS/macOS.
On tvOS/watchOS confirm save actions are disabled; on watchOS also confirm UI/avatar actions
are disabled. Test account changes without retaining data or conflict handles from another player.
