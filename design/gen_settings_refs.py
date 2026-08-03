#!/usr/bin/env python3
"""Author the Device Settings design-parity references (design/DeviceSettings.*.html).

Hand-authored design intent for the Device Settings screen's discovered "Ready"
state (the buzzer toggle), mirroring the CMP-desktop render of
DeviceSettingsPreview. Tokens are the MeshCore M3 scheme from MeshcoreTokens.kt.
Run from repo root: python3 design/gen_settings_refs.py
"""
import json
import os

LIGHT = dict(
    primary="#006a60", onPrimary="#ffffff", surface="#f4fbf8", onSurface="#161d1b",
    onSurfaceVariant="#3f4946", outlineVariant="#bec9c5",
)
DARK = dict(
    primary="#53dbc9", onPrimary="#003731", surface="#0e1513", onSurface="#dde4e1",
    onSurfaceVariant="#bec9c5", outlineVariant="#3f4946",
)

BACK = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
        'stroke-linecap="round" stroke-linejoin="round"><line x1="20" y1="12" x2="5" y2="12"/>'
        '<polyline points="11 18 5 12 11 6"/></svg>')
GEAR = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle '
        'cx="12" cy="12" r="3.2"/><path d="M19 12a7 7 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7 7 0 0 0-2-1.2'
        'L16 2H8l-.5 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6A7 7 0 0 0 3 12a7 7 0 0 0 .1 1.2l-2 1.6 '
        '2 3.4 2.4-1a7 7 0 0 0 2 1.2L8 22h8l.5-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6A7 7 0 0 0 19 12z"/></svg>')

# System bars (showSystemUi = true): a status bar (9:30 + battery) and a gesture
# nav pill, drawn as non-reflowing overlays. Shared verbatim with gen_chat_refs.py
# — keep the two in sync. This screen's content is flush to the top, so it carries
# the full status-bar inset (14px, the residual Δpos dy it showed once bars added).
SYSBAR_INSET = 14
SYSBAR_MARKUP = (
    '    <div class="sysbar sysbar-top"><span>9:30</span><svg viewBox="0 0 24 12" fill="none" '
    'stroke="currentColor" stroke-width="1.5"><rect x="1" y="1" width="19" height="10" rx="2.5"/>'
    '<rect x="3" y="3" width="11" height="6" rx="1" fill="currentColor" stroke="none"/>'
    '<path d="M22 4.5v3" stroke-width="2" stroke-linecap="round"/></svg></div>\n'
    '    <div class="sysbar sysbar-bottom"><span class="pill"></span></div>'
)


def sysbar_style(inset):
    pad = f" padding-top: {inset}px;" if inset else ""
    return f"""
      /* System bars (showSystemUi = true): status bar + gesture nav pill drawn as
         non-reflowing overlays. padding-top insets the content to the candidate's
         status-bar inset (the residual layout-diff offset this screen showed once
         the bars were added). */
      body {{ position: relative;{pad} }}
      .sysbar {{ position: absolute; left: 0; right: 0; pointer-events: none; color: var(--on-surface); }}
      .sysbar-top {{ top: 0; height: 24px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; font-size: 13px; font-weight: 600; letter-spacing: 0.2px; }}
      .sysbar-top svg {{ width: 22px; height: 12px; }}
      .sysbar-bottom {{ bottom: 7px; display: flex; justify-content: center; }}
      .sysbar-bottom .pill {{ width: 108px; height: 4px; border-radius: 2px; background: currentColor; }}"""


