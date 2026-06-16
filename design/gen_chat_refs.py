#!/usr/bin/env python3
"""Author the chat-screen design-parity references (design/*.html).

Hand-authored design intent for the chat screens, mirroring the CMP-desktop
renders of ChatBodyPreviews. One template, three screens (contact 1:1, channel
group, commands), two themes. Tokens are the MeshCore M3 scheme from
MeshcoreTokens.kt; values match docs/design-parity.md + the Device reference.
Run from repo root: python3 design/gen_chat_refs.py
"""
import html
import os

LIGHT = dict(
    primary="#006a60", primaryContainer="#74f8e5", onPrimaryContainer="#00201c",
    surface="#f4fbf8", onSurface="#161d1b", onSurfaceVariant="#3f4946",
    surfaceContainerHigh="#e2e9e6", outline="#6f7976", error="#ba1a1a",
)
DARK = dict(
    primary="#53dbc9", primaryContainer="#005048", onPrimaryContainer="#74f8e5",
    surface="#0e1513", onSurface="#dde4e1", onSurfaceVariant="#bec9c5",
    surfaceContainerHigh="#242b29", outline="#89938f", error="#ffb4ab",
)

BACK = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
        'stroke-linecap="round" stroke-linejoin="round"><line x1="20" y1="12" x2="5" y2="12"/>'
        '<polyline points="11 18 5 12 11 6"/></svg>')
SEND = ('<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3.4 20.4 21 12 3.4 3.6 3 10l12 2-12 2z"/></svg>')
TERMINAL = ('<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
            'stroke-linecap="round" stroke-linejoin="round"><polyline points="5 7 9 11 5 15"/>'
            '<line x1="12" y1="16" x2="18" y2="16"/></svg>')

# Messages mirror ChatBodyPreviews (sender, text, snr, mine, status, displayed time).
contact = [
    ("in", None, None, "hey — are you on tonight?", "14/11 21:58", None),
    ("out", None, None, "yeah, firing up the node now", "14/11 21:59", "Delivered"),
    ("in", None, None, "nice — I'll relay through bob-repeater", "14/11 22:11", None),
    ("out", None, None, "sounds good, sending a test ping", "14/11 22:12", "Sent"),
]
channel = [
    ("in", "alice", 7, "anyone near the north ridge?", "14/11 21:53", None),
    ("in", "bob-repeater", 3, "I've got line of sight, relaying", "14/11 21:55", None),
    ("out", None, None, "copy — net looks healthy", "14/11 22:08", "Delivered"),
    ("out", None, None, "broadcasting weather update", "14/11 22:13", "Sending…"),
]
commands = [
    ("out", None, None, "get bat", "14/11 22:10", "Sent"),
    ("in", "device", 9, "battery: 3980 mV (97%)", "14/11 22:10", None),
    ("out", None, None, "get freq", "14/11 22:12", "Confirmed→Delivered"),
    ("in", "device", 9, "freq: 869.525 MHz · BW 125 kHz · SF 10 · CR 5", "14/11 22:12", None),
]
# normalise the one "Confirmed" → its display label "Delivered"
commands[2] = ("out", None, None, "get freq", "14/11 22:12", "Delivered")


def bubble(side, sender, snr, text, time, status):
    mine = side == "out"
    header = ""
    if not mine and sender:
        snr_html = f'<span class="snr">SNR {snr}</span>' if snr is not None else ""
        header = f'<div class="who-row"><span class="who">{html.escape(sender)}</span>{snr_html}</div>'
    foot = f'<span>{time}</span>'
    if mine and status:
        foot += f'<span class="dot">·</span><span>{html.escape(status)}</span>'
    return (f'<div class="row {"mine" if mine else "them"}">'
            f'<div class="bubble">{header}'
            f'<div class="text">{html.escape(text)}</div>'
            f'<div class="foot">{foot}</div></div></div>')


