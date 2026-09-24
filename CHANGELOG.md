# Changelog

All notable changes to this project are documented in this file.

## Unreleased

- Fix standard Android achievement updates and overflow in step/percentage conversion.
- Keep Game Center authentication state current after account changes and coordinate concurrent initialization.
- Check snapshot writes, close opened resources, retain retryable conflicts, and clean up late cancelled results.
- Load complete leaderboard ranges and friend pages; remove friend-only restrictions from iOS player lookup.
- Wait for score/achievement acknowledgement and normalize provider errors across modules.
- Reduce repeated achievement metadata loads, remove the Android save-read catalog preflight, and move snapshot I/O and avatar encoding off the caller's UI thread.
- Extend the sample with authentication refresh, pending states, score queries, editable conflicts, and form restoration.
- Breaking: `LeaderboardScore.player` is nullable, `rank` is a nullable `Long`, and `displayName` supports anonymous entries. Queries accept start ranks through 1000; Android scans at most 41 SDK pages and fails explicitly beyond that bound.
- Add opt-in implementation helpers for provider modules and expand lifecycle, pagination, arithmetic, and sample regression coverage.
