#!/usr/bin/env bash
# Re-renders PlayStorePreviews and copies the resulting PNGs into the
# Play Console listing under app/src/main/play/listings/en-GB/graphics.
#
# Source of truth: app/src/main/kotlin/.../ui/PlayStorePreviews.kt
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

./gradlew :app:renderPreviews

RENDERS="app/build/compose-previews/renders"
GRAPHICS="app/src/main/play/listings/en-GB/graphics"

copy() {
  local src="$RENDERS/$1"
  local dst="$GRAPHICS/$2"
  if [ ! -f "$src" ]; then
    echo "missing render: $src" >&2
    exit 1
  fi
  install -m 0644 "$src" "$dst"
  echo "$dst"
}

copy ui.PlayStorePreviewsKt.PlayStorePhoneHomeLight_Play_Store_phone_home_light.png      phone-screenshots/01-home-light.png
copy ui.PlayStorePreviewsKt.PlayStorePhoneHomeDark_Play_Store_phone_home_dark.png        phone-screenshots/02-home-dark.png
copy ui.PlayStorePreviewsKt.PlayStorePhoneScannerSaved_Play_Store_phone_scanner_saved.png phone-screenshots/03-saved-devices.png
copy ui.PlayStorePreviewsKt.PlayStorePhoneScannerBle_Play_Store_phone_scanner_BLE.png    phone-screenshots/04-ble-scanner.png
copy ui.PlayStorePreviewsKt.PlayStoreTabletSevenHome_Play_Store_7-inch_tablet_home.png   seven-inch-screenshots/01-home.png
copy ui.PlayStorePreviewsKt.PlayStoreTabletTenHome_Play_Store_10-inch_tablet_home.png    ten-inch-screenshots/01-home.png
