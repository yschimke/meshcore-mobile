# MeshCore desktop

The desktop host uses the common CMP device and chat presentation with adaptive Material 3
navigation and Navigation 3. Connect to a companion radio over TCP with its host and port.
Android keeps permission-aware BLE/USB transports and persistent message storage.

```sh
./gradlew :desktop-app:run
./gradlew :desktop-app:createDistributable
./gradlew :desktop-app:packageDistributionForCurrentOS
```

Use JDK 21. Android variants require SDK 37.1 for the current Compose alphas.
Installers are configured for macOS, Windows, and Linux and build on their respective hosts.

The common module owns the TCP form, device body, chat body, theme picker, login dialog,
and status views. Android-specific routes remain internal to the Android app.
Desktop conversation history is currently held in memory;
BLE/USB desktop transport, durable history, and full device-settings integration remain.
The commands channel is usable when configured on the companion radio. Room and repeater
contacts prompt for login before opening chat. Credentials stay in memory and are masked;
sent messages survive leaving a chat during the connection, including an in-flight send.
Closing the window disconnects asynchronously.

The three Remote Compose device-widget previews have been rendered locally with the embedded
player 2.1.4 and AndroidX Remote Compose alpha21, without linkage errors.

The shared Remote Compose wrapper renders embedded-image documents through rc-players.
External-image loading awaits the player host API; keep that integration at the wrapper.