def render(theme, t, fn, png):
    manifest = json.dumps({
        "componentId": f"meshcore-components/src/commonMain/kotlin/ee/schimke/meshcore/components/ui/DeviceSettingsBodyPreviews.kt#{fn}",
        "tokens": {
            "spacing": {"screenPadding": 16, "rowGap": 8},
            "radius": {"switch": 16},
            "colors": {
                "primary": t["primary"].upper(),
                "onPrimary": t["onPrimary"].upper(),
                "surface": t["surface"].upper(),
                "onSurface": t["onSurface"].upper(),
                "onSurfaceVariant": t["onSurfaceVariant"].upper(),
                "outlineVariant": t["outlineVariant"].upper(),
            },
        },
        # The dark export MUST tag its image `theme: "dark"` — design-parity pairs
        # reference and candidate images by variant slot, and the candidate from
        # `DeviceSettingsDarkPreview` is tagged `dark`. An untagged dark reference
        # sits on the `default/compact` slot, finds no candidate there, and the
        # component reports "no candidate render to compare" instead of a diff.
        # Light is the untagged default (see design/gen_chat_refs.py).
        "images": [
            {"state": "default", "size": "compact", "src": png}
            | ({"theme": "dark"} if theme == "dark" else {})
        ],
    }, indent=2)
    return f"""<!doctype html>
<!--
  Claude Design HTML export — Device Settings ({theme}).

  Hand-authored design intent for the Device Settings screen's discovered "Ready"
  state (buzzer toggle), mirroring the CMP-desktop render of DeviceSettingsBody.
  Colours below are the MeshCore Material 3 {theme} scheme from MeshcoreTokens.kt.
  The application/design-parity+json manifest at the end is the machine-readable
  hand-off; the reference PNG is generated from this document (not committed).
  See docs/design-parity.md.
-->
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=411, initial-scale=1" />
    <title>Device Settings · MeshCore ({theme})</title>
    <style>
      :root {{
        --primary: {t['primary']};
        --on-primary: {t['onPrimary']};
        --surface: {t['surface']};
        --on-surface: {t['onSurface']};
        --on-surface-variant: {t['onSurfaceVariant']};
        --outline-variant: {t['outlineVariant']};
      }}
      * {{ box-sizing: border-box; }}
      html, body {{ margin: 0; padding: 0; }}
      body {{
        width: 411px; height: 914px; overflow: hidden;
        background: var(--surface); color: var(--on-surface);
        font-family: "Space Grotesk", "Roboto", "Segoe UI", system-ui, sans-serif;
        -webkit-font-smoothing: antialiased;
        display: flex; flex-direction: column;
      }}
      .topbar {{
        height: 56px; flex: none; display: flex; align-items: center;
        gap: 4px; padding: 0 8px; background: var(--surface);
      }}
      .icon-btn {{
        width: 40px; height: 40px; flex: none; display: flex;
        align-items: center; justify-content: center; color: var(--on-surface);
      }}
      .icon-btn.action {{ color: var(--on-surface-variant); margin-left: auto; margin-right: 8px; }}
      .icon-btn svg {{ width: 24px; height: 24px; }}
      .title {{ flex: 1; font-size: 16px; font-weight: 600; color: var(--on-surface); }}
      .content {{ padding: 8px 16px 0; }}
      .row {{ display: flex; align-items: center; padding: 4px 0; }}
      .row .labels {{ flex: 1; }}
      .row .label {{ font-size: 16px; color: var(--on-surface); }}
      .row .sub {{ font-size: 13px; color: var(--on-surface-variant); }}
      .switch {{ width: 52px; height: 32px; border-radius: 16px; position: relative; flex: none; background: var(--primary); }}
      .switch .thumb {{ position: absolute; top: 6px; right: 6px; width: 20px; height: 20px; border-radius: 50%; background: var(--on-primary); }}
      .divider {{ height: 1px; background: var(--outline-variant); margin: 8px 0; }}
{sysbar_style(SYSBAR_INSET)}
    </style>
  </head>
  <body>
{SYSBAR_MARKUP}
    <div class="topbar">
      <span class="icon-btn" role="button" aria-label="Back">{BACK}</span>
      <div class="title">Device Settings</div>
      <span class="icon-btn action" aria-hidden="true">{GEAR}</span>
    </div>
    <div class="content">
      <div class="row">
        <div class="labels">
          <div class="label">Buzzer</div>
          <div class="sub">On (rtttl)</div>
        </div>
        <div class="switch" role="switch" aria-label="Buzzer" aria-checked="true"><span class="thumb"></span></div>
      </div>
      <div class="divider"></div>
    </div>
    <script type="application/design-parity+json">
{manifest}
    </script>
  </body>
</html>
"""


out = os.path.dirname(__file__)
for theme, t, fn in (("light", LIGHT, "DeviceSettingsPreview"),
                     ("dark", DARK, "DeviceSettingsDarkPreview")):
    path = os.path.join(out, f"DeviceSettings.{theme}.html")
    open(path, "w").write(render(theme, t, fn, f"DeviceSettings.{theme}.png"))
    print("wrote", path)
