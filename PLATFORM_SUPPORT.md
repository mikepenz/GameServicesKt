# Platform support

Backends are selected explicitly. Contract artifacts do not install provider SDKs.

| Runtime | Backend | Available operations | Validation |
| --- | --- | --- | --- |
| Android API 30+ | PlayGamesBackend | Authentication, achievements, leaderboards, saves, social | Local adapter regression tests |
| Android on ChromeOS | PlayGamesBackend | Same Android API | Device validation pending |
| Android on Google Play Games for Windows | PlayGamesBackend | Same Android API | PC runtime validation pending |
| iOS | GameCenterBackend | Authentication, achievements, leaderboards, saves, social | Compile and simulator regression tests |
| macOS Native | GameCenterBackend | Authentication, achievements, leaderboards, saves, social | Compile and native capability tests |
| macOS JVM / Compose Desktop | GameCenterBackend | Same features as macOS Native | Both architectures compile; packaged JNI load test |
| Android Native (4 ABIs) | NativePlayGamesBackend (experimental) | Boolean authentication, achievements, Recall | Compile; Linux link CI |
| Windows x64 JVM / Native | PlayGamesPcBackend (experimental) | Initialization and Recall | Compile; Windows DLL CI |
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

## macOS JVM / Compose Desktop

The JVM variants of `game-services-game-center-*` use the same GameKit implementation through
JNI. The core artifact packages Apple Silicon and Intel native libraries. Choose this backend
explicitly with `GameCenterBackend()` and close it when the host exits. It requires macOS and
an AppKit event loop, as provided by Compose Desktop; plain headless JVM processes are not hosts.
Other desktop operating systems must select the unsupported backend explicitly.

`sample-host-desktop` is the runnable Compose host. For live authentication, package and sign it
with the consuming game's bundle identifier and Game Center entitlement. Saved games also need
iCloud containers/entitlements. Embedded dylibs must be signed with the application's identity
for hardened-runtime distribution; development ad-hoc signatures are not distribution signatures.
The optional `nativeLibrary: Path` constructor argument loads a host-packaged JNI library instead
of extracting the bundled resources. Its sibling Game Center dylib must be present.

Native requests are owned by the backend session. Cancellation ignores late callbacks and
`close()` cancels the native session. Account changes arrive from GameKit's authentication flow.
Do not create competing native and JVM sessions inside the same process.

## Experimental Android Native C SDK

Opt into `ExperimentalGameServicesApi` and depend on `game-services-play-games-native` for
`androidNativeArm64`, `androidNativeArm32`, `androidNativeX64`, or `androidNativeX86`.
`NativePlayGamesBackend(javaVm, activity)` takes the host's `JavaVM*` and a retained JNI global
Activity reference. Create one on the Activity's UI thread and suspend `close()` before releasing
that reference. The host APK must also include
`com.google.android.gms:play-services-games-v2-native-c:21.0.0-beta1` and its Java dependencies;
a Kotlin/Native library alone cannot package those Android classes or initialize an Activity.

The pinned beta SDK provides boolean `signIn()` / `isAuthenticated()`, an `AchievementsClient`,
and `RecallClient`. It has no player identity API, leaderboard API, save API, or social API in its
shipped headers. It therefore does not implement the identity-bearing `GameServices` contract.
Use the existing unsupported feature clients for absent features. Do not mix the Android Java and
C SDK session owners in one host. C SDK requests finish before cancellation/close releases handles.

The build verifies the SDK archive SHA-256 and binds its actual C ABI. Two C++-only standard-header
includes are normalized for the C interop parser; declarations remain unchanged. Native linking is
checked separately on Linux because Kotlin's bundled Android linker is an Intel executable that
cannot run on this Apple Silicon host without Rosetta. A signed Android host still needs live SDK
validation for sign-in, achievement acknowledgement/UI, Activity recreation, and process shutdown.

## Experimental native Windows / JVM Recall

`game-services-play-games-pc` supports Windows x64 through JVM/JNI and `mingwX64`.
`PlayGamesPcBackend(absoluteDllPath)` implements `RecallClient`; call `initialize()` first,
then `requestRecallAccess()`, and suspend `close()` at shutdown. This is the native Windows SDK,
not the Android runtime on Google Play Games for PC. It currently supplies Recall, not the
common achievements, leaderboard, saved-game, social, or player-identity contracts.

Build its pinned, SHA-256-verified SDK and C/JNI bridge on Windows with Java 21 and Visual Studio:

```shell
cmake -S game-services-play-games-pc/native -B game-services-play-games-pc/build/native -A x64
cmake --build game-services-play-games-pc/build/native --config Release
ctest --test-dir game-services-play-games-pc/build/native -C Release --output-on-failure
```

Distribute `gs_play_pc.dll`, `play_pc_sdk.dll`, and the copied SDK notices together. The CI artifact
`play-pc-x64-bridge` contains these files; Kotlin artifacts intentionally require their absolute
host-packaged DLL path. The DLL remains loaded for the process lifetime so native callback returns
cannot jump into unloaded code. Cancellation waits for SDK completion before releasing a session.

`PcInitializationException` preserves `ShutdownRequired`, `RuntimeUpdateRequired`,
`RuntimeUnavailable`, and other failure codes. The host must act on initialization failure and
must not call provider operations until initialization succeeds. The library never terminates
the host process. Configure the Play PC manifest, distribution/signing, project, and Play Games
runtime as required by the SDK before live testing.

A `RecallSession` is an opaque sensitive token to send to the game's server. It is not a player ID,
a login proof, or a client-side account-linking API. Its string representation is redacted.
Test server-side linking/unlinking and account recovery in the configured game backend. No
client secret or server credential belongs in this library or its samples.

Windows CI checks the real DLL loading and both JVM/Native bindings without a provider account.
The C++ fake-SDK test checks initialization failures and callback/client lifetime. Real runtime
initialization and Recall/server operations remain live-provider validation.

The Windows console validation host is `:sample-host-pc:run` with
`-PplayPcLibrary=C:\absolute\path\gs_play_pc.dll`. It exits on initialization failure and never
prints the Recall token. Launch/configure the packaged host through the Play Games runtime for a
live check; merely running the console host is not proof of runtime/store configuration.

## Upstream references

- [Play Games Services overview](https://developer.android.com/games/pgs/overview)
- [Play Games SDK entry points](https://developer.android.com/games/pgs/start)
- [GameKit](https://developer.apple.com/documentation/gamekit)

SDK archive versions and checksums are pinned in the native adapter builds. Treat downloaded
headers as the binding source of truth: language choice (C/C++/Java) does not itself add an OS target.
