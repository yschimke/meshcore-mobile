# MeshCore as a Wasmo app

[Wasmo](https://github.com/wasmcomputercompany/wasmo) is a "cloud computer": it installs apps
packaged as `.wasmo` files and serves each one from its own origin,
`https://<app>-<computer>.wasmo.com` (or `*.wasmo.localhost:8080` when run locally).
`:web-app:wasmoApp` packages the Kotlin/Wasm browser build as one of those apps, so a Wasmo owner
can open MeshCore from their Wasmo launcher. The packaging mirrors
[Cadence's](https://github.com/yschimke/cadence/blob/main/docs/wasmo-app.md) and
[Terrazzo's](https://github.com/yschimke/homeassistant-remotecompose/blob/main/docs/architecture/wasmo-app.md).

```
./gradlew :web-app:wasmoApp            # web-app/build/wasmo/meshcore.wasmo (ZIP archive)
./gradlew :web-app:wasmoAppDirectory   # web-app/build/wasmo/dir/meshcore.wasmo/ (dev-mode directory)
```

## Shape

```
meshcore.wasmo/
  wasmo-manifest.toml      # web-app/wasmo/wasmo-manifest.toml, version from the app version
  www/                     # the wasmJsBrowserDistribution output, served to the owner only
    app.html               # the dist's index.html, renamed; + wasm-mime-fallback.js and the
                           #   manifest/icon links
    meshcore-backend.js    # shares the radios list through the install's kv service, then loads
                           #   meshcore.js
    bootstrap.js, meshcore.js, wasm-mime-fallback.js, *.wasm
    assets/icon.png        # launcher icon
  www-public/              # web-app/wasmo/www-public/, served to everyone
    index.html             # landing page: sends the owner to /app.html, explains installing
    manifest.webmanifest   # web app manifest (start_url /app.html), so the app can be installed
    icon-192.png, icon-512.png, apple-touch-icon.png
```

The manifest's integer `version` is `major * 1_000_000 + minor * 1_000 + patch` of `appVersion` in
`web-app/build.gradle.kts`, which release-please bumps with the Android version.

## Connecting a radio

The Wasmo page is the same build as the [GitHub Pages web app](https://yschimke.github.io/meshcore-mobile/),
so it connects the same way:

| Transport | From the Wasmo page | Why |
| --- | --- | --- |
| USB serial | Works, on desktop Chrome or Edge | Web Serial (`meshcore-transport-webserial`), 115200 baud. Each install is a top-level HTTPS page on its own origin, which Web Serial requires. Firefox and Safari have no Web Serial. |
| Bluetooth LE | Not built yet | Possible in principle with Web Bluetooth (desktop Chrome/Edge, Chrome on Android), but the web build has no Web Bluetooth transport; `meshcore-transport-ble` uses Kable on Android and the JVM only. |
| TCP (Wi-Fi companion) | Not available | Browsers can't open raw TCP sockets, and Wasmo has no socket proxy for static apps. |

Messages and contacts aren't persisted in the browser build, so they don't survive a reload. The
list of radios is (see below).

## Public landing page and installing to a device

Wasmo serves `www-public/` to everyone and checks it **before** the owner-only `www/`, and it has
no sign-in redirect: anyone who isn't the signed-in owner gets a bare 404 for every `www/` path.
So `/` is `www-public/index.html` for every visitor, and the app itself is `www/app.html` (the
launcher's `home_path`). The landing page fetches `/app.html`; a 200 means the visitor is the
owner, and it replaces itself with the app, keeping the query and hash. Anyone else, including the
owner signed out of Wasmo, sees what MeshCore is and how to install it, instead of a 404.

The app page links `manifest.webmanifest`, so once open the owner can **Install app** from the
browser. The manifest and its icons sit in `www-public/` because browsers fetch a manifest without
credentials, which the owner-only `www/` would answer with 404. There is no service worker.

## Sharing radios across browsers

The connect screen lists **Your radios**: every radio this owner has connected, by name and public
key, with its USB vendor and product ids. The web app keeps the list in `localStorage`
(`meshcore-radios-v1`). On a Wasmo that enables its per-install services for this install name
(the fork's `WASMO_SERVICE_APPS` allow-list, as for Terrazzo and Cadence), the list follows the
owner to every browser they open MeshCore in. The `.wasmo` build's `app.html` loads
`web-app/wasmo/meshcore-backend.js` in place of `meshcore.js`:

1. It probes `/api/wasmo/v1/capabilities` (one longer retry if the Wasmo doesn't answer).
2. With `kv` and `kvImport`, it reads the install's `radios` value and seeds `localStorage` with it
   before the app starts. The first browser on an empty install uploads what it has with
   `POST kv/import`, which only an empty install accepts; if another browser got there first, its
   list wins.
3. Each time the connect screen shows, and before each change, the app calls
   `globalThis.meshcoreStorage.refresh`, which re-reads the install's list. A change (a radio
   connected or forgotten) is applied to that fresh list, so another browser's additions survive,
   and `meshcoreStorage.changed` `PUT`s it. A failed write stays queued, is retried with the next
   change or when the browser reconnects, and shows a notice.
4. If the install's list can't be read at startup, the page offers a reload rather than starting
   from this browser's copy.

What is shared is the list, not the connection. Web Serial grants a port to one browser only, so
in a new browser **Connect** opens the port picker, narrowed to the radio's USB ids; once granted
there, a radio whose ids match exactly one granted port connects without the picker. A radio
forgotten in one browser while another adds it again keeps the later change.

Without the service (hosted wasmo.com, or an install name not on the list) the list stays in the
browser, as in the static web build, and a dismissible notice says so. Theme choices aren't stored.

## Installing

Wasmo's computer menu has an **Install App** item that takes an app address and a name of your
choice.

1. Use the published `meshcore.wasmo`. Each release attaches it (the
   [`Release`](../.github/workflows/release-build.yml) workflow builds `:web-app:wasmoApp` and
   uploads it under an unversioned name), so the latest build is always at the stable URL
   `https://github.com/yschimke/meshcore-mobile/releases/latest/download/meshcore.wasmo`.
   To host it yourself instead, serve `web-app/build/wasmo/meshcore.wasmo` from any HTTPS URL
   Wasmo can reach.
2. In Wasmo, choose **Install App**, paste the URL, and name it `meshcore` (or whichever name the
   operator lists in `WASMO_SERVICE_APPS`; other names work but keep the radios list in the
   browser).
3. Open it from the launcher, or `https://meshcore-<computer>.wasmo.com/`.

The **Wasmo server**, not your browser, fetches the URL, so it must be reachable from wherever
Wasmo runs. Wasmo does not update an app installed from a URL when the file changes, so updating
means installing again under the same name.

## Wasmo gaps worked around here

- **`.wasm` content type.** Wasmo's content-type table has no `wasm` entry, so `.wasm` may be
  served without `application/wasm`, and `WebAssembly.instantiateStreaming` rejects it.
  `web-app/wasmo/wasm-mime-fallback.js` falls back to compiling from bytes for those responses.
- **Directory entries.** Wasmo's installer writes every zip entry as a file, so a directory entry
  blocks the files beneath it. `wasmoApp` writes file entries only, with fixed timestamps.
