// Intercepts sell POSTs and asks for confirmation before anything valuable goes out: shiny or
// Lendária+ quality (>=1.7) Pokémon, and a short list of named rare items. Never acts on the
// game by itself, only blocks/confirms via window.confirm (handled natively by onJsConfirm).
(() => {
  if (window.__pgSellGuard) return; window.__pgSellGuard = true;
  const PROT = ['strange pheromone', 'rare pokémon picture', 'rare pokemon picture'];
  const nomeItem = (id) => { try { const P = window.__poke; const doc = P && P.api && P.api['/game/items.json']; const it = doc && doc.items && doc.items.find(x => x.id === id); return it ? it.name : ''; } catch (x) { return ''; } };
  const OF2 = window.fetch;
  window.fetch = function (...a) {
    try {
      const u = (a[0] && a[0].url) || a[0]; const ini = a[1] || {};
      if (typeof u === 'string' && ini.body && typeof ini.body === 'string') {
        const avisos = [];
        if (/\/api\/game\/pokemon\/sell/.test(u)) {
          const b = JSON.parse(ini.body); const ids = (b && b.pokeIds) || [];
          const P = window.__poke; const LP = (P && P.ws && P.ws.pokes && P.ws.pokes.list) || [];
          ids.forEach(id => { const p = LP.find(x => x.id === id); if (p && (p.shiny || (p.quality || 0) >= 1.7)) { const q = p.quality || 0; const lab = q >= 4 ? 'Divina' : q >= 3 ? 'Anciã' : q >= 2 ? 'Mítica' : q >= 1.7 ? 'Lendária' : q >= 1.5 ? 'Épica' : q >= 1.3 ? 'Rara' : q >= 1.1 ? 'Incomum' : q >= 1 ? 'Comum' : 'Fraca'; avisos.push((p.shiny ? '✨ SHINY ' : '') + (p.name || '?') + ' · Nv ' + (p.level != null ? p.level : '?') + ' · IV ' + (p.ivTotal != null ? p.ivTotal : '?') + '/192 · ' + lab + ' ×' + q.toFixed(2)); } });
        } else if (/\/api\/game\/(shop|flint)\/sell/.test(u)) {
          const b = JSON.parse(ini.body); const its = b && b.items ? b.items : (b && b.itemId ? [{ itemId: b.itemId, qty: b.qty }] : []);
          its.forEach(x => { const nm = nomeItem(x.itemId); if (nm && PROT.includes(nm.toLowerCase())) avisos.push(nm + ' x' + (x.qty || 1)); });
        }
        if (avisos.length && window.__pgSellGuardOn !== false) {
          const ok = window.confirm('PokeGrid: você está vendendo coisas valiosas:\n\n' + avisos.join('\n') + '\n\nConfirmar a venda?');
          if (!ok) return Promise.reject(new Error('Venda cancelada pelo PokeGrid'));
        }
      }
    } catch (x) {}
    return OF2.apply(this, a);
  };
})();
