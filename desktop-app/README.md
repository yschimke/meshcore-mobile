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
Desktop conversation history is stored in `~/.meshcore/meshcore.db`, using the same Room model
as Android. History belongs to the radio's public key, so reconnecting at a different TCP address
keeps it. Received messages are saved even when their conversation is not open. Interrupted sends
are marked failed on the next connection. History becomes available when connected to that radio;
offline conversation browsing is not implemented yet.
BLE/USB desktop transport and full device-settings integration remain.
The commands channel is usable when configured on the companion radio. Room and repeater
contacts prompt for login before opening chat. Credentials stay in memory and are masked;
sent messages survive leaving a chat during the connection, including an in-flight send.
On Linux, closing the window disconnects asynchronously and quits. On macOS, closing the window
keeps the connection running; click the Dock icon to reopen it, or Quit the application to disconnect.

## Desktop interaction

- Connection menu: reconnect (`Cmd+R` / `Ctrl+R`) and disconnect (`Cmd+Shift+D` / `Ctrl+Shift+D`).
- Window menu: full screen (`Ctrl+Cmd+F` / `F11`) and minimize (`Cmd+M` on macOS).
- Help menu: keyboard shortcut reference.
- Enter sends a nonblank message; holding Enter does not repeat the send. Mobile keyboards show a
  Send action. Message text can be selected and copied.
- The last successful TCP host/port, normal window bounds, and maximized state are saved in
  `~/.meshcore/desktop.properties`. Window bounds are fitted to available monitors when reopening.

## Verification

Run `:desktop-app:jvmTest` and `:meshcore-components:desktopTest` on a graphical desktop. The tests
cover database reopen/address changes, message isolation and deduplication, interrupted sends,
monitor removal, preference round trips, and keyboard submission.

Before releasing, also check the native macOS application menu, Quit, closing/reopening through the
Dock, full-screen transitions, and window restoration on a Mac. Check Linux menu shortcuts and
restoration on both X11 and Wayland. Notifications and tray integration are not included in this slice.

The three Remote Compose device-widget previews have been rendered locally with the embedded
player 2.1.4 and AndroidX Remote Compose alpha21, without linkage errors.

The shared Remote Compose wrapper renders embedded-image documents through rc-players.
External-image loading awaits the player host API; keep that integration at the wrapper.
