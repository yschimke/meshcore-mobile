# Android Play publishing

Releases publish `ee.schimke.meshed` to the **internal** track with `COMPLETED`
status: testers receive the release without a separate draft rollout. The
release-please workflow calls `release-build.yml` directly because tags made
with `GITHUB_TOKEN` do not trigger another workflow. Direct `v*` tag pushes
also publish. Production promotion remains a Play Console action.

The workflow builds and publishes only `:app:publishReleaseBundle`, then
`:app:publishReleaseListing` for the checked-in English (UK) title,
descriptions and graphics in `app/src/main/play`. Set the repository
variable `PLAY_PUBLISH_LISTING=false` to skip listing changes. Listings are
app-wide, so edits can affect the production listing too.

Manual **Release** dispatches build artifacts by default. Select
`publish_play=true` to publish an existing tag deliberately. Workflow changes
apply from the selected workflow ref; app sources are checked out at `tag`.
Use a new version code for each new upload; rerunning a published version
will be rejected by Play. Existing version codes are derived from the
release-please version and must exceed the highest uploaded Play version.

## Firebase project

- Project: [meshed-up-yschimke](https://console.firebase.google.com/project/meshed-up-yschimke/overview)
- Android app ID: `1:208081902905:android:f44a3ccd79dadc466ddce8`
- Analytics property: `560919219` (separate from Terrazzo and other apps)
- `GOOGLE_SERVICES_JSON` is configured in this GitHub repository.

## Required repository secrets

- `SIGNING_KEYSTORE`: base64 of the existing Play upload keystore.
- `MESHCORE_KEYSTORE_PASSWORD`, `MESHCORE_KEY_ALIAS`, `MESHCORE_KEY_PASSWORD`:
  signing credentials. Keep the established upload key.
- `PLAY_SERVICE_ACCOUNT_JSON`: raw JSON for a service account authorized
  for this app's testing releases and store presence in Play Console.
- `GOOGLE_SERVICES_JSON`: base64 of `app/google-services.json` for the
  Firebase Android app registered with package `ee.schimke.meshed`.

Publishing fails early if these are missing rather than silently succeeding
without a rollout or telemetry. Build-only dispatches need no publishing
secrets. Firebase config is written before any Gradle call. Builds with no
config resolve neither the Firebase plugins nor SDKs. A configured build
uses Firebase's startup provider for Analytics and uncaught-crash reporting;
Crashlytics' Gradle plugin handles mapping upload for minified releases.
No custom event payloads or nonfatal exception forwarding are added.
Advertising-ID and ad-attribution permissions are removed, matching Terrazzo.
Firebase installation/app-instance identifiers remain and must still be
disclosed under Device or other IDs in Data safety.

## Firebase and Console setup

1. Create a dedicated Firebase project, enable Google Analytics, and register
   the Android package above. Download its `google-services.json` and save
   the base64 contents as `GOOGLE_SERVICES_JSON` in this repository.
2. Enable Crashlytics for that Android app. Install a configured release,
   exercise it, and verify Analytics events in DebugView. On a local test
   build only, trigger an uncaught crash, restart the app, and confirm it
   arrives in Crashlytics. SDK wiring alone does not prove ingestion.
3. Check Play's app setup, privacy policy URL, Data safety declarations,
   content rating, and internal tester access. The checked-in privacy policy
   discloses the Firebase SDKs. Review the declarations against
   [Firebase's SDK disclosure guidance](https://firebase.google.com/docs/android/play-data-disclosure)
   before distributing a build with telemetry. These Console forms are not
   managed by Gradle Play Publisher.
4. Publish a new release and check **Android publishing** in the job summary,
   the internal track's version and rollout status, and the store listing.

The Play app and initial bundle upload must already exist. If Play still
marks the app as draft, complete its required Console setup; a `COMPLETED`
API release may be rejected until that is resolved.
