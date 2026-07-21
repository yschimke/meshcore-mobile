#!/usr/bin/env python3
"""Generate subset Noto fallback fonts for MeshCore's non-Latin locales.

MeshCore's branded faces (Orbitron / Space Grotesk / JetBrains Mono) cover
Latin only, but the app ships translations in scripts they don't cover —
Cyrillic (ru), Arabic (ar), Devanagari (hi), Thai (th), and CJK / Hangul /
Kana (ja, ko, zh-rCN, zh-rTW). Without a fallback those glyphs render as tofu
(and, on a device, in whatever OEM font happens to be installed).

This script downloads the upstream Noto families and subsets each to *only*
the characters that actually appear in :meshcore-components' translations, so
the desktop/CMP bundle stays tiny (a few hundred glyphs) instead of shipping
full multi-MB CJK fonts. The generated `*-subset.ttf` files are committed and
loaded by `MeshcoreFonts.desktop.kt`. Android pulls the *same* families from
the downloadable Google Fonts provider at runtime (`MeshcoreFonts.android.kt`),
so nothing is bundled on Android.

Re-run whenever the translations gain characters in a new script/glyph:

    pip install fonttools brotli
    python3 scripts/fonts/generate-fallback-subsets.py

Source: google/fonts @ main (OFL). Requires `curl` + network access to
raw.githubusercontent.com.
"""

import glob
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STRINGS_GLOB = os.path.join(
    ROOT, "meshcore-components/src/commonMain/composeResources/values*/strings.xml"
)
OUT_DIR = os.path.join(
    ROOT, "meshcore-components/src/desktopMain/resources/fonts/noto"
)

RAW = "https://raw.githubusercontent.com/google/fonts/main/ofl"

# Each Noto family covers one script (plus Latin). CJK is split JP/KR/SC/TC —
# no single per-language file carries Kana + Hangul + both Han variants, so we
# bundle all four subsets and let the FontFamily fallback pick per glyph. The
# non-CJK families carry a width+weight variable axis; CJK carry weight only.
SOURCES = [
    ("NotoSans", f"{RAW}/notosans/NotoSans[wdth,wght].ttf"),  # Cyrillic/Greek/Latin-ext
    ("NotoSansArabic", f"{RAW}/notosansarabic/NotoSansArabic[wdth,wght].ttf"),
    ("NotoSansDevanagari", f"{RAW}/notosansdevanagari/NotoSansDevanagari[wdth,wght].ttf"),
    ("NotoSansThai", f"{RAW}/notosansthai/NotoSansThai[wdth,wght].ttf"),
    ("NotoSansJP", f"{RAW}/notosansjp/NotoSansJP[wght].ttf"),  # Kana + Japanese Han
    ("NotoSansKR", f"{RAW}/notosanskr/NotoSansKR[wght].ttf"),  # Hangul + Korean Han
    ("NotoSansSC", f"{RAW}/notosanssc/NotoSansSC[wght].ttf"),  # Simplified Han
    ("NotoSansTC", f"{RAW}/notosanstc/NotoSansTC[wght].ttf"),  # Traditional Han
]


def used_codepoints():
    cps = set()
    files = glob.glob(STRINGS_GLOB)
    if not files:
        sys.exit(f"no strings.xml found under {STRINGS_GLOB}")
    for f in files:
        for el in ET.parse(f).getroot().iter():
            if el.text:
                cps.update(ord(c) for c in el.text)
    # Keep printable ASCII so mixed punctuation in a fallback run renders too.
    cps.update(range(0x20, 0x7F))
    return cps


def fetch(url, dest):
    raw = url.replace("[", "%5B").replace("]", "%5D")
    subprocess.run(["curl", "-sSL", "--fail", "-o", dest, raw], check=True)


def main():
    cps = used_codepoints()
    print(f"{len(cps)} codepoints across the translations")
    os.makedirs(OUT_DIR, exist_ok=True)
    total = 0
    for name, url in SOURCES:
        print(f"  {name:20s}", end=" ", flush=True)
        with tempfile.NamedTemporaryFile(suffix=".ttf", delete=False) as tf:
            src = tf.name
        try:
            fetch(url, src)
            font = TTFont(src)
            # Pin the variable font to its default Regular static instance so the
            # bundled fallback is a single plain weight (no gvar bloat).
            if "fvar" in font:
                axes = {a.axisTag: a.defaultValue for a in font["fvar"].axes}
                if "wght" in axes:
                    axes["wght"] = 400
                if "wdth" in axes:
                    axes["wdth"] = 100
                instantiateVariableFont(font, axes, inplace=True)
            options = subset.Options()
            options.layout_features = ["*"]  # keep shaping (Arabic/Devanagari/Thai)
            options.name_IDs = ["*"]
            options.glyph_names = False
            options.recalc_bounds = True
            ss = subset.Subsetter(options=options)
            ss.populate(unicodes=cps)
            ss.subset(font)
            out = os.path.join(OUT_DIR, f"{name}-subset.ttf")
            font.save(out)
            kb = os.path.getsize(out) // 1024
            total += os.path.getsize(out)
            print(f"-> {name}-subset.ttf  {kb} KB  ({len(font.getGlyphOrder())} glyphs)")
        finally:
            if os.path.exists(src):
                os.remove(src)
    print(f"done: {total // 1024} KB total in {OUT_DIR}")


if __name__ == "__main__":
    main()
