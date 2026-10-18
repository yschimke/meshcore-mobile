#!/usr/bin/env python3
"""Offline browser regressions for reference font readiness and identity."""
import importlib.util
import os
import subprocess
from pathlib import Path
import tempfile
import unittest

from playwright.sync_api import sync_playwright

spec = importlib.util.spec_from_file_location('references', Path(__file__).with_name('render-design-references.py'))
references = importlib.util.module_from_spec(spec)
spec.loader.exec_module(references)


class ReferenceFontsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'), args=['--no-sandbox'])

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.context = self.browser.new_context(viewport={'width': 411, 'height': 914}, device_scale_factor=2.625)
        self.context.route('https://**/*', lambda route: route.abort())
        self.page = self.context.new_page()

    def tearDown(self):
        self.context.close()

    def test_all_html_is_deterministic_and_uses_intended_fonts(self):
        sources = sorted((references.ROOT / 'design').glob('*.html'))
        self.assertEqual(len(sources), 12, 'guard: exercise every current light/dark document')
        weights = set()
        families = set()
        with tempfile.TemporaryDirectory() as output:
            for source in sources:
                with self.subTest(source=source.name):
                    first = references.capture(self.page, source, Path(output) / 'first.png')
                    second = references.capture(self.page, source, Path(output) / 'second.png')
                    self.assertEqual(first, second)
                    for text in first['text']:
                        families.add(text['family'])
                        weights.add(text['weight'])
                        self.assertEqual(text['style'], 'normal')
        self.assertEqual(families, set(references.FAMILIES))
        self.assertTrue({'400', '500', '600', '700'} <= weights, weights)

    def test_capture_matches_with_and_without_host_branded_fonts(self):
        with tempfile.TemporaryDirectory() as output:
            output = Path(output)
            config = output / 'fonts.conf'
            font_dir = references.ROOT / 'meshcore-components/src/desktopMain/resources/fonts'
            config.write_text(f'<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd"><fontconfig><include>/etc/fonts/conf.d</include><dir>{font_dir}</dir><cachedir>{output}/cache</cachedir></fontconfig>')
            env = dict(os.environ, FONTCONFIG_FILE=str(config))
            original = subprocess.check_output(['fc-list'])
            branded = subprocess.check_output(['fc-list'], env=env)
            self.assertNotEqual(original, branded, 'guard: host font sets must differ')
            self.assertIn(b'Orbitron', branded)
            source = references.ROOT / 'design/DeviceScreen.light.html'
            baseline = references.capture(self.page, source, output / 'baseline.png')
            browser = self.playwright.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'), args=['--no-sandbox'], env=env)
            try:
                page = browser.new_page(viewport={'width': 411, 'height': 914}, device_scale_factor=2.625)
                actual = references.capture(page, source, output / 'branded.png')
                self.assertEqual(actual, baseline)
            finally:
                browser.close()

    def test_missing_declaration_fails_even_when_fonts_ready_resolves(self):
        source = references.ROOT / 'design/DeviceScreen.light.html'
        self.page.route('**/reference-fonts.css', lambda route: route.fulfill(body='', content_type='text/css'))
        self.page.goto(source.as_uri())
        self.page.evaluate('document.fonts.ready')
        self.assertEqual(self.page.evaluate('document.fonts.size'), 0)
        with self.assertRaisesRegex(Exception, 'missing @font-face: Orbitron'):
            references.ready_fonts(self.page)

    def test_corrupt_font_failure_identifies_family_and_status(self):
        self.page.route('**/Orbitron.ttf', lambda route: route.fulfill(body=b'broken font', content_type='font/ttf'))
        self.page.goto((references.ROOT / 'design/DeviceScreen.light.html').as_uri())
        self.page.evaluate('document.fonts.ready')
        self.assertIn('error', self.page.evaluate('[...document.fonts].map(f => f.status)'))
        with self.assertRaisesRegex(Exception, r'reference font readiness failed:.*Orbitron.*error'):
            references.ready_fonts(self.page)

    def test_successful_request_with_wrong_font_bytes_fails_identity(self):
        wrong = (references.ROOT / 'meshcore-components/src/desktopMain/resources/fonts/SpaceGrotesk.ttf').read_bytes()
        self.page.route('**/Orbitron.ttf', lambda route: route.fulfill(body=wrong, content_type='font/ttf'))
        self.page.goto((references.ROOT / 'design/DeviceScreen.light.html').as_uri())
        references.ready_fonts(self.page)
        self.assertTrue(all('loaded' in f for f in references.ready_fonts(self.page)))
        with self.assertRaisesRegex(RuntimeError, r'font identity mismatch:.*Orbitron.*Space Grotesk'):
            references.painted_fonts(self.page)


if __name__ == '__main__':
    unittest.main()
