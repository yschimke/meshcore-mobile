# Agent notes

- Use `agent/...` for branch names, never `claude/...` or `copilot/...`
  (enforced by `.github/workflows/no-ai-coauthors.yml`).
- Strip any `Co-authored-by` trailers, AI bot emails, and "Generated with"
  lines from commits and PR bodies before pushing.
- Project layout: `app/` (Android), Wear in `wear/`, plus several
  `meshcore-*` library modules (`meshcore-core`, `meshcore-data`,
  `meshcore-components`, `meshcore-mobile`, `meshcore-cli`,
  `meshcore-tui`, `meshcore-grpc-service`, and the
  `meshcore-transport-{ble,tcp,usb}` transports).
- Build: `./gradlew assembleDebug` for debug APKs;
  `./gradlew test lintDebug ktfmtCheck` mirrors what CI runs.
- JDK: 21 (configured via `gradle/gradle-daemon-jvm.properties`; do not
  pin a specific vendor).
- Cloud / remote agent sandboxes need an Android SDK — see
  `.claude/CLAUDE.md` for the `SessionStart` hook that installs one
  on demand.
- Keep PR titles in conventional-commits form
  (`feat:`, `fix:`, `chore:`, …); enforced by
  `.github/workflows/pr-title.yml` and consumed by release-please.
