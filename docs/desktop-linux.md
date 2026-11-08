# MeshCore desktop on Linux

Every release attaches a `.deb` for Linux x64. On Arch Linux and its derivatives (CachyOS,
EndeavourOS, Manjaro), install the `meshcore-desktop-bin` package instead. It repackages that
`.deb` with a menu entry, an icon and a `meshcore-desktop` command, and pacman updates it with the
rest of the system.

Its files are named `meshcore-desktop`, not `meshcore`, because the AUR's unrelated `meshcore-bin`
(the closed-source reference companion app) already uses those names. The two can be installed
together.

## pacman repository (recommended)

Every release carries the `meshcore` repository as assets: its package and a database that lists
it. pacman reads them from the [latest release](https://github.com/yschimke/meshcore-mobile/releases/latest),
so it always sees the newest version. The first release to carry it is the one after v0.1.10;
until then pacman reports the database as not found. Add it to `/etc/pacman.conf` once:

```ini
[meshcore]
SigLevel = Optional TrustAll
Server = https://github.com/yschimke/meshcore-mobile/releases/latest/download
```

Then:

```bash
sudo pacman -Syu meshcore-desktop-bin
```

From then on, `sudo pacman -Syu` installs each new release. Each release also carries the
previous version's package, so an update that read the old database just as a new release came out
still finds its download.

If you added the repository with `Server = .../releases/download/pacman-repo`, change it to the
line above. Nothing was ever published there.

The packages are not GPG-signed yet, so `SigLevel = Optional TrustAll` is needed. What you get is
whatever this repository's release workflow uploaded, fetched over HTTPS from GitHub. That's the
same trust as downloading the `.deb` by hand.

## AUR

`meshcore-desktop-bin` is also set up to publish to the AUR for `paru`/`yay` users. It is not live
yet: AUR account registration was paused when this was set up. Once it is,
[`arch-packages.yml`](../.github/workflows/arch-packages.yml) says how to switch it on.

## Build it yourself

```bash
cd packaging/aur/meshcore-desktop-bin
makepkg -si
```

This builds the version named in the checked-in PKGBUILD. CI updates `pkgver` for each release
but doesn't commit it.

## Other distributions

Install the `.deb` from the [latest release](https://github.com/yschimke/meshcore-mobile/releases/latest)
on Debian or Ubuntu (`sudo apt install ./meshcore-desktop-*-linux-x64.deb`). It doesn't update
itself.
