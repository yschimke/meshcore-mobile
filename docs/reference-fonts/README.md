# Portable reference fonts (#361)

Current main was checked at `8a22cc7`. Its reference HTML has named font stacks
but no sources. The parity workflow now installs the bundled fonts, so the old
report is conditional, not proof that current CI still falls back.

Using the original main HTML and Playwright 1.62.0's Chromium 151.0.7922.34:

| Host font set | Actual painted `node-peak` heading | Evidence |
| --- | --- | --- |
| This host, no branded fonts | DejaVu Sans | [identity](host.json) |
| Space Grotesk installed, Orbitron absent | Space Grotesk Light | [before PNG](before-space-grotesk.png), [identity](space.json) |
| All three bundled fonts installed | Orbitron | [current-main PNG](current-main-installed-fonts.png), [identity](all.json) |
| Explicit reference webfonts | Orbitron, custom webfont, CSS weight 500 normal | [after PNG](after-orbitron.png), [font audit](after-fonts.json) |

The original report's fallback is reproducible without Orbitron installed.
The current installed-font lane resolves Orbitron locally. **Retain the system
font installation/staging workaround**: neither hosted CI nor the served catalog
was regenerated or inspected by this change. No upstream bug or unrelated
workaround was removed.

Every reference HTML now links `reference-fonts.css`, including both generators.
The stylesheet points directly at the unchanged committed variable TTFs used by
the app: Orbitron 400–900, Space Grotesk 300–700 and JetBrains Mono 100–800,
normal style. It has no `local()` or remote font-server sources. App font
choices and candidate rendering are unchanged.

The checked parity capture uses Playwright at the existing 411×914 CSS viewport
and 2.625 density. It loads all faces explicitly, checks readiness, then queries
Chromium's actual painted identities for visible text. The JSON audit records
requested family, weight/style, loaded status and painted identity. It refuses
missing declarations, failed/corrupt faces, wrong font bytes and empty audits.
The workflow captures twice in fresh contexts and rejects unequal results.

```sh
python -m pip install playwright==1.62.0
python -m playwright install --with-deps chromium
python scripts/test-reference-fonts.py
python scripts/render-design-references.py --output build/reference-fonts --repeat 2
```

Five browser regressions pass with the pinned browser. They exercise all twelve
light/dark HTML documents twice, assert the fixture includes all three families
and weights 400/500/600/700, compare captures across different installed font
sets, and test missing declarations, corrupt bytes, and a successful HTTP/font
load carrying the wrong family. Before adding the font declarations, the
all-document regression reports `missing @font-face: Orbitron` for all twelve
files. The separate PR workflow uploads generated PNGs and the full audit.

Seven unique HTML references currently appear in design-map.json; all seven
were also captured twice from fresh browser contexts with identical hashes.
The representative after image's SHA-256 is
`1c7b90f2b6b66779810343cd9b2eb6a9e3cb50ae068a086845a4aee5fb715a7e`.
The same checks also passed with local Chromium 156 as an additional smoke check;
PNG bytes are only compared within a fixed browser/rasterization environment.

This changes reference tooling only, so Android/Gradle app builds were not run.
No live renderer, token, or hosted deployment is required for these tests.

Release ordering: these HTML declarations and the local parity capture need no
tooling release or dependency bump. The companion design-parity change guards
failed webfonts in the shared export driver. That guard reaches the hosted
catalog export after a design-parity release, a compose-ai-tools driver
package.json **and lockfile** bump, a Tools release, and a MeshCore workflow pin
bump. Do not claim a caret range or a tag-only bump enables it before the frozen
lockfile moves. This PR does not bump those dependencies. Hosted end-to-end
verification remains a separate follow-up after that release chain.
