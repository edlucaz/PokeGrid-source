// Reads the snapshot state_collector.js keeps in window.__poke and returns a small JSON object
// that GamePanel polls to decide whether to fire a low-resource / shiny notification. Returns
// null until the collector has seen at least one relevant WebSocket/fetch message.
(() => {
  const P = window.__poke;
  if (!P || !P.ws) return null;
  const ws = P.ws, api = P.api || {};
  const cat = {};
  const doc = api['/game/items.json'];
  if (doc && doc.items) doc.items.forEach((it) => { cat[it.id] = it; });
  let balls = 0;
  const bm = ws.balls;
  if (bm && bm.counts) for (const k in bm.counts) balls += bm.counts[k];
  let potions = 0, revives = 0;
  const inv = (ws.inventory && ws.inventory.items) || [];
  inv.forEach((it) => {
    const c = (cat[it.itemId] || {}).category;
    if (c === 'heal' || c === 'potion') potions += it.quantity;
    else if (c === 'revive') revives += it.quantity;
  });
  const S = P.sess || {};
  const ch = (api['/api/characters/me'] && api['/api/characters/me'].character) || {};
  const hasBalls = !!(bm && bm.counts);
  const hasInv = !!(ws.inventory && ws.inventory.items);
  const live = !!(ch.id || hasBalls || hasInv);
  return { live, hasBalls, hasInv, balls, potions, revives, shinyN: S.shinyN || 0, faintN: S.faintN || 0, caps: S.captures || 0, shinyCapN: S.shinyCapN || 0 };
})();
