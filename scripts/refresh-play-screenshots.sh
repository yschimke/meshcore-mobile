#!/usr/bin/env bash
# Re-renders the Play listing artwork and copies the resulting PNGs into
# app/src/main/play/listings/en-GB/graphics.
#
# Sources of truth:
#   app/src/main/kotlin/.../ui/PlayStorePreviews.kt        (screenshots)
#   app/src/main/kotlin/.../ui/PlayStoreBrandPreviews.kt   (icon + feature)
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

# Play Console rejects icon/feature graphic PNGs that carry an alpha
# channel, even if no pixels are transparent. The Compose renderer
# always emits RGBA, so flatten the brand assets onto opaque RGB.
strip_alpha() {
  local path="$GRAPHICS/$1"
  python3 - "$path" <<'PY'
import sys
from PIL import Image
p = sys.argv[1]
im = Image.open(p)
if im.mode == "RGB":
    sys.exit(0)
bg = Image.new("RGB", im.size, (0, 0, 0))
bg.paste(im, mask=im.split()[-1] if im.mode in ("RGBA", "LA") else None)
bg.save(p, format="PNG")
PY
}

copy ui.PlayStorePreviewsKt.PlayStorePhoneHomeLight_Play_Store_phone_home_light.png      phone-screenshots/01-home-light.png
copy ui.PlayStorePreviewsKt.PlayStorePhoneHomeDark_Play_Store_phone_home_dark.png        phone-screenshots/02-home-dark.png
copy ui.PlayStorePreviewsKt.PlayStorePhoneScannerSaved_Play_Store_phone_scanner_saved.png phone-screenshots/03-saved-devices.png
copy ui.PlayStorePreviewsKt.PlayStorePhoneScannerBle_Play_Store_phone_scanner_BLE.png    phone-screenshots/04-ble-scanner.png
copy ui.PlayStorePreviewsKt.PlayStoreTabletSevenHome_Play_Store_7-inch_tablet_home.png   seven-inch-screenshots/01-home.png
copy ui.PlayStorePreviewsKt.PlayStoreTabletTenHome_Play_Store_10-inch_tablet_home.png    ten-inch-screenshots/01-home.png
copy ui.PlayStoreBrandPreviewsKt.PlayStoreIcon_Play_Store_icon_512x512.png               icon/icon.png
copy ui.PlayStoreBrandPreviewsKt.PlayStoreFeature_Play_Store_feature_graphic_1024x500.png feature-graphic/feature.png
strip_alpha icon/icon.png
strip_alpha feature-graphic/feature.png
