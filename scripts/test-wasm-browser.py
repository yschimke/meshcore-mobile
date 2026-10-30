#!/usr/bin/env python3
"""Production browser checks with Web Serial streams and companion protocol fixtures."""
import http.server
import json
import os
import re
import struct
import sys
import threading
from pathlib import Path

from playwright.sync_api import expect, sync_playwright

ROOT = Path(sys.argv[1]).resolve()
PREFIX = '/meshcore-mobile/'


class Handler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        if not path.startswith(PREFIX):
            return str(ROOT / 'not-found')
        return str(ROOT / path.removeprefix(PREFIX).split('?')[0])

    def log_message(self, *args):
        pass


server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
self_info = bytes([5, 1, 14, 22]) + bytes([171] * 32) + struct.pack(
    '<iiBBBBiiBB', 53000000, -1500000, 0, 0, 0, 0, 869525000, 125000, 10, 5) + b'browser-radio\0'
radio = bytes([0x19]) + struct.pack('<iiBB', 869525000, 125000, 10, 5)
fixture = r"""
(() => {
  const fixture = window.serialFixture = {requests: 0, gestures: [], opens: 0, closes: 0, writes: [], cancel: true, failHandshake: false};
  let controller, port, opened = false;
  function respond(payload) {
    const frame = Uint8Array.from([0x3e, payload.length & 255, payload.length >> 8, ...payload]);
    // Deliberately fragment the stream header, rather than delivering BLE-style frames.
    controller.enqueue(frame.slice(0, 2));
    controller.enqueue(frame.slice(2));
  }
  port = {
    async open(options) {
      if (options.baudRate !== 115200) throw new Error('Wrong baud rate');
      fixture.opens++; opened = true;
      port.readable = new ReadableStream({start(c) {controller = c;}});
      port.writable = new WritableStream({write(bytes) {
        const frame = Array.from(bytes);
        fixture.writes.push(frame);
        if (frame[0] !== 0x3c || frame[1] + 256 * frame[2] !== frame.length - 3) {
          throw new Error('Bad outbound companion framing');
        }
        const command = frame[3];
        switch (command) {
          case 1: if (!fixture.failHandshake) respond(SELF_INFO); break;
          case 0x16: respond([0x0d, 3, 1, 1]); break;
          case 0x14: respond([0x0c, 0xa0, 0x0f, 0,0,0,0, 0,0,0,0]); break;
          case 0x39: respond(RADIO); break;
          case 4: respond([2]); respond([4]); break;
          case 0x1f: respond([0x12, frame[4], ...Array.from(new TextEncoder().encode('Public')).concat(Array(26).fill(0)), ...Array(16).fill(0)]); break;
          case 0x0a: respond([0x0a]); break;
          case 3: respond([6, 1, 0,0,0,0, 0,0,0,0]); break;
          default: throw new Error('Unexpected companion command ' + command);
        }
      }});
    },
    async close() {
      if (!opened) throw new Error('Port already closed');
      opened = false; fixture.closes++;
    },
  };
  fixture.port = port;
  fixture.unplug = () => controller.error(new Error('USB unplugged'));
  Object.defineProperty(navigator, 'serial', {configurable: true, value: {
    requestPort() {
      fixture.requests++;
      fixture.gestures.push(navigator.userActivation.isActive);
      return fixture.cancel ? Promise.reject(new DOMException('No port chosen', 'NotFoundError')) : Promise.resolve(port);
    },
  }});
})()
""".replace('SELF_INFO', json.dumps(list(self_info))).replace('RADIO', json.dumps(list(radio)))