def render(theme_name, t, title, subtitle, msgs, placeholder, terminal, component_id, png):
    sub = f'<div class="subtitle">{html.escape(subtitle)}</div>' if subtitle else ""
    action = f'<span class="icon-btn action">{TERMINAL}</span>' if terminal else ""
    bubbles = "\n      ".join(bubble(*m) for m in msgs)
    import json
    manifest = json.dumps({
        "componentId": component_id,
        "tokens": {
            "spacing": {"listPadding": 12, "bubblePadding": 12, "inputPadding": 8, "bubbleGap": 4},
            "radius": {"bubble": 12, "input": 24},
            "colors": {
                "primary": t["primary"].upper(),
                "primaryContainer": t["primaryContainer"].upper(),
                "onPrimaryContainer": t["onPrimaryContainer"].upper(),
                "surface": t["surface"].upper(),
                "onSurface": t["onSurface"].upper(),
                "onSurfaceVariant": t["onSurfaceVariant"].upper(),
                "surfaceContainerHigh": t["surfaceContainerHigh"].upper(),
                "outline": t["outline"].upper(),
            },
        },
        "images": [{"state": "default", "size": "compact", "src": png}],
    }, indent=2)
    return f"""<!doctype html>
<!--
  Claude Design HTML export — {html.escape(title)} chat ({theme_name}).

  Hand-authored design intent for the MeshCore chat screen as rendered by the
  ChatBody preview, mirroring its CMP-desktop render. Colours, radii and spacing
  below are the MeshCore Material 3 {theme_name} scheme (seed teal #006A60) from
  MeshcoreTokens.kt. The application/design-parity+json manifest at the end is the
  machine-readable hand-off (componentId, tokens, reference PNG src); the PNG is
  generated from this document (not committed). See docs/design-parity.md.
-->
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=411, initial-scale=1" />
    <title>{html.escape(title)} · MeshCore ({theme_name})</title>
    <style>
      :root {{
        --primary: {t['primary']};
        --primary-container: {t['primaryContainer']};
        --on-primary-container: {t['onPrimaryContainer']};
        --surface: {t['surface']};
        --on-surface: {t['onSurface']};
        --on-surface-variant: {t['onSurfaceVariant']};
        --surface-container-high: {t['surfaceContainerHigh']};
        --outline: {t['outline']};
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
      .icon-btn.action {{ color: var(--on-surface-variant); margin-left: auto; }}
      .icon-btn svg {{ width: 24px; height: 24px; }}
      .titles {{ flex: 1; display: flex; flex-direction: column; justify-content: center; }}
      .titles .title {{ font-size: 16px; font-weight: 600; color: var(--on-surface); }}
      .titles .subtitle {{ font-size: 13px; color: var(--on-surface-variant); }}
      .messages {{
        flex: 1; overflow: hidden; padding: 8px 12px;
        display: flex; flex-direction: column; gap: 4px;
      }}
      .row {{ display: flex; }}
      .row.them {{ justify-content: flex-start; padding-right: 48px; }}
      .row.mine {{ justify-content: flex-end; padding-left: 48px; }}
      .bubble {{ padding: 8px 12px; border-radius: 12px; max-width: 100%; }}
      .row.them .bubble {{
        background: var(--surface-container-high); color: var(--on-surface);
        border-bottom-left-radius: 4px;
      }}
      .row.mine .bubble {{
        background: var(--primary-container); color: var(--on-primary-container);
        border-bottom-right-radius: 4px;
      }}
      .who-row {{ display: flex; align-items: center; gap: 8px; }}
      .who {{ font-size: 12px; font-weight: 600; color: var(--primary); }}
      .snr {{ font-size: 11px; opacity: 0.55; }}
      .text {{ font-size: 15px; line-height: 1.3; }}
      .foot {{ display: flex; gap: 6px; font-size: 11px; opacity: 0.55; margin-top: 3px; }}
      .input {{
        flex: none; display: flex; align-items: center; gap: 8px; padding: 8px;
      }}
      .field {{
        flex: 1; height: 48px; border: 1px solid var(--outline); border-radius: 24px;
        display: flex; align-items: center; padding: 0 18px;
        color: var(--on-surface-variant); font-size: 15px;
      }}
      .send {{ width: 44px; height: 44px; flex: none; display: flex;
        align-items: center; justify-content: center; color: var(--on-surface-variant); }}
      .send svg {{ width: 24px; height: 24px; }}
    </style>
  </head>
  <body>
    <div class="topbar">
      <span class="icon-btn">{BACK}</span>
      <div class="titles"><div class="title">{html.escape(title)}</div>{sub}</div>
      {action}
    </div>
    <div class="messages">
      {bubbles}
    </div>
    <div class="input">
      <div class="field">{html.escape(placeholder)}</div>
      <span class="send">{SEND}</span>
    </div>
    <script type="application/design-parity+json">
{manifest}
    </script>
  </body>
</html>
"""


SCREENS = [
    ("ContactChat", "alice", "Direct message", contact, "Message", False,
     "ContactChatPreview", "ContactChatDarkPreview"),
    ("ChannelChat", "General", "Channel 0", channel, "Message", False,
     "ChannelChatPreview", "ChannelChatDarkPreview"),
    ("Commands", "Commands", None, commands, "Enter command…", True,
     "CommandsPreview", "CommandsDarkPreview"),
]
CID = "meshcore-components/src/commonMain/kotlin/ee/schimke/meshcore/components/ui/ChatBodyPreviews.kt#{}"

out_dir = os.path.join(os.path.dirname(__file__))
for base, title, subtitle, msgs, placeholder, terminal, light_fn, dark_fn in SCREENS:
    for theme, t, fn in (("light", LIGHT, light_fn), ("dark", DARK, dark_fn)):
        path = os.path.join(out_dir, f"{base}.{theme}.html")
        png = f"{base}.{theme}.png"
        with open(path, "w") as f:
            f.write(render(theme, t, title, subtitle, msgs, placeholder, terminal,
                           CID.format(fn), png))
        print("wrote", path)
