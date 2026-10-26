This is a Kotlin Multiplatform project targeting Android, Desktop (JVM).

<img height="500" alt="image" src="https://github.com/user-attachments/assets/2b2e1b9c-fee4-4df1-b3ac-58af8938d869" />

<img height="500" alt="image" src="https://github.com/user-attachments/assets/765f49c8-4692-416f-96e7-efd237cae9e4" />


* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

## Design parity

UI screens are checked against their design references with
[design-parity](https://github.com/yschimke/design-parity). The design intent is
committed as HTML under [`design/`](./design); see
[`docs/design-parity.md`](./docs/design-parity.md) for how it works.

The rendered artifacts (reference | candidate | diff `report.html` triptychs and
the candidate bundle) are **not committed** — they're regenerated on every push
to `main` by [`.github/workflows/design-parity.yml`](./.github/workflows/design-parity.yml)
and published to the
[**`design-parity/main`**](https://github.com/yschimke/meshcore-mobile-out/tree/design-parity/main)
branch of the public output repository
[`yschimke/meshcore-mobile-out`](https://github.com/yschimke/meshcore-mobile-out),
which always reflects the current parity state of `main`. Every other generated
branch (`compose-preview/*` render baselines and PR renders,
`design-artifacts/meshcore-mobile`) lives there too, so this repository's
history only carries source.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

### Desktop downloads

Release tags publish desktop installers to [GitHub Releases](https://github.com/yschimke/meshcore-mobile/releases): Linux x64 (`.deb`), Windows x64 (`.msi`), and macOS Intel/Apple Silicon (`.dmg`). Each installer bundles Java; a separate Java installation is unnecessary. Installers are currently unsigned and macOS builds are not notarized.

Desktop installer checks run on pull requests and retain their packages as workflow artifacts. The **Desktop installers** workflow can also build any ref without publishing, or rebuild an existing release by supplying its tag. Manual builds stay build-only unless **publish** is explicitly selected; automated tagged releases publish all platforms. Release assets include SHA-256 checksums, and their filenames identify the application version and architecture. Installer metadata offsets the semantic major version by one to satisfy macOS packaging requirements while keeping upgrades monotonic.

Local packaging: `./gradlew :desktop-app:packageDistributionForCurrentOS`. Override the app version with `-PdesktopVersion=0.1.0`.

### Web app

The production Wasm app is deployed by **Wasm website** to [GitHub Pages](https://yschimke.github.io/meshcore-mobile/). Pull requests build the same production distribution and exercise browser flows before deployment. Existing documentation remains available alongside the app.

Local build: `./gradlew :web-app:wasmJsBrowserDistribution`. Serve `web-app/build/dist/wasmJs/productionExecutable` over HTTP on localhost or over HTTPS; opening `index.html` directly from disk does not work.

MeshCore connects directly to USB companion radios through Web Serial at 115200 baud, using the same framing as Android USB. Use desktop Chrome or Edge and choose a port when prompted. Firefox and Safari can load the UI but do not provide Web Serial. Raw TCP sockets, Android BLE permissions and persistent message storage are not available in the browser. Browser typography uses platform font families.

Browser checks: `python -m pip install playwright==1.62.0`, `python -m playwright install chromium`, then `python scripts/test-wasm-browser.py web-app/build/dist/wasmJs/productionExecutable`. Set `CHROMIUM_PATH` to use an existing Chromium installation.

Android internal-track releases, store listings, and Firebase setup are documented in
[Play publishing](docs/play-publishing.md).
