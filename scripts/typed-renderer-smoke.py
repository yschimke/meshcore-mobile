#!/usr/bin/env python3
"""Render the packaged typed catalog through its real opaque-origin browser protocol."""
import hashlib
import json
import shutil
import tempfile
import threading
import zipfile
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import sync_playwright

APP = Path(__file__).resolve().parent.parent
ARCHIVE = APP / "ui-builder-catalog-renderer/build/ui-builder-runtime.zip"
OUTPUT = APP / "ui-builder-catalog-renderer/build/smoke"


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="meshcore-renderer-") as staging:
        root = Path(staging)
        with zipfile.ZipFile(ARCHIVE) as archive:
            manifest = json.loads(archive.read("runtime-manifest.json"))
            digest = hashlib.sha256()
            for name in sorted(archive.namelist()):
                assert not name.startswith("/") and ".." not in name.split("/")
                if name.endswith("/") or name == "runtime-manifest.json":
                    continue
                data = archive.read(name)
                digest.update(name.encode() + b"\0" + str(len(data)).encode() + b"\0" + data)
            assert digest.hexdigest() == manifest["integritySha256"]
            runtime_id = manifest["runtimeId"]
            runtime = root / "ui-builder/runtime" / runtime_id
            runtime.mkdir(parents=True)
            archive.extractall(runtime)
        root.joinpath("index.html").write_text(f'''<!doctype html><html><body style="margin:0">
<iframe sandbox="allow-scripts" src="/ui-builder/runtime/{runtime_id}/index.html"
style="width:500px;height:180px;border:0"></iframe><script>
window.messages=[];
addEventListener('message', e=>{{
  if(typeof e.data==='string') {{try {{messages.push(JSON.parse(e.data))}} catch {{}}}}
}});
window.request=(requestId,type,payload)=>document.querySelector('iframe').contentWindow.postMessage(
  JSON.stringify({{schema:'compose-ui-builder-renderer/v2',protocolVersion:2,
    runtimeId:'{runtime_id}',requestId,type,payload}}),'*');
</script></body></html>''')

        class Handler(SimpleHTTPRequestHandler):
            def __init__(self, *args, **kwargs):
                super().__init__(*args, directory=str(root), **kwargs)

            def end_headers(self):
                self.send_header("Access-Control-Allow-Origin", "*")
                super().end_headers()

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(
                    executable_path=shutil.which("chromium"),
                    headless=True,
                    args=["--no-sandbox", "--enable-unsafe-swiftshader"],
                )
                page = browser.new_page(viewport={"width": 520, "height": 200})
                errors = []
                page.on("pageerror", lambda error: errors.append(str(error)))
                page.goto(f"http://127.0.0.1:{server.server_port}")
                # Distinct request IDs allow initialization to wait for Wasm startup.
                for index in range(60):
                    page.evaluate('(id)=>request(id,"initialize",{})', f"init-{index}")
                    if page.evaluate('messages.some(m=>m.type==="initialized")'):
                        break
                    page.wait_for_timeout(1000)
                else:
                    raise AssertionError(("runtime did not initialize", errors))

                def render(name, width, height):
                    document = json.loads((APP / f"ui-builder-catalog/fixtures/{name}.uid").read_text())
                    document["catalogPin"]["nativeRuntimeId"] = runtime_id
                    request_id = f"render-{name}"
                    page.evaluate(
                        "({id,payload})=>request(id,'renderDocument',payload)",
                        {"id": request_id, "payload": {"document": document, "surface": {
                            "mode": "authoring-unrolled", "widthDp": width, "heightDp": height,
                            "density": 1, "surfaceId": name,
                        }}},
                    )
                    page.wait_for_function(
                        'id=>messages.some(m=>m.type==="rendered" && m.requestId===id)',
                        arg=request_id, timeout=60000,
                    )
                    message = next(message for message in page.evaluate("messages")
                                   if message["type"] == "rendered" and message["requestId"] == request_id)
                    inspection = message["payload"]["inspection"]
                    generation = inspection["generation"]
                    assert set(generation["measuredNodeIds"]) == set(generation["expectedAuthoredNodeIds"])
                    return inspection

                inspection = render("chat-input", 500, 180)
                assert set(inspection["generation"]["measuredTextNodeIds"]) == {"echo", "status"}
                page.screenshot(path=str(OUTPUT / "wasm-chat-input.png"))
                before_width = next(node["bounds"]["width"] for node in inspection["nodes"]
                                    if node["nodeId"] == "status")
                # Genuine pointer input invokes the real component and its typed send callback.
                page.mouse.click(468, 36)
                page.wait_for_function(
                    'width=>messages.some(m=>m.type==="inspectionUpdated" && '
                    'm.payload.inspection.nodes.some(n=>n.nodeId==="status" && n.bounds.width>width))',
                    arg=before_width, timeout=15000,
                )
                page.screenshot(path=str(OUTPUT / "wasm-chat-input-sent.png"))
                # The complete sheet exercises nested model values and the actual app dialogs.
                page.set_viewport_size({"width": 1000, "height": 1600})
                page.evaluate('document.querySelector("iframe").style.cssText="width:1000px;height:1600px;border:0"')
                render("component-sheet", 1000, 1600)
                page.screenshot(path=str(OUTPUT / "wasm-component-sheet.png"))
                assert not errors, errors
                (OUTPUT / "result.json").write_text(json.dumps({
                    "manifest": manifest, "messages": page.evaluate("messages"), "errors": errors,
                }, indent=2))
                browser.close()
        finally:
            server.shutdown()
    print("Runtime integrity, opaque iframe rendering, measured nodes and send interaction pass")


if __name__ == "__main__":
    main()
