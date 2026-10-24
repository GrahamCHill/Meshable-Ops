// This SDK runs only inside an opaque-origin frame. It receives no session tokens or secrets.
export function runtimeDocument(html: string, channel: string) {
  const sdk = `(() => {
    const channel = new MessageChannel(); const pending = new Map(); let sequence = 0;
    channel.port1.onmessage = event => { const item = pending.get(event.data.id); if (!item) return; pending.delete(event.data.id); clearTimeout(item.timer); event.data.error ? item.reject(new Error(event.data.error)) : item.resolve(event.data.result); };
    function request(method, params = {}) { return new Promise((resolve, reject) => { const id = String(++sequence); const timer = setTimeout(() => { pending.delete(id); reject(new Error('The host did not respond. Reload the plugin.')); }, 30000); pending.set(id, {resolve, reject, timer}); channel.port1.postMessage({id, method, params}); }); }
    window.meshableOps = Object.freeze({
      context: () => request('context'), navigate: section => request('navigate', {section}),
      data: Object.freeze({list: (table, offset = 0) => request('list', {table, offset}), get: (table, id) => request('get', {table, id}),
        create: (table, data) => request('create', {table, data}), update: (table, id, data, revision) => request('update', {table, id, data, revision}),
        remove: (table, id, revision) => request('remove', {table, id, revision})})
    });
    window.weaveops = window.meshableOps;
    parent.postMessage({type: 'weaveops:connect', channel: ${JSON.stringify(channel)}}, '*', [channel.port2]);
  })();`
  const policy = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${policy}"><meta name="referrer" content="no-referrer"><script>${sdk}</script></head><body>${html}</body></html>`
}
