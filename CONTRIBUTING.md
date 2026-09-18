# Contributing

Open an issue before substantial changes. Keep pull requests focused, include tests for changed
behavior, and run the relevant Gradle checks before opening a pull request.

Every pull request must have exactly one release category label: `feature`, `fix`, `test`, `other`,
or `dependencies`. The release workflow uses that label to place the change in its release notes.

## Releases

Set `VERSION_NAME` in `gradle.properties`, then push a matching `v` tag. For example, version
`0.1.0` uses `v0.1.0`; version `0.2.0-SNAPSHOT` uses `v0.2.0-SNAPSHOT`. Stable tags publish to
Maven Central, while `-SNAPSHOT` tags publish to the Central Portal snapshot repository. Both create
a GitHub release after publication succeeds.

The repository needs Central Portal user-token credentials in `NEXUS_USERNAME` and
`NEXUS_PASSWORD`, plus `SIGNING_KEY_ID`, `SIGNING_PRIVATE_KEY`, and `SIGNING_PASSWORD`. Enable
snapshot publishing for the `com.mikepenz` namespace in the Central Portal before pushing a
snapshot tag.