try:
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'), args=['--no-sandbox'])
        context = browser.new_context(viewport={'width': 1200, 'height': 800})
        context.add_init_script(fixture)
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(f'http://127.0.0.1:{server.server_port}{PREFIX}', wait_until='networkidle')
        button = page.get_by_role('button', name='Connect USB radio', exact=True)
        expect(button).to_be_enabled()
        button.click(force=True)
        expect(page.get_by_text(re.compile('Could not connect'))).to_have_count(1)
        assert page.evaluate('serialFixture.opens') == 0
        page.evaluate('serialFixture.cancel = false')
        page.evaluate('serialFixture.failHandshake = true')
        button.click(force=True)
        expect(page.get_by_role('button', name='Connecting…', exact=True)).to_be_disabled()
        expect(page.get_by_text(re.compile('Could not connect'))).to_have_count(1, timeout=15000)
        expect(button).to_be_enabled()
        assert page.evaluate('serialFixture.opens') == 1
        assert page.evaluate('serialFixture.closes') == 1
        assert page.evaluate('!serialFixture.port.readable.locked && !serialFixture.port.writable.locked')
        page.evaluate('serialFixture.failHandshake = false')
        button.click(force=True)
        disconnect = page.get_by_role('button', name='Disconnect', exact=True)
        expect(disconnect).to_have_count(1, timeout=15000)
        expect(page.get_by_text(re.compile('browser-radio')).first).to_have_count(1)
        channel = page.get_by_role('button', name='Channel', exact=True)
        expect(channel).to_have_count(1)
        channel.dispatch_event('click')
        textbox = page.get_by_role('textbox')
        expect(textbox).to_have_count(1)
        # The accessibility tree appears before Nav3 finishes moving the canvas field.
        textbox.evaluate("""async element => {
          let previous = '', stable = 0;
          while (stable < 5) {
            await new Promise(requestAnimationFrame);
            const rect = element.getBoundingClientRect();
            const current = [rect.x, rect.y, rect.width, rect.height].join(',');
            stable = current === previous ? stable + 1 : 0;
            previous = current;
          }
        }""")
        send = page.get_by_role('button', name='Send', exact=True)
        # Focus can reach Compose's backing field a frame before its text input session listens,
        # and text inserted in that window never reaches the composition, leaving Send disabled.
        # A retry clears the field through the keyboard first, so whatever did arrive late is
        # replaced rather than doubled.
        for attempt in range(3):
            textbox.click(force=True)
            page.wait_for_function("() => { let e=document.activeElement; while(e.shadowRoot?.activeElement) e=e.shadowRoot.activeElement; return e.classList.contains('compose-backing-field'); }")
            if attempt > 0:
                page.keyboard.press('ControlOrMeta+a')
                page.keyboard.press('Backspace')
            page.keyboard.insert_text('Hello from Wasm')
            try:
                expect(send).to_be_enabled(timeout=5000)
                break
            except AssertionError:
                if attempt == 2:
                    raise
        page.evaluate('() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))')
        send.dispatch_event('click')
        expect(page.get_by_text(re.compile('Hello from Wasm')).first).to_have_count(1)
        page.wait_for_timeout(200)
        sent_frames = page.evaluate('serialFixture.writes.filter(frame => frame[3] === 3)')
        assert len(sent_frames) == 1
        assert bytes(sent_frames[0]).endswith(b'Hello from Wasm\0')
        page.get_by_role('button', name='Back', exact=True).dispatch_event('click')
        expect(page.get_by_role('button', name='Back', exact=True)).to_have_count(0)
        channel.dispatch_event('click')
        expect(page.get_by_text(re.compile('Hello from Wasm')).first).to_have_count(1)
        page.get_by_role('button', name='Back', exact=True).dispatch_event('click')
        expect(page.get_by_role('button', name='Back', exact=True)).to_have_count(0)
        assert page.evaluate('serialFixture.gestures.every(Boolean)')
        assert page.evaluate('serialFixture.opens') == 2
        Path('_wasm-test').mkdir(exist_ok=True)
        page.screenshot(path='_wasm-test/meshcore-connected.png')
        disconnect.dispatch_event('click')
        expect(button).to_be_enabled()
        assert page.evaluate('serialFixture.closes') == 2
        assert page.evaluate('!serialFixture.port.readable.locked && !serialFixture.port.writable.locked')
        button.click(force=True)
        expect(disconnect).to_have_count(1)
        page.evaluate('serialFixture.unplug()')
        expect(button).to_be_enabled()
        assert page.evaluate('serialFixture.closes') == 3
        # The connected radio is remembered, and reconnects from its own row.
        expect(page.get_by_text('Your radios', exact=True)).to_have_count(1)
        assert page.evaluate("JSON.parse(localStorage.getItem('meshcore-radios-v1'))[0].name") == 'browser-radio'
        page.get_by_role('button', name='Connect', exact=True).click(force=True)
        expect(disconnect).to_have_count(1)
        disconnect.dispatch_event('click')
        forget = page.get_by_role('button', name='Forget', exact=True)
        expect(forget).to_have_count(1)
        forget.dispatch_event('click')
        expect(page.get_by_text('Your radios', exact=True)).to_have_count(0)
        assert page.evaluate("localStorage.getItem('meshcore-radios-v1')") == '[]'
        assert not errors, errors
        context.close()
        # Unsupported browsers still load the shared app and explain the missing API.
        unsupported = browser.new_context(viewport={'width': 1200, 'height': 800})
        unsupported.add_init_script("delete Object.getPrototypeOf(navigator).serial; delete navigator.serial;")
        page = unsupported.new_page()
        page.goto(f'http://127.0.0.1:{server.server_port}{PREFIX}', wait_until='networkidle')
        expect(page.get_by_role('button', name='Connect USB radio', exact=True)).to_be_disabled()
        expect(page.get_by_text(re.compile('USB connections require Chrome or Edge'))).to_have_count(1)
        browser.close()
        print('Wasm browser: project path, permission gesture, cancel/retry, failed handshake cleanup, companion handshake, chat send/history, disconnect/reconnect, unplug, saved radios and unsupported browser passed')
finally:
    server.shutdown()
