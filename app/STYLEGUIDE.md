# MeshCore Android — Style Guide

This document defines the visual language used across the MeshCore sample
app. It complements the M3 spec at https://m3.material.io — read that
first for the "why", then use this doc for the "what we picked".

The single source of truth is
[`ui/theme/MeshcoreTheme.kt`](src/main/kotlin/ee/schimke/meshcore/app/ui/theme/MeshcoreTheme.kt).
If a value conflicts with this document, the code wins and this doc
should be updated.

## Principles

1. **Operator-first.** This is a field tool for mesh radio operators.
   Legibility at arm's length, in bright sun or at night, beats
   decoration.
2. **Dark theme is a first-class target.** Every screen must work in
   both light and dark — never hard-code colors, always pull from the
   `ColorScheme`.
3. **Data is content.** Public keys, MAC addresses, frequencies, RSSI
   values — these are the primary payload. Don't bury them under
   decorative chrome, but do label them.
4. **One scale, one palette.** All spacing comes from `Dimens`. All
   colors come from `MaterialTheme.colorScheme`. All type comes from
   `MaterialTheme.typography`. No ad-hoc `dp` or `Color(0x...)` in
   feature code.

## Color

Seed color: `#00695C` (teal). The palette is derived to read as
"signal / radio / network".

### Role → use

| Role | Use for |
|---|---|
| `primary` | Primary action buttons, active tab indicator, FAB |
| `onPrimary` | Text/icons on `primary` |
| `primaryContainer` | Selected chips, active states, hero surfaces |
| `onPrimaryContainer` | Text/icons on `primaryContainer` |
| `secondary` | Secondary actions, less prominent accents |
| `secondaryContainer` | Contact chips, neutral highlights |
| `tertiary` | **Warnings only** — low battery, weak signal, stale advertisement |
| `tertiaryContainer` | Warning backgrounds (badges, banners) |
| `error` | Destructive actions, connection failures |
| `errorContainer` | Error banners |
| `surface` | Screen background |
| `surfaceContainerLow` | Default card background (list rows) |
| `surfaceContainer` | Elevated/featured card (e.g. DeviceSummaryCard) |
| `surfaceContainerHigh` | Modal / dialog surfaces |
| `surfaceVariant` | Dividers, subtle backgrounds (tab row background) |
| `onSurface` | Primary text |
| `onSurfaceVariant` | Secondary text, helper text, icons |
| `outline` | Borders, dividers |
| `outlineVariant` | Subtle dividers (between list items) |

### Do / don't

- **Do** use `tertiary` / `tertiaryContainer` for warnings the user
  should notice but don't require immediate action (battery < 30%,
  RSSI < -90 dBm, stale advert).
- **Do** use `error` / `errorContainer` only for real failures
  (connection refused, send failed) and destructive confirmations
  (Disconnect, Forget).
- **Don't** use raw `Color(0xFF...)` in feature code. Add it to the
  theme first if you genuinely need a new color.
- **Don't** overload `primary`. A screen with three "primary" buttons
  has no primary button.

## Typography

| Style | Use |
|---|---|
| `headlineSmall` | Screen titles in the top bar |
| `titleLarge` | Section headers inside a screen |
| `titleMedium` | Card titles (e.g. contact name, device name) |
| `titleSmall` | Sub-section labels ("Contacts (4)", "Connect by MAC") |
| `bodyLarge` | Primary reading text |
| `bodyMedium` | Default text inside cards |
| `bodySmall` | Helper text, captions, counters |
| `labelLarge` | Button text (set by M3) |
| `labelMedium` | Chip text, tab labels |
| `labelSmall` | Micro-labels (unit suffixes, timestamps) |

**Monospace** (`MeshcoreMonoBody`): use for MAC addresses, pubkey
hex, frequencies, RSSI values — anything a user might copy/paste or
compare character-by-character. Use the proportional body styles for
names and prose.

## Spacing

