# Play Store graphics

Drop image files directly into the subdirectories below. Gradle Play
Publisher (GPP) auto-uploads them on `publishBundle`. All files in a
given directory are treated as the set of images for that asset, in
filename order.

| Directory | Required? | Format | Dimensions | Notes |
|---|---|---|---|---|
| `icon/` | yes | PNG, 32-bit, no transparency | exactly **512 × 512** | Hi-res app icon shown on the Play listing. Different from the launcher icon. |
| `feature-graphic/` | yes | PNG / JPEG | exactly **1024 × 500** | Banner at the top of the listing. No transparency. |
| `phone-screenshots/` | yes (≥ 2, ≤ 8) | PNG / JPEG | between 320 px and 3840 px on either side; aspect ratio 16:9 or 9:16 | The screenshots Google shows on phone listings. |
| `tablet-screenshots/` | optional | as above | as above | Only if you ship tablet-specific UI. |
| `wear-screenshots/` | leave empty for now | — | — | Wear app publishes separately when its listing exists. |

Tip: capture phone screenshots on a clean Pixel emulator running the
release build — Play has no minimum content requirement but they will
be visible to internal testers and (eventually) the public.

Filenames don't matter; sort order does. Use `01-pair.png`,
`02-chat.png`, etc. if you want to control ordering.
