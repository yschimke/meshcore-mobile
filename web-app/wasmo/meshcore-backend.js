// Shares MeshCore's list of radios across the owner's browsers through the Wasmo install's
// key-value service (/api/wasmo/v1/kv). Only the .wasmo build loads this script, in place of
// meshcore.js: it seeds localStorage from the install before the app starts, re-reads the list when
// the app asks, and writes each change back. A Wasmo that doesn't enable services for this install
// name keeps the list in the browser and says so. Adapted from Cadence's cadence-backend.js; see
// docs/wasmo-app.md.
(() => {
  const RADIOS = 'meshcore-radios-v1';
  // Remote keys must match Wasmo's [a-z0-9][a-z0-9._-]{0,63}.
  const REMOTE = { [RADIOS]: 'radios' };

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
    return (await response.json()) ?? {};
  }

  // The list is stored on the Wasmo as JSON, not as a string of JSON.
  const parse = (json) => { try { return JSON.parse(json); } catch { return null; } };

  function apply(remote) {
    for (const [key, name] of Object.entries(REMOTE)) {
      // A write still waiting to reach the Wasmo is newer than what the Wasmo has.
      if (remote[name] != null && !pending.has(key)) localStorage.setItem(key, JSON.stringify(remote[name]));
    }
  }

  async function seed() {
    let remote = await readRemote();
    if (!Object.values(REMOTE).some((name) => remote[name] != null)) {
      // First browser on this install: upload what it has, atomically and only into an empty
      // install. Another browser that won the race answers 409; its values win.
      const initial = {};
      for (const [key, name] of Object.entries(REMOTE)) {
        const value = parse(localStorage.getItem(key));
        if (value != null) initial[name] = value;
      }
      if (Object.keys(initial).length) {
        const response = await request('kv/import', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(initial),
        });
        if (response.status === 409) remote = await readRemote();
        else if (!response.ok) throw new Error(`Radios import failed (${response.status})`);
        else remote = initial;
      }
    }
    apply(remote);
  }

  // Latest unsaved value per key; a failed write is retried with the next change or on reconnect.
  const pending = new Map();
  let flushing = null;
  let warned = false;

  async function flush() {
    for (const [key, json] of [...pending]) {
      const response = await request(`kv/${REMOTE[key]}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: json,
      });
      if (response.status === 413) {
        pending.delete(key);
        showNotice('Your list of radios is too large to save on your Wasmo. Changes are saved in this browser only.');
        continue;
      }
      if (!response.ok) throw new Error(`Radios write failed (${response.status})`);
      // A newer change may have arrived while this one was in flight.
      if (pending.get(key) === json) pending.delete(key);
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
          if (!(key in REMOTE)) return;
          pending.set(key, json);
          warned = false;
          save();
        },
        // Picks up radios another browser added since this page started. Best effort: on failure
        // the app keeps the copy it has.
        async refresh(key) {
          if (!(key in REMOTE)) return;
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
