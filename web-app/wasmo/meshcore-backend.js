// Shares MeshCore's list of radios across the owner's browsers through the Wasmo install's
// key-value service (/api/wasmo/v1/kv). Only the .wasmo build loads this script, in place of
// meshcore.js: it seeds localStorage from the install before the app starts, re-reads the list when
// the app asks, and writes each change back. Each radio is its own kv value (radio.<key prefix>), so
// browsers changing different radios at the same time never overwrite each other. A Wasmo that doesn't enable services for this install
// name keeps the list in the browser and says so. Adapted from Cadence's cadence-backend.js; see
// docs/wasmo-app.md.
(() => {
  // The app's whole list, as JSON, in localStorage.
  const RADIOS = 'meshcore-radios-v1';
  // Remote keys must match Wasmo's [a-z0-9][a-z0-9._-]{0,63}: 'radio.' and 56 hex digits of the key.
  const PREFIX = 'radio.';
  const remoteKey = (radio) => PREFIX + String(radio.publicKey).toLowerCase().slice(0, 56);

  function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 10000);
    const headers = new Headers(options.headers);
    headers.set('X-Wasmo-Api', '1');
    // A relative API path is deliberate: the owner's cookie must never leave the app origin.
    return fetch(`/api/wasmo/v1/${path}`, {
      method: options.method ?? 'GET', body: options.body, headers,
      credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal: controller.signal,
    }).finally(() => clearTimeout(timeout));
  }

  const isJson = (response) => /^application\/json(?:\s*;|$)/i.test(response.headers.get('content-type') ?? '');

  // 'kv' when the install has the service; otherwise why not: 'not-enabled' or 'unavailable'.
  async function probe(timeoutMs) {
    try {
      const response = await request('capabilities', { timeoutMs });
      if (response.status >= 500) return 'unavailable';
      if (response.status !== 200 || !isJson(response)) return 'not-enabled';
      const caps = await response.json().catch(() => null);
      return caps?.api === 1 && caps.kv === true && caps.kvImport === true ? 'kv' : 'not-enabled';
    } catch {
      return 'unavailable';
    }
  }

  async function readRemote() {
    const response = await request('kv');
    if (!response.ok || !isJson(response)) throw new Error(`Radios read failed (${response.status})`);
    const all = (await response.json()) ?? {};
    return new Map(Object.entries(all).filter(([name, value]) => name.startsWith(PREFIX) && value?.publicKey));
  }

  function readLocal() {
    try {
      const radios = JSON.parse(localStorage.getItem(RADIOS) ?? '[]');
      return new Map((Array.isArray(radios) ? radios : []).filter((r) => r?.publicKey).map((r) => [remoteKey(r), r]));
    } catch {
      return new Map();
    }
  }

  // What this browser believes the install holds, plus its own unsent changes: the diff base for
  // the app's next change.
  let known = new Map();
  // Unsent changes per remote key: the radio to PUT, or null to DELETE.
  const pending = new Map();

  function apply(remote) {
    known = new Map(remote);
    for (const [name, radio] of pending) {
      if (radio) known.set(name, radio); else known.delete(name);
    }
    const radios = [...known.values()].sort((a, b) => (b.lastConnectedMs ?? 0) - (a.lastConnectedMs ?? 0));
    localStorage.setItem(RADIOS, JSON.stringify(radios));
  }

  async function seed() {
    let remote = await readRemote();
    if (!remote.size) {
      // First browser on this install: upload what it has, atomically and only into an empty
      // install. Another browser that won the race answers 409; its values win.
      const local = readLocal();
      if (local.size) {
        const response = await request('kv/import', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(Object.fromEntries(local)),
        });
        if (response.status === 409) remote = await readRemote();
        else if (!response.ok) throw new Error(`Radios import failed (${response.status})`);
        else remote = local;
      }
    }
    apply(remote);
  }

  // Turns the app's new list into per-radio writes against what the install is believed to hold.
  function changed(json) {
    let radios;
    try { radios = JSON.parse(json); } catch { return; }
    if (!Array.isArray(radios)) return;
    const next = new Map(radios.filter((r) => r?.publicKey).map((r) => [remoteKey(r), r]));
    for (const [name, radio] of next) {
      if (JSON.stringify(known.get(name)) !== JSON.stringify(radio)) pending.set(name, radio);
    }
    for (const name of known.keys()) if (!next.has(name)) pending.set(name, null);
    known = next;
  }

  let flushing = null;
  let warned = false;

  async function flush() {
    for (const [name, radio] of [...pending]) {
      const response = await request(`kv/${name}`, radio
        ? { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(radio) }
        : { method: 'DELETE' });
      if (response.status === 413) {
        pending.delete(name);
        showNotice('Your Wasmo has no room for more radios. New ones are saved in this browser only.');
        continue;
      }
      if (!response.ok) throw new Error(`Radios write failed (${response.status})`);
      // A newer change may have arrived while this one was in flight.
      if (pending.get(name) === radio) pending.delete(name);
    }
  }

  function save() {
    flushing ??= flush()
      .then(() => { warned = false; })
      .catch(() => {
        if (warned) return;
        warned = true;
        showNotice('Changes couldn’t be saved to your Wasmo. They’re kept in this browser and retried with your next change.');
      })
      .finally(() => {
        flushing = null;
        if (pending.size && !warned) save();
      });
  }

  function showNotice(text) {
    const notice = document.createElement('div');
    notice.setAttribute('role', 'status');
    notice.style.cssText = 'position:fixed;inset:0 0 auto 0;z-index:2147483647;display:flex;gap:12px;align-items:center;'
      + 'padding:8px 12px;font:14px/1.4 system-ui,sans-serif;background:#fff4d6;color:#3d2e00;box-shadow:0 1px 4px #0003';
    const message = document.createElement('span');
    message.style.flex = '1';
    message.textContent = text;
    const close = document.createElement('button');
    close.textContent = 'Dismiss';
    close.addEventListener('click', () => notice.remove());
    notice.append(message, close);
    // Outside <body>: Compose replaces the body's children when it mounts.
    document.documentElement.append(notice);
  }

  function showStartupError() {
    const loading = document.querySelector('#app .loading');
    if (!loading) return;
    const retry = document.createElement('button');
    retry.textContent = 'Reload';
    retry.addEventListener('click', () => location.reload());
    loading.replaceChildren('Your radios on your Wasmo could not be loaded. ', retry);
  }

  function startApp() {
    const script = document.createElement('script');
    script.src = 'meshcore.js';
    document.body.append(script);
  }

  (async () => {
    let mode = await probe(3000);
    // A backend that is starting up or briefly overloaded gets one longer second chance.
    if (mode === 'unavailable') mode = await probe(6000);
    if (mode === 'kv') {
      try {
        await seed();
      } catch {
        // Starting from this browser's copy would silently fork the owner's list.
        showStartupError();
        return;
      }
      globalThis.meshcoreStorage = {
        changed(key, json) {
          if (key !== RADIOS) return;
          changed(json);
          warned = false;
          if (pending.size) save();
        },
        // Picks up radios other browsers changed since this page started. Best effort: on failure
        // the app keeps the copy it has.
        async refresh(key) {
          if (key !== RADIOS) return;
          try { apply(await readRemote()); } catch {}
        },
      };
      addEventListener('online', () => { if (pending.size) save(); });
    } else {
      showNotice(mode === 'not-enabled'
        ? 'This Wasmo isn’t sharing your radios between your browsers. Install the app under the name your Wasmo enables for it (usually “meshcore”). Until then, they’re saved in this browser only.'
        : 'Your Wasmo didn’t answer, so your radios are saved in this browser only for now. Reload to try again.');
    }
    startApp();
  })();
})();
