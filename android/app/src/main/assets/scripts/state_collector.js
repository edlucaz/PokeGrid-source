// Passive telemetry: hooks WebSocket/fetch to keep a snapshot of the game's own state
// (pokéballs, potions, revives, shiny/faint counters) so read_alerts.js can read it without ever
// touching gameplay. Simplified port of the Electron app's collector (drops the cross-reload
// session-seeding and financial-estimate bits, which only fed an optional stats dashboard).
(() => {
  if (window.__poke) return;
  const mk = () => ({ start: Date.now(), kills: 0, xp: 0, balls: 0, captures: 0, shinyN: 0, shinyCapN: 0, faintN: 0, hp: {} });
  const P = { ws: {}, api: {}, sess: mk(), knownPokes: {}, pokesBaselined: false };
  window.__poke = P;
  let auth = null;

  const OW = window.WebSocket;
  window.WebSocket = function (...ar) {
    const s = Reflect.construct(OW, ar);
    P.sock = s;
    s.addEventListener('message', (e) => {
      try {
        if (typeof e.data !== 'string') return;
        const m = JSON.parse(e.data);
        if (!m || !m.type) return;
        P.ws[m.type] = m;
        const S = P.sess;
        if (m.type === 'field-kill') {
          S.kills++; S.xp += m.xpGained || 0;
          if (m.shiny) S.shinyN = (S.shinyN || 0) + 1;
        } else if (m.type === 'catch-result') {
          S.balls++;
          if (m.success) S.captures++;
        } else if (m.type === 'poke-delta') {
          const pk = m.poke;
          if (pk) {
            const was = S.hp[pk.id];
            if (pk.team && pk.hp === 0 && was > 0) S.faintN++;
            S.hp[pk.id] = pk.hp;
            if (P.pokesBaselined && !P.knownPokes[pk.id] && pk.shiny) S.shinyCapN = (S.shinyCapN || 0) + 1;
            P.knownPokes[pk.id] = 1;
          }
        } else if (m.type === 'pokes') {
          (m.list || []).forEach((pk) => {
            const was = S.hp[pk.id];
            if (pk.team && pk.hp === 0 && was > 0) S.faintN++;
            S.hp[pk.id] = pk.hp;
            P.knownPokes[pk.id] = 1;
          });
          P.pokesBaselined = true;
        }
      } catch (x) {}
    });
    return s;
  };
  window.WebSocket.prototype = OW.prototype;
  ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach((k) => { try { window.WebSocket[k] = OW[k]; } catch (x) {} });

  const OF = window.fetch;
  window.fetch = function (...a) {
    try {
      let H;
      const r0 = a[0], ini = a[1];
      if (ini && ini.headers) H = ini.headers;
      else if (r0 && typeof r0 === 'object' && r0.headers) H = r0.headers;
      if (H) {
        let au;
        if (typeof H.get === 'function') au = H.get('authorization');
        else au = H.Authorization || H.authorization;
        if (au) auth = au;
      }
    } catch (x) {}
    const p = OF.apply(this, a);
    try {
      const u = (a[0] && a[0].url) || a[0];
      if (typeof u === 'string' && /\/api\/|items\.json/.test(u)) {
        p.then((r) => {
          const c = r.clone();
          c.json().then((j) => {
            const key = u.split('?')[0];
            if (key === '/api/characters/me' && !(j && j.character)) return;
            P.api[key] = j;
          }).catch(() => {});
        }).catch(() => {});
      }
    } catch (x) {}
    return p;
  };

  const poll = () => {
    try {
      const o = auth ? { headers: { Authorization: auth } } : undefined;
      fetch('/api/characters/me', o).catch(() => {});
      fetch('/game/items.json', o).catch(() => {});
      try { if (P.sock && P.sock.readyState === 1) P.sock.send(JSON.stringify({ type: 'inv-get' })); } catch (x) {}
    } catch (x) {}
  };
  setInterval(poll, 30000);
  setTimeout(poll, 9000);
})();