Use `Dimens` — no literal `dp` in feature code.

| Token | dp | Use |
|---|---|---|
| `XXS` | 2 | Never a gap; only for hairline tweaks |
| `XS` | 4 | Tight chip padding, inline icon→text gap |
| `S` | 8 | Default row padding inside cards |
| `M` | 12 | Card gap, card inner padding |
| `L` | 16 | Screen edge inset, section gap |
| `XL` | 24 | Major section separator |
| `XXL` | 32 | Empty-state illustrations |

`ScreenPadding = L`, `CardGap = M`, `RowGap = S` give us a consistent
rhythm across every screen.

## Shape

- `extraSmall` (4dp) — chips, badges
- `small` (8dp) — list items
- `medium` (12dp) — cards (the default; everything lives on cards)
- `large` (16dp) — dialogs, bottom sheets
- `extraLarge` (28dp) — FAB, large containers

## Components

### Screens

Every screen is a `Scaffold` with:
- `topBar` → `MeshcoreTopBar(title, actions)`
- `content` → column with `ScreenPadding`

The old pattern of `Column + windowInsetsPadding(safeDrawing)` has been
replaced — the Scaffold handles insets. Do not add extra top padding;
let the app bar own the header.

### Cards

- **Elevated card** (`DeviceSummaryCard`): important, one per screen.
  Uses `surfaceContainer`.
- **Filled card** (default list row): uses `surfaceContainerLow`. Same
  shape as elevated, no shadow.
- **Outlined card**: reserved for selectable items (future feature).

Card inner padding is `Dimens.M` (12dp). Rows within a card are
separated by `Dimens.RowGap` (8dp).

### Stateless / Stateful split

Every screen has two composables:

1. `FooScreen(onThing: () -> Unit)` — stateful entry point. Observes
   `MeshcoreApp.get().manager`, wires callbacks, handles navigation.
2. `FooBody(state: …, onThing: () -> Unit, …)` — stateless body.
   Takes plain data and lambdas, renders UI. **This is what gets
   previewed.**

If a screen needs platform-specific content (e.g. BLE permission
flow), `FooBody` takes a `@Composable () -> Unit` slot for that
region and the stateful wrapper supplies the real implementation.
Previews supply a fake. See `ScannerBody` for the reference pattern.

### Icons

Use the Material Icons Extended set (`Icons.Rounded.*`). Rounded
variants match M3's current visual language. Use tinted
`onSurfaceVariant` for inline icons inside body text; use the
component's content color in buttons.

Canonical icon → meaning:

| Icon | Meaning |
|---|---|
| `Bluetooth` | BLE transport |
| `Usb` | USB transport |
| `Lan` | TCP transport |
| `Router` | Repeater contact |
| `Groups` | Room contact |
| `Sensors` | Sensor contact |
| `Person` | Chat contact (default) |
| `BatteryFull` / `Battery3Bar` / `Battery1Bar` | Battery level |
| `SignalCellular4Bar` etc. | RSSI buckets |
| `Warning` | Warning state (tertiary color) |

## Empty, loading, and failure states

Every list must handle all three:

- **Empty** — short sentence in `onSurfaceVariant`, optional illustration
  or icon. Example: "No devices in range. Move closer, or try again."
- **Loading** — `CircularProgressIndicator` inline with a label; or a
  shimmer/placeholder card. Never an empty screen.
- **Failure** — `error` colored text / icon, explain what failed and
  what the user can do next. Example: "Connection refused
  (192.168.1.10:5000). Check the host and port and try again."

## Preview discipline

Every composable that takes data should have at least three previews:
**empty, few (1–2), many (10+)**. Add a **loading** and **failure**
preview if those states exist. Add a **dark theme** preview for any
screen-level composable. All previews wrap in `MeshcoreTheme {}`.

Previews are verified by the `compose-preview` skill — see
`.agents/skills/compose-previews/SKILL.md`.
