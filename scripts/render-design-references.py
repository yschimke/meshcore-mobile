#!/usr/bin/env python3
"""Capture reference HTML only after checking loaded and actually painted fonts.

python scripts/render-design-references.py [--output DIR] [--repeat N]
Needs playwright==1.62.0 + its Chromium, or CHROMIUM_PATH. Runs offline.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
FAMILIES = {
    'Orbitron': {'Orbitron'},
    'Space Grotesk': {'Space Grotesk', 'Space Grotesk Light'},
    'JetBrains Mono': {'JetBrains Mono'},
}


def ready_fonts(page, timeout_ms=10000):
    """ready alone resolves on failure; explicitly load every declared face."""
    return page.evaluate('''async timeout => {
      let timer;
      const describe = f => `${f.family} ${f.weight} ${f.style}: ${f.status}`;
      try {
        return await Promise.race([
          (async () => {
            for (const family of ['Orbitron', 'Space Grotesk', 'JetBrains Mono']) {
              if (![...document.fonts].some(f => f.family.replaceAll('"', '') === family))
                throw new Error(`missing @font-face: ${family}`);
            }
            await Promise.all([...document.fonts].map(f => f.load()));
            await document.fonts.ready;
            if ([...document.fonts].some(f => f.status !== 'loaded')) throw new Error('unloaded face');
            return [...document.fonts].map(describe);
          })(),
          new Promise((_, reject) => {timer = setTimeout(() => reject(new Error('timed out')), timeout);})
        ]);
      } catch (e) {
        throw new Error(`reference font readiness failed: ${e.message}; ` + [...document.fonts].map(describe).join('; '));
      } finally {clearTimeout(timer);}
    }''', timeout_ms)


def painted_fonts(page):
    """Audit real text nodes, retaining weight/style and Chromium's actual identity."""
    session = page.context.new_cdp_session(page)
    try:
        session.send('DOM.enable')
        session.send('CSS.enable')
        document = session.send('DOM.getDocument')
        nodes = session.send('DOM.querySelectorAll', {
            'nodeId': document['root']['nodeId'], 'selector': 'body *',
        })['nodeIds']
        report = []
        for node in nodes:
            obj = session.send('DOM.resolveNode', {'nodeId': node})['object']['objectId']
            info = session.send('Runtime.callFunctionOn', {
                'objectId': obj, 'returnByValue': True,
                'functionDeclaration': '''function() {
                  if (!this.getClientRects().length || ![...this.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) return null;
                  const s = getComputedStyle(this);
                  return {text: this.textContent.trim(), family: s.fontFamily.split(',')[0].replaceAll('"', '').trim(), weight: s.fontWeight, style: s.fontStyle};
                }''',
            })['result'].get('value')
            if not info or info['family'] not in FAMILIES:
                continue
            fonts = session.send('CSS.getPlatformFontsForNode', {'nodeId': node})['fonts']
            # Every visible text element must paint its intended first family from
            # the committed webfont; a network success or computed style isn't proof.
            if not fonts or any(not f['isCustomFont'] or f['familyName'] not in FAMILIES[info['family']] for f in fonts):
                raise RuntimeError(f"font identity mismatch: {info}; painted {fonts}")
            report.append(dict(info, painted=fonts))
        if not report:
            raise RuntimeError('font audit found no visible text')
        return report
    finally:
        session.detach()


def capture(page, source, output):
    page.goto(source.as_uri(), wait_until='load')
    try:
        faces = ready_fonts(page)
        text = painted_fonts(page)
        data = page.screenshot(path=str(output))
        return {'faces': faces, 'text': text, 'sha256': hashlib.sha256(data).hexdigest()}
    except Exception as error:
        raise RuntimeError(f'{source.name}: {error}') from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'design')
    parser.add_argument('--repeat', type=int, default=1)
    args = parser.parse_args()
    if args.repeat < 1:
        parser.error('--repeat must be positive')
    args.output.mkdir(parents=True, exist_ok=True)
    refs = sorted({entry['ref'] for entry in json.loads((ROOT / 'design-map.json').read_text())['components'] if entry['source'] == 'claude-design'})
    results = {}
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'), args=['--no-sandbox'])
        try:
            for ref in refs:
                previous = None
                for _ in range(args.repeat):
                    context = browser.new_context(viewport={'width': 411, 'height': 914}, device_scale_factor=2.625)
                    # Reference captures must never need a remote font server.
                    context.route('http://**/*', lambda route: route.abort())
                    context.route('https://**/*', lambda route: route.abort())
                    try:
                        result = capture(context.new_page(), ROOT / ref, args.output / (Path(ref).stem + '.png'))
                        if previous and result != previous:
                            raise RuntimeError(f'{ref}: repeated capture is not deterministic')
                        previous = result
                    finally:
                        context.close()
                results[ref] = previous
                print(f"{ref}: fonts verified; sha256={previous['sha256']}")
        finally:
            browser.close()
    (args.output / 'reference-fonts.json').write_text(json.dumps(results, indent=2) + '\n')


if __name__ == '__main__':
    main()
